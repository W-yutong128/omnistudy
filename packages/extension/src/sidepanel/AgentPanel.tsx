import { FormEvent, useEffect, useRef, useState } from "react";
import type { AgentChatResult, AgentPractice } from "@omnistudy/shared";

type AgentResult = AgentChatResult;
type ChatMessage = { role: "user" | "assistant"; text: string; result?: AgentResult };

interface Props {
  sessionId?: string;
  authenticated: boolean;
}

const suggestions = [
  "解释一下当前内容",
  "根据当前内容出一道练习题",
  "在我的笔记里搜索这个概念",
  "刚才这个概念是在哪里讲的？",
];

export function AgentPanel({ sessionId, authenticated }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([{
    role: "assistant",
    text: "我能结合当前视频解释知识点、搜索笔记、生成练习，并帮你定位到讲解位置。",
  }]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [ideaProjectPath, setIdeaProjectPath] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    void chrome.storage.local.get("ideaProjectPath").then(value => setIdeaProjectPath(value.ideaProjectPath || ""));
  }, []);

  async function openIdea(result: AgentResult) {
    if (!result.ideHandoff) return;
    if (!ideaProjectPath.trim()) return appendAssistant("请先在下方配置允许打开的 IDEA 项目绝对路径。");
    if (!window.confirm(`将在 IDEA 中打开：\n${ideaProjectPath}\n\n原因：${result.ideHandoff.reason}`)) return;
    const response = await chrome.runtime.sendMessage({
      type: "idea:open",
      payload: { projectPath: ideaProjectPath.trim(), filePath: result.ideHandoff.suggestedFile },
    });
    appendAssistant(response?.success ? "已把打开请求交给 IDEA。" : `打开失败：${response?.error || "未知错误"}`);
  }

  async function send(text: string) {
    const message = text.trim();
    if (!message || loading) return;
    if (!authenticated) return appendAssistant("请先登录后再使用学习 Agent。");
    if (!sessionId) return appendAssistant("请先打开一个 B 站视频，等侧边栏连接到学习会话。");

    setInput("");
    setMessages(current => [...current, { role: "user", text: message }]);
    setLoading(true);
    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (!tab?.id) throw new Error("无法访问当前视频标签页");
      const context = await chrome.tabs.sendMessage(tab.id, { type: "agent:get-context" });
      if (!context || context.error) throw new Error(context?.error || "无法读取视频上下文，请刷新视频页面");

      const response = await chrome.runtime.sendMessage({
        type: "agent:chat",
        payload: { sessionId, message, ...context },
      });
      if (!response?.success) throw new Error(response?.error || "Agent 暂时无法回答");
      const result = response.data as AgentResult;

      if (typeof result.seekTo === "number") {
        await chrome.tabs.sendMessage(tab.id, { type: "agent:seek", time: result.seekTo });
      }
      setMessages(current => [...current, { role: "assistant", text: result.reply, result }]);
    } catch (error) {
      appendAssistant("请求失败：" + (error as Error).message);
    } finally {
      setLoading(false);
      requestAnimationFrame(() => bottomRef.current?.scrollIntoView({ behavior: "smooth" }));
    }
  }

  function appendAssistant(text: string) {
    setMessages(current => [...current, { role: "assistant", text }]);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void send(input);
  }

  return <div className="flex-1 min-h-0 flex flex-col bg-slate-50">
    <div className="px-4 py-3 border-b bg-white">
      <div className="flex items-center gap-2">
        <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-indigo-600 text-white">✦</span>
        <div><h2 className="text-sm font-semibold text-slate-800">学习 Agent</h2><p className="text-[11px] text-slate-400">只读笔记 · 单步工具 · 操作可见</p></div>
      </div>
    </div>

    <div className="flex-1 overflow-y-auto p-3 space-y-3">
      {messages.map((message, index) => <div key={index} className={message.role === "user" ? "flex justify-end" : "flex justify-start"}>
        <div className={`max-w-[88%] rounded-2xl px-3 py-2 text-sm leading-6 ${message.role === "user" ? "bg-indigo-600 text-white rounded-br-md" : "bg-white border border-slate-100 text-slate-700 shadow-sm rounded-bl-md"}`}>
          <div className="whitespace-pre-wrap">{message.text}</div>
          {message.result?.skill && <div className="mt-1 text-[10px] text-slate-400">Skill · {message.result.skill}</div>}
          {message.result?.seekTo != null && <div className="mt-2 text-xs text-indigo-600">已跳转到 {formatTime(message.result.seekTo)}</div>}
          {message.result?.practice && <PracticeCard practice={message.result.practice} />}
          {message.result?.ideHandoff && <div className="mt-2 rounded-xl border border-amber-200 bg-amber-50 p-2 text-xs text-amber-900">
            <div>{message.result.ideHandoff.reason}</div>
            <button onClick={() => void openIdea(message.result!)} className="mt-2 rounded-lg bg-amber-600 px-3 py-1.5 font-medium text-white">确认并打开 IDEA</button>
          </div>}
          {!!message.result?.sources?.length && <details className="mt-2 border-t border-slate-100 pt-2 text-[11px] text-slate-500">
            <summary className="cursor-pointer">查看来源（{message.result.sources.length}）</summary>
            <div className="mt-1 space-y-2">{message.result.sources.map((source, i) => <div key={i}><div className="font-medium text-slate-600">[{i + 1}] {source.title}{source.startTime != null ? ` · ${formatTime(source.startTime)}` : ""}</div><p className="line-clamp-3">{source.snippet}</p></div>)}</div>
          </details>}
        </div>
      </div>)}
      {loading && <div className="flex justify-start"><div className="rounded-2xl rounded-bl-md bg-white border px-3 py-2 text-sm text-slate-400">正在查看上下文并选择工具…</div></div>}
      {messages.length <= 1 && <div className="grid grid-cols-2 gap-2 pt-1">{suggestions.map(text => <button key={text} onClick={() => void send(text)} className="rounded-xl border border-indigo-100 bg-indigo-50/60 p-2 text-left text-xs leading-5 text-indigo-700 hover:bg-indigo-50">{text}</button>)}</div>}
      <div ref={bottomRef} />
    </div>

    <form onSubmit={submit} className="border-t bg-white p-3">
      <details className="mb-2 text-[11px] text-slate-500">
        <summary className="cursor-pointer">IDEA 项目设置（可选）</summary>
        <input value={ideaProjectPath} onChange={event => {
          setIdeaProjectPath(event.target.value);
          void chrome.storage.local.set({ ideaProjectPath: event.target.value });
        }} placeholder="/Users/you/projects/course-lab" className="mt-2 w-full rounded-lg border border-slate-200 px-2 py-1.5 outline-none focus:border-indigo-400" />
      </details>
      <div className="flex items-end gap-2 rounded-xl border border-slate-200 bg-slate-50 p-2 focus-within:border-indigo-400">
        <textarea value={input} onChange={event => setInput(event.target.value)} onKeyDown={event => {
          if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); void send(input); }
        }} rows={2} placeholder="问当前视频、笔记或让我出题…" className="min-h-[44px] flex-1 resize-none bg-transparent px-1 text-sm outline-none" />
        <button type="submit" disabled={loading || !input.trim()} className="h-9 rounded-lg bg-indigo-600 px-3 text-xs font-medium text-white disabled:bg-slate-300">发送</button>
      </div>
      <p className="mt-1.5 text-center text-[10px] text-slate-400">Agent 可能出错，重要知识请结合课程原文核对</p>
    </form>
  </div>;
}

function PracticeCard({ practice }: { practice: AgentPractice }) {
  const [choice, setChoice] = useState<string | null>(null);
  const correct = choice === practice.correctOptionId;
  return <div className="mt-3 rounded-xl bg-blue-50 p-3 text-xs text-slate-700">
    <div className="font-medium text-blue-900">随堂练习 · {practice.question}</div>
    <div className="mt-2 space-y-1.5">{practice.options.map(option => <button key={option.id} disabled={choice != null} onClick={() => setChoice(option.id)} className={`block w-full rounded-lg border px-2.5 py-2 text-left ${choice === option.id ? (correct ? "border-emerald-400 bg-emerald-50" : "border-amber-400 bg-amber-50") : "border-blue-100 bg-white"}`}>{option.id}. {option.text}</button>)}</div>
    {choice && <div className={`mt-2 rounded-lg p-2 ${correct ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-700"}`}><div className="font-medium">{correct ? "回答正确" : `回答错误，正确答案是 ${practice.correctOptionId}`}</div><p className="mt-1">{practice.explanation}</p></div>}
  </div>;
}

const formatTime = (seconds: number) => `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, "0")}`;
