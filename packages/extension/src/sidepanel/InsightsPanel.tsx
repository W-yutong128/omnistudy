import React, { useEffect, useState } from "react";
import type { AgentTrace, KnowledgePoint, UserUsageOverview } from "@omnistudy/shared";

type NoteStatus = { id?: string; status?: string; contentStatus?: string; generatedAt?: string; error?: string };

const FEATURE_NAMES: Record<string, string> = {
  AGENT: "Agent 对话", AGENT_V2: "AgentScope 对话", AGENT_REPLY: "Agent 回复",
  AGENT_PLAN: "Agent 规划", INTERCEPT: "视频提问", EVALUATE_ANSWER: "答案分析",
  NOTE_CHUNK_SUMMARY: "增量笔记", NOTE_FINALIZE: "最终总结",
  STUDY_MATERIAL: "复习材料", CONNECTION_TEST: "模型检测",
  CONTEXT_COMPACTION: "上下文压缩", AGENT_MEMORY: "记忆维护",
};

export const InsightsPanel: React.FC<{ sessionId?: string; refreshKey?: number }> = ({ sessionId, refreshKey }) => {
  const [usage, setUsage] = useState<UserUsageOverview | null>(null);
  const [weak, setWeak] = useState<KnowledgePoint[]>([]);
  const [due, setDue] = useState<KnowledgePoint[]>([]);
  const [traces, setTraces] = useState<AgentTrace[]>([]);
  const [note, setNote] = useState<NoteStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = async () => {
    setLoading(true); setError("");
    try {
      const [usageResult, weakResult, dueResult, traceResult, noteResult] = await Promise.all([
        chrome.runtime.sendMessage({ type: "insights:usage" }),
        chrome.runtime.sendMessage({ type: "insights:weak" }),
        chrome.runtime.sendMessage({ type: "insights:due" }),
        chrome.runtime.sendMessage({ type: "insights:traces" }),
        sessionId
          ? chrome.runtime.sendMessage({ type: "notes:session-status", payload: { sessionId } })
          : Promise.resolve({ success: true, data: null }),
      ]);
      const failed = [usageResult, weakResult, dueResult, traceResult, noteResult]
        .find(result => !result?.success);
      if (failed) throw new Error(failed.error || "学习概览加载失败");
      setUsage(usageResult.data);
      setWeak(Array.isArray(weakResult.data) ? weakResult.data : []);
      setDue(Array.isArray(dueResult.data) ? dueResult.data : []);
      setTraces(Array.isArray(traceResult.data) ? traceResult.data.slice(0, 8) : []);
      setNote(noteResult.data);
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, [sessionId, refreshKey]);

  if (loading) return <div className="flex-1 p-6 text-center text-sm text-gray-500">正在汇总学习数据…</div>;

  return (
    <div className="flex-1 overflow-y-auto p-4 space-y-4">
      <div className="flex items-center justify-between">
        <div><h2 className="font-semibold text-gray-900">今日学习概览</h2><p className="text-xs text-gray-500">只统计当前登录用户</p></div>
        <button onClick={() => void load()} className="text-xs text-indigo-600">刷新</button>
      </div>
      {error && <div className="rounded bg-red-50 p-3 text-xs text-red-600">{error}</div>}

      {usage && <>
        <div className="grid grid-cols-2 gap-2">
          <Metric label="AI 操作" value={usage.requests.toLocaleString()} />
          <Metric label="总 Token" value={usage.totalTokens.toLocaleString()} />
          <Metric label="缓存 Token" value={usage.cachedTokens.toLocaleString()} />
          <Metric label="估算成本" value={`¥${(usage.estimatedCostMicros / 1_000_000).toFixed(4)}`} />
        </div>
        <div className="rounded-lg border bg-white p-3 shadow-sm">
          <div className="flex justify-between text-xs text-gray-500">
            <span>输入 {usage.inputTokens.toLocaleString()}</span><span>输出 {usage.outputTokens.toLocaleString()}</span>
            <span className={usage.failed ? "text-red-500" : "text-green-600"}>成功 {usage.succeeded} / 失败 {usage.failed}</span>
          </div>
          <div className="mt-3 space-y-2">
            {usage.features.length === 0 ? <p className="text-xs text-gray-400">今天还没有 AI 调用</p> : usage.features.map(item => (
              <div key={item.feature} className="flex items-center justify-between text-xs">
                <span className="text-gray-700">{FEATURE_NAMES[item.feature] || item.feature}</span>
                <span className="text-gray-400">{item.requests} 次 · {item.totalTokens.toLocaleString()} Token</span>
              </div>
            ))}
          </div>
        </div>
      </>}

      <div className="rounded-lg border bg-white p-3 shadow-sm">
        <h3 className="text-sm font-medium text-gray-800">当前视频总结</h3>
        {!sessionId ? <p className="mt-2 text-xs text-gray-400">打开一个视频后可查看总结状态</p>
          : !note?.id ? <p className="mt-2 text-xs text-gray-400">尚未生成笔记；播放视频并开启字幕后会自动整理</p>
          : <div className="mt-2 text-xs text-gray-600">
              <p>{noteStatusText(note.status, note.contentStatus)}</p>
              {note.generatedAt && <p className="mt-1 text-gray-400">最近生成：{formatDate(note.generatedAt)}</p>}
              {note.error && <p className="mt-1 text-red-500">{note.error}</p>}
            </div>}
      </div>

      <div className="grid grid-cols-2 gap-2">
        <KnowledgeCard title="薄弱知识点" points={weak} empty="暂无已识别薄弱点" />
        <KnowledgeCard title="今日待复习" points={due} empty="今天没有到期复习" />
      </div>

      <div className="rounded-lg border bg-white p-3 shadow-sm">
        <h3 className="text-sm font-medium text-gray-800">最近 Agent 执行</h3>
        <div className="mt-2 space-y-2">
          {traces.length === 0 ? <p className="text-xs text-gray-400">暂无 Agent 执行记录</p> : traces.map(trace => (
            <details key={trace.id} className="rounded bg-gray-50 px-2 py-2 text-xs">
              <summary className="cursor-pointer flex justify-between gap-2">
                <span className={trace.success ? "text-green-700" : "text-red-600"}>{trace.success ? "成功" : "失败"} · {trace.skill || trace.tool || trace.state}</span>
                <span className="text-gray-400">{trace.inputTokens + trace.outputTokens} Token · {trace.latencyMs}ms</span>
              </summary>
              <div className="mt-2 space-y-1 text-gray-500">
                <p>模型：{trace.model || "—"}</p><p>输入/输出/缓存：{trace.inputTokens}/{trace.outputTokens}/{trace.cachedTokens}</p>
                {trace.error && <p className="text-red-500">{trace.error}</p>}
              </div>
            </details>
          ))}
        </div>
      </div>
    </div>
  );
};

const Metric: React.FC<{ label: string; value: string }> = ({ label, value }) => <div className="rounded-lg border bg-white p-3 shadow-sm"><p className="text-xs text-gray-500">{label}</p><p className="mt-1 text-lg font-semibold text-gray-900">{value}</p></div>;

const KnowledgeCard: React.FC<{ title: string; points: KnowledgePoint[]; empty: string }> = ({ title, points, empty }) => <div className="rounded-lg border bg-white p-3 shadow-sm"><h3 className="text-xs font-medium text-gray-800">{title}（{points.length}）</h3><div className="mt-2 space-y-1">{points.length === 0 ? <p className="text-[11px] text-gray-400">{empty}</p> : points.slice(0, 5).map(point => <div key={point.id} title={point.name} className="truncate text-[11px] text-gray-600">{point.name} · {Math.round((point.masteryScore || 0) * 100)}%</div>)}</div></div>;

function noteStatusText(status?: string, contentStatus?: string) {
  if (status === "generating") return "AI 正在整理字幕和章节…";
  if (status === "done" && contentStatus === "organized") return "最终总结已完成，并已生成复习材料";
  if (status === "done") return "增量笔记已生成；视频自然结束后会形成最终总结和复习材料";
  if (status === "failed") return "最近一次生成失败，可在笔记页重试";
  return "等待生成";
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}
