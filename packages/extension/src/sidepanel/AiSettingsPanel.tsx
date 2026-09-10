import React, { useEffect, useState } from "react";
import type { AiProviderSettings } from "@omnistudy/shared";

export const AiSettingsPanel: React.FC<{ onVerified?: () => void }> = ({ onVerified }) => {
  const [settings, setSettings] = useState<AiProviderSettings | null>(null);
  const [apiKey, setApiKey] = useState("");
  const [fastVisionModel, setFastVisionModel] = useState("qwen3-vl-flash");
  const [strongTextModel, setStrongTextModel] = useState("qwen-plus");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);

  const load = async () => {
    const result = await chrome.runtime.sendMessage({ type: "ai-settings:get" });
    if (!result?.success) {
      setError(result?.error || "加载模型配置失败");
      return;
    }
    const next = result.data as AiProviderSettings;
    setSettings(next);
    setFastVisionModel(next.fastVisionModel);
    setStrongTextModel(next.strongTextModel);
  };

  useEffect(() => { void load(); }, []);

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(""); setMessage("");
    if (!apiKey.trim()) { setError("请输入 API Key"); return; }
    setSaving(true);
    const result = await chrome.runtime.sendMessage({
      type: "ai-settings:save",
      payload: { apiKey: apiKey.trim(), fastVisionModel, strongTextModel },
    });
    setSaving(false);
    if (!result?.success) { setError(result?.error || "保存失败"); return; }
    setSettings(result.data); setApiKey("");
    setMessage("Key 已加密保存，正在验证模型连接…");
    await testConnection(true);
  };

  const remove = async () => {
    if (!window.confirm("确定删除个人 API Key 吗？删除后 AI 功能将暂停，重新配置后即可恢复。")) return;
    setError(""); setMessage("");
    const result = await chrome.runtime.sendMessage({ type: "ai-settings:delete" });
    if (!result?.success) { setError(result?.error || "删除失败"); return; }
    setSettings(result.data); setApiKey(""); setMessage("个人 Key 已删除");
  };

  const testConnection = async (afterSave = false) => {
    setError(""); setMessage(""); setTesting(true);
    const result = await chrome.runtime.sendMessage({ type: "ai-settings:test" });
    setTesting(false);
    if (!result?.success) {
      setError((afterSave ? "Key 已保存，但连接验证失败：" : "连接测试失败：") + (result?.error || "未知错误"));
      await load();
      return;
    }
    await load();
    setMessage(`${result.data.message} · ${result.data.model} · ${result.data.latencyMs}ms`);
    onVerified?.();
  };

  return (
    <div className="flex-1 overflow-y-auto p-4">
      <div className="rounded-lg border bg-white p-4 shadow-sm">
        <h2 className="font-semibold text-gray-900">模型配置</h2>
        <p className="mt-1 text-xs leading-5 text-gray-500">开源 BYOK 模式：Key 发送到后端加密保存，不存入浏览器，也不会返回明文。</p>

        <div className="mt-3 rounded-md bg-indigo-50 p-3 text-xs text-indigo-800">
          {!settings ? "正在读取配置…" : settings.source === "USER"
            ? `${settings.connectionStatus === "VERIFIED" ? "✓ 已验证" : settings.connectionStatus === "FAILED" ? "⚠ 验证失败" : "待验证"} · 正在使用你的 Key（${settings.maskedApiKey}）`
            : "尚未配置个人 Key，AI 功能暂不可用"}
        </div>

        {settings?.lastVerifiedAt && <p className="mt-2 text-[11px] text-green-600">最近验证：{new Date(settings.lastVerifiedAt).toLocaleString("zh-CN", { hour12: false })}</p>}
        {settings?.lastTestError && <p className="mt-2 text-[11px] text-red-500">最近失败：{settings.lastTestError}</p>}

        {error && <div className="mt-3 rounded bg-red-50 p-2 text-xs text-red-600">{error}</div>}
        {message && <div className="mt-3 rounded bg-green-50 p-2 text-xs text-green-700">{message}</div>}

        <form className="mt-4 space-y-3" onSubmit={save}>
          <label className="block text-xs font-medium text-gray-700">
            DashScope API Key
            <input type="password" autoComplete="off" value={apiKey} onChange={event => setApiKey(event.target.value)}
              placeholder="sk-..." className="mt-1 w-full rounded border px-3 py-2 text-sm" />
          </label>
          <label className="block text-xs font-medium text-gray-700">
            视觉模型
            <input value={fastVisionModel} onChange={event => setFastVisionModel(event.target.value)}
              className="mt-1 w-full rounded border px-3 py-2 text-sm" />
          </label>
          <label className="block text-xs font-medium text-gray-700">
            文本模型
            <input value={strongTextModel} onChange={event => setStrongTextModel(event.target.value)}
              className="mt-1 w-full rounded border px-3 py-2 text-sm" />
          </label>
          <button disabled={saving || testing} className="w-full rounded bg-indigo-600 py-2 text-sm font-medium text-white disabled:bg-gray-300">
            {saving || testing ? "保存并验证中…" : settings?.source === "USER" ? "替换并验证 Key" : "保存并验证 Key"}
          </button>
        </form>

        {settings?.source === "USER" && (
          <div className="mt-3 grid grid-cols-2 gap-2">
            <button onClick={() => void testConnection()} disabled={testing}
              className="rounded border border-indigo-200 py-2 text-sm text-indigo-600 disabled:opacity-50">
              {testing ? "测试中…" : "测试连接"}
            </button>
            <button onClick={() => void remove()} disabled={testing}
              className="rounded border border-red-200 py-2 text-sm text-red-600 disabled:opacity-50">
              删除个人 Key
            </button>
          </div>
        )}
        {settings?.source === "USER" && <p className="mt-2 text-[11px] text-gray-400">连接测试会发起一次极小的模型请求并记录实际 Token。</p>}
        <p className="mt-4 break-all text-[11px] leading-4 text-gray-400">接口：{settings?.baseUrl || "DashScope compatible API"}</p>
      </div>
    </div>
  );
};
