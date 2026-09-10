import React, { useEffect, useState } from "react";

type Overview = {
  users: number; activeUsers: number; requestsToday: number; tokensToday: number; estimatedCostMicrosToday: number;
  features?: Array<{ feature: string; requests: number; tokens: number; estimatedCostMicros: number }>;
};
type AdminUser = {
  id: string; username: string; email?: string; role: "USER" | "ADMIN";
  status: "ACTIVE" | "DISABLED"; dailyRequestLimit: number; dailyTokenLimit: number; unlimited: boolean;
  requestsUsedToday: number; tokensUsedToday: number;
};

export const AdminPanel: React.FC = () => {
  const [overview, setOverview] = useState<Overview | null>(null);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    setError("");
    const [overviewResult, usersResult] = await Promise.all([
      chrome.runtime.sendMessage({ type: "admin:overview" }),
      chrome.runtime.sendMessage({ type: "admin:users" }),
    ]);
    if (!overviewResult?.success || !usersResult?.success) {
      setError(overviewResult?.error || usersResult?.error || "加载管理数据失败");
    } else {
      setOverview(overviewResult.data);
      setUsers(usersResult.data);
    }
    setLoading(false);
  };

  useEffect(() => { void load(); }, []);

  const update = async (userId: string, patch: Partial<Pick<AdminUser, "role" | "status" | "unlimited">>) => {
    setError("");
    const result = await chrome.runtime.sendMessage({ type: "admin:update-user", payload: { userId, update: patch } });
    if (!result?.success) {
      setError(result?.error || "更新失败");
      return;
    }
    setUsers(current => current.map(user => user.id === userId ? result.data : user));
    await load();
  };

  if (loading) return <div className="p-5 text-sm text-gray-500">加载管理数据…</div>;

  return (
    <div className="p-4 space-y-4 overflow-y-auto">
      {error && <div className="rounded bg-red-50 p-3 text-sm text-red-600">{error}</div>}
      {overview && (
        <>
          <div className="grid grid-cols-2 gap-2">
            <Metric label="用户" value={`${overview.activeUsers}/${overview.users}`} />
            <Metric label="今日操作" value={String(overview.requestsToday)} />
            <Metric label="今日 Token" value={overview.tokensToday.toLocaleString()} />
            <Metric label="预估费用" value={`¥${(overview.estimatedCostMicrosToday / 1_000_000).toFixed(4)}`} />
          </div>
          {!!overview.features?.length && (
            <div className="rounded-lg border bg-white p-3">
              <h3 className="mb-2 text-sm font-medium text-gray-800">今日 AI 成本明细</h3>
              <div className="space-y-2">
                {overview.features.map(item => (
                  <div key={item.feature} className="grid grid-cols-[1fr_auto] gap-x-3 text-xs">
                    <span className="truncate font-medium text-gray-700">{featureLabel(item.feature)}</span>
                    <span className="text-gray-500">¥{(item.estimatedCostMicros / 1_000_000).toFixed(4)}</span>
                    <span className="text-gray-400">{item.requests} 次操作</span>
                    <span className="text-gray-400">{item.tokens.toLocaleString()} Token</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-gray-800">用户与额度</h2>
        <button onClick={() => void load()} className="text-sm text-indigo-600">刷新</button>
      </div>
      {users.map(user => (
        <article key={user.id} className="rounded-lg border bg-white p-3 shadow-sm">
          <div className="flex items-start justify-between gap-2">
            <div>
              <div className="font-medium text-gray-900">{user.username}</div>
              <div className="text-xs text-gray-500">{user.email || "未绑定邮箱"}</div>
            </div>
            <span className={`rounded px-2 py-0.5 text-xs ${user.status === "ACTIVE" ? "bg-green-50 text-green-700" : "bg-red-50 text-red-700"}`}>
              {user.status === "ACTIVE" ? "正常" : "停用"}
            </span>
          </div>
          <div className="mt-2 text-xs text-gray-500">
            今日 {user.requestsUsedToday}/{user.unlimited ? "∞" : user.dailyRequestLimit} 次 · {user.tokensUsedToday.toLocaleString()} Token
          </div>
          <div className="mt-3 grid grid-cols-2 gap-2">
            <select value={user.role} onChange={e => void update(user.id, { role: e.target.value as AdminUser["role"] })}
              className="rounded border px-2 py-1.5 text-xs">
              <option value="USER">用户</option><option value="ADMIN">管理员</option>
            </select>
            <button onClick={() => void update(user.id, { status: user.status === "ACTIVE" ? "DISABLED" : "ACTIVE" })}
              className="rounded border px-2 py-1.5 text-xs text-gray-700">
              {user.status === "ACTIVE" ? "停用" : "启用"}
            </button>
          </div>
        </article>
      ))}
    </div>
  );
};

const Metric: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <div className="rounded-lg bg-indigo-50 p-3">
    <div className="text-xs text-indigo-500">{label}</div>
    <div className="mt-1 font-semibold text-indigo-900">{value}</div>
  </div>
);

function featureLabel(feature: string): string {
  const labels: Record<string, string> = {
    CONNECTION_TEST: "模型连接测试",
    AGENT: "Agent 对话", AGENT_V2: "AgentScope 对话", INTERCEPT: "视频拦截",
    EVALUATE_ANSWER: "答案评估", NOTE_CHUNK_SUMMARY: "字幕摘要",
    NOTE_FINALIZE: "笔记整理", STUDY_MATERIAL: "学习材料",
    CONTEXT_COMPACTION: "上下文压缩", AGENT_MEMORY: "Agent 记忆维护",
  };
  return labels[feature] || feature;
}
