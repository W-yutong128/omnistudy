import React, { useState, useEffect } from "react";
import type { Session, InterceptResult } from "@omnistudy/shared";
import { NotePanel } from "./NotePanel";
import { AgentPanel } from "./AgentPanel";
import { AdminPanel } from "./AdminPanel";
import { AiSettingsPanel } from "./AiSettingsPanel";
import { API_BASE_URL } from "../config";
import "./styles.css";

interface Message {
  type: string;
  payload?: unknown;
}

type BackendSession = Session & {
  videoTitle?: string;
};

type AuthState = {
  token: string;
  refreshToken?: string;
  userId: string;
  username?: string;
  role?: "USER" | "ADMIN";
  expiresAt?: string;
};

const API_BASE = API_BASE_URL;

export const App: React.FC = () => {
  const [session, setSession] = useState<Session | null>(null);
  const [questionsByPart, setQuestionsByPart] = useState<Record<number, InterceptResult[]>>({});
  const [currentPart, setCurrentPart] = useState<number>(1);
  const [totalParts, setTotalParts] = useState<number>(1);
  const [currentSubtitle, setCurrentSubtitle] = useState<string>("");
  const [isIntercepting, setIsIntercepting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [auth, setAuth] = useState<AuthState | null>(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [showLogin, setShowLogin] = useState(false);
  const [authMode, setAuthMode] = useState<"login" | "register">("login");
  const [loginForm, setLoginForm] = useState({ username: "", email: "", password: "" });
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [deleteInProgress, setDeleteInProgress] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [deleteResolvingIndex, setDeleteResolvingIndex] = useState<number | null>(null);
  const [activeSection, setActiveSection] = useState<"learning" | "agent" | "notes" | "models" | "admin">("learning");
  const [noteRefreshKey, setNoteRefreshKey] = useState(0);
  const [noteSyncStatus, setNoteSyncStatus] = useState<"idle" | "flushing" | "generating" | "success" | "error">("idle");
  const [noteSyncMessage, setNoteSyncMessage] = useState("");

  // 获取当前分P的问题列表
  const currentPartQuestions = questionsByPart[currentPart] || [];

  // 获取所有有问题的分P列表（用于显示标签）
  const partsWithQuestions = Object.keys(questionsByPart)
    .map(Number)
    .filter(p => questionsByPart[p]?.length > 0)
    .sort((a, b) => a - b);

  // 已观看到的最大集数；下拉框用连续集数，便于查看没有产生问题的集。
  const maxNavigablePart = Math.max(currentPart, totalParts, ...partsWithQuestions, 1);
  const navigableParts = Array.from({ length: maxNavigablePart }, (_, index) => index + 1);

  // Load auth from storage
  useEffect(() => {
    const init = async () => {
      try {
        const [sessionResult, authResult] = await Promise.all([
          chrome.storage.local.get("currentSession"),
          chrome.storage.local.get("auth"),
        ]);
        if (sessionResult.currentSession) {
          const restored = normalizeSession(sessionResult.currentSession);
          setSession(restored);
          await chrome.storage.local.set({ sessionId: restored.id });
          if (authResult.auth) {
            const restoredQuestions = await loadCourseQuestions(
              restored.videoUrl,
              restored.id,
              authResult.auth.token,
            );
            // 按分P分组
            const grouped = groupQuestionsByPart(restoredQuestions);
            setQuestionsByPart(grouped);
            // 设置当前分P
            const parts = Object.keys(grouped).map(Number).sort((a, b) => b - a);
            if (parts.length > 0) {
              setCurrentPart(parts[0]); // 默认显示最新的分P
            }
            setSession((current) => current
              ? { ...current, questionCount: restoredQuestions.length }
              : current);
          }
          syncCurrentTime();
        }
        if (authResult.auth) {
          setAuth(authResult.auth);
          const me = await chrome.runtime.sendMessage({ type: "auth:me" });
          if (me?.success) {
            const hydrated = { ...authResult.auth, username: me.data.username, role: me.data.role };
            await chrome.storage.local.set({ auth: hydrated });
            setAuth(hydrated);
          }
          const latestAuth = await chrome.storage.local.get("auth");
          if (!latestAuth.auth) {
            setAuth(null);
            setShowLogin(true);
          }
        } else {
          setShowLogin(true);
        }
      } catch (e) {
        console.error("Init failed:", e);
        const latestAuth = await chrome.storage.local.get("auth");
        if (!latestAuth.auth) setShowLogin(true);
      } finally {
        setAuthChecked(true);
      }
    };
    init();

    const listener = (msg: Message) => {
      if (msg.type === "subtitle:update") setCurrentSubtitle(msg.payload as string);
      else if (msg.type === "time:update") setCurrentTime(msg.payload as number);
      else if (msg.type === "question:added") {
        const q = msg.payload as InterceptResult;
        if (!q?.id || !q.question || !q.questionType || !Number.isFinite(q.confidence)) {
          console.warn("[OmniStudy] 忽略不完整的问题消息", q);
          return;
        }
        const part = q.part || 1;
        setQuestionsByPart(prev => ({
          ...prev,
          [part]: [...(prev[part] || []), q],
        }));
        setSession((s) => s ? { ...s, questionCount: (s.questionCount ?? 0) + 1 } : null);
      }
      else if (msg.type === "note:sync") setNoteRefreshKey((key) => key + 1);
      else if (msg.type === "context:ready" && msg.payload) {
        const payload = msg.payload as { url: string; part?: number; totalParts?: number };
        const newPart = payload.part || extractPartFromUrl(payload.url);
        setCurrentPart(newPart);
        if (payload.totalParts) setTotalParts(current => Math.max(current, payload.totalParts || 1));
        handleContextReady(payload.url);
        syncCurrentTime();
      }
    };

    chrome.runtime.onMessage.addListener(listener);
    const storageListener = (changes: Record<string, chrome.storage.StorageChange>, area: string) => {
      if (area === "local" && changes.auth && !changes.auth.newValue) {
        setAuth(null);
        setShowLogin(true);
        setActiveSection("learning");
      }
    };
    chrome.storage.onChanged.addListener(storageListener);
    return () => {
      chrome.runtime.onMessage.removeListener(listener);
      chrome.storage.onChanged.removeListener(storageListener);
    };
  }, []);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const res = await chrome.runtime.sendMessage({
        type: authMode === "register" ? "auth:register" : "auth:login",
        payload: { username: loginForm.username, email: authMode === "register" ? loginForm.email : undefined, password: loginForm.password },
      });
      if (!res?.success) {
        throw new Error(res?.error || "登录失败");
      }
      const authInfo = res.data as AuthState;
      await chrome.storage.local.set({ auth: authInfo });
      setAuth(authInfo);
      setShowLogin(false);
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (tab?.url) {
        await handleContextReady(tab.url);
        syncCurrentTime();
      }
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const handleLogout = async () => {
    await chrome.runtime.sendMessage({ type: "auth:logout" });
    setAuth(null);
    setAuthMode("login");
    setLoginForm({ username: "", email: "", password: "" });
    setError(null);
    setShowLogin(true);
    setActiveSection("learning");
  };

  const handleContextReady = async (url: string) => {
    try {
      const { auth: storedAuth, currentSession } = await chrome.storage.local.get([
        "auth",
        "currentSession",
      ]);
      if (!storedAuth?.token) return;
      
      // 检查是否是同一个视频的分P切换
      const currentCourseId = extractCourseId(url);
      const existingCourseId = currentSession ? extractCourseId(currentSession.videoUrl || "") : "";
      
      // 如果是同一个视频（相同 BV 号），复用已有 session
      if (currentSession && currentCourseId && currentCourseId === existingCourseId) {
        console.log("[OmniStudy] 检测到同一视频分P切换，复用已有 session");
        await chrome.storage.local.set({ sessionId: currentSession.id });
        // 更新 session 的视频 URL
        const updatedSession = { ...currentSession, videoUrl: url };
        await chrome.storage.local.set({ currentSession: updatedSession });
        setSession((existing) => existing ? { ...existing, videoUrl: url } : normalizeSession(updatedSession));
        // 不再清空问题列表，只是切换当前显示的分P
        setCurrentSubtitle("");
        setCurrentTime(0);
        return;
      }
      
      const title = await getVideoTitle();
      // 调用后端创建真实 session
      const res = await fetch(`${API_BASE}/session/start`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${storedAuth.token}`,
        },
        body: JSON.stringify({ videoUrl: url, videoTitle: title || "未知视频" }),
      });

      if (!res.ok) throw new Error(`创建 session 失败: ${res.status}`);

      const json = await res.json();
      if (!json.success) throw new Error(json.error);

      const newSession = normalizeSession(json.data);
      await chrome.storage.local.set({ currentSession: newSession, sessionId: newSession.id });
      setSession(newSession);
      setQuestionsByPart({}); // 新 session 初始为空
    } catch (e) {
      console.error("创建 session 失败:", e);
    }
  };

  const getVideoTitle = async (): Promise<string | null> => {
    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (tab?.id) {
        const result = await chrome.tabs.sendMessage(tab.id, { type: "get:title" });
        return result?.title || null;
      }
    } catch { /* ignore */ }
    return null;
  };

  const syncCurrentTime = async () => {
    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (tab?.id) {
        const result = await chrome.tabs.sendMessage(tab.id, { type: "get:currentTime" });
        if (typeof result?.currentTime === "number") {
          setCurrentTime(result.currentTime);
        }
        if (typeof result?.part === "number") setCurrentPart(result.part);
        if (typeof result?.totalParts === "number") setTotalParts(result.totalParts);
      }
    } catch { /* ignore */ }
  };

  const handleIntercept = async () => {
    if (!session || !auth || isIntercepting) return;
    setIsIntercepting(true);
    setError(null);

    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (!tab?.id) throw new Error("无法获取当前标签页");

      const interceptData = await chrome.tabs.sendMessage(tab.id, {
        type: "trigger:intercept",
        difficulty: 1,
      });

      const response = await fetch(`${API_BASE}/intercept`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${auth.token}`,
        },
        body: JSON.stringify({ ...interceptData, sessionId: session.id }),
      });

      if (!response.ok) {
        const err = await response.text();
        throw new Error(`请求失败: ${response.status} ${err}`);
      }

      const result = await response.json();
      if (result.success) {
        const q = { ...result.data.response, id: result.data.questionId } as InterceptResult;
        const part = q.part ?? interceptData.part ?? currentPart;
        setQuestionsByPart(prev => ({
          ...prev,
          [part]: [...(prev[part] || []), q],
        }));
        setSession((s) => s ? { ...s, questionCount: (s.questionCount ?? 0) + 1 } : null);
      } else {
        throw new Error(result.error || "未知错误");
      }
    } catch (e) {
      setError("拦截失败: " + (e as Error).message);
    } finally {
      setIsIntercepting(false);
    }
  };

  const syncNoteNow = async () => {
    if (!session) return;
    setNoteSyncStatus("flushing");
    setNoteSyncMessage("正在保存你目前看过的字幕");
    try {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (tab?.id) {
        const flushed = await chrome.tabs.sendMessage(tab.id, { type: "notes:flush-current" });
        if (flushed && flushed.success === false) throw new Error(flushed.error || "字幕上传失败");
      }

      setNoteSyncStatus("generating");
      setNoteSyncMessage("AI 正在判断章节并生成笔记，通常需要几秒到几十秒");
      const beforeResult = await chrome.runtime.sendMessage({
        type: "notes:session-status", payload: { sessionId: session.id },
      });
      const previousGeneratedAt = beforeResult?.success ? beforeResult.data?.generatedAt : null;
      const result = await chrome.runtime.sendMessage({
        type: "notes:auto-sync", payload: { sessionId: session.id, finalize: false },
      });
      if (!result?.success) throw new Error(result?.error || "无法启动整理任务");

      // 后端模型连接的响应超时为 120 秒；额外预留排队和数据库提交时间。
      const deadline = Date.now() + 150_000;
      let observedGenerating = false;
      while (Date.now() < deadline) {
        await delay(1200);
        const statusResult = await chrome.runtime.sendMessage({
          type: "notes:session-status", payload: { sessionId: session.id },
        });
        if (!statusResult?.success) throw new Error(statusResult?.error || "无法读取笔记状态");
        if (statusResult.data?.status === "failed") {
          throw new Error(statusResult.data?.error || "笔记生成失败，请稍后重试");
        }
        if (statusResult.data?.status === "generating") {
          observedGenerating = true;
          setNoteSyncMessage("AI 已收到任务，正在分析字幕并划分章节");
          continue;
        }
        const generatedAt = statusResult.data?.generatedAt;
        if (statusResult.data?.status === "done" && statusResult.data?.id
            && ((generatedAt && generatedAt !== previousGeneratedAt) || observedGenerating)) {
          setNoteRefreshKey((key) => key + 1);
          setNoteSyncStatus("success");
          setNoteSyncMessage("笔记已更新，不需要等视频结束");
          return;
        }
      }
      throw new Error("模型在 150 秒内没有返回。请查看后端日志中的“生成笔记失败”或上游请求错误");
    } catch (e) {
      const message = (e as Error).message;
      setNoteSyncStatus("error");
      setNoteSyncMessage(message);
      setError("笔记整理失败：" + message);
    }
  };

  const formatTime = (seconds: number): string => {
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s.toString().padStart(2, "0")}`;
  };

  const handleDeleteQuestion = async () => {
    if (!deleteConfirm || !auth) return;
    setDeleteInProgress(true);
    setDeleteError("");
    try {
      const result = await chrome.runtime.sendMessage({
        type: "question:delete",
        payload: { questionId: deleteConfirm },
      });
      if (result?.success) {
        // 从对应分P中删除
        setQuestionsByPart(prev => {
          const updated = { ...prev };
          for (const part in updated) {
            updated[Number(part)] = updated[Number(part)].filter(q => q.id !== deleteConfirm);
          }
          return updated;
        });
        setSession(s => s ? { ...s, questionCount: Math.max(0, (s.questionCount ?? 1) - 1) } : null);
        setDeleteConfirm(null);
      } else {
        setDeleteError(result?.error || "删除失败，请稍后重试");
      }
    } catch (e) {
      console.error("删除失败:", e);
      setDeleteError((e as Error).message || "删除失败，请稍后重试");
    } finally {
      setDeleteInProgress(false);
    }
  };

  const openDeleteDialog = async (question: InterceptResult, index: number) => {
    setError(null);
    setDeleteError("");
    if (question.id) {
      setDeleteConfirm(question.id);
      return;
    }
    if (!session || !auth) {
      setError("无法刷新问题记录，请重新登录后重试");
      return;
    }
    setDeleteResolvingIndex(index);
    try {
      const refreshed = await loadCourseQuestions(session.videoUrl, session.id, auth.token);
      const grouped = groupQuestionsByPart(refreshed);
      setQuestionsByPart(grouped);
      setSession(current => current ? { ...current, questionCount: refreshed.length } : current);
      const candidates = grouped[currentPart] || [];
      const matched = candidates.find(candidate => candidate.id
        && candidate.time === question.time
        && candidate.question === question.question) || candidates[index];
      if (!matched?.id) throw new Error("后端中找不到这条问题，列表已刷新");
      setDeleteConfirm(matched.id);
    } catch (e) {
      setError("无法定位旧问题记录：" + (e as Error).message);
    } finally {
      setDeleteResolvingIndex(null);
    }
  };

  if (!authChecked) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-50 text-sm text-gray-500">
        正在检查登录状态...
      </div>
    );
  }

  // 未登录时使用独立认证页，避免进入不可操作的学习工作区。
  if (showLogin || !auth) {
    return (
      <div className="flex flex-col h-screen bg-gray-50">
        <header className="bg-indigo-600 text-white p-4">
          <h1 className="text-lg font-bold">OmniStudy - 登录</h1>
        </header>
        <div className="flex-1 flex items-center justify-center p-4">
          <form onSubmit={handleLogin} className="w-full max-w-sm bg-white rounded-lg shadow p-6">
            <h2 className="text-xl font-bold mb-4">{authMode === "login" ? "登录" : "创建账号"}</h2>
            {error && <p className="text-red-500 text-sm mb-2">{error}</p>}
            <input
              type="text"
              placeholder="用户名"
              className="w-full border rounded px-3 py-2 mb-3"
              value={loginForm.username}
              onChange={(e) => setLoginForm({ ...loginForm, username: e.target.value })}
            />
            {authMode === "register" && <input
              type="email"
              placeholder="邮箱（可选）"
              className="w-full border rounded px-3 py-2 mb-3"
              value={loginForm.email}
              onChange={(e) => setLoginForm({ ...loginForm, email: e.target.value })}
            />}
            <input
              type="password"
              placeholder="密码"
              className="w-full border rounded px-3 py-2 mb-4"
              value={loginForm.password}
              onChange={(e) => setLoginForm({ ...loginForm, password: e.target.value })}
            />
            <button type="submit" className="w-full bg-indigo-600 text-white rounded py-2 hover:bg-indigo-700">
              {authMode === "login" ? "登录" : "注册并登录"}
            </button>
            <button type="button" onClick={() => { setAuthMode(authMode === "login" ? "register" : "login"); setError(null); }}
              className="w-full mt-3 text-indigo-600 text-sm">
              {authMode === "login" ? "没有账号？立即注册" : "已有账号？返回登录"}
            </button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-screen bg-gray-50">
      <header className="bg-indigo-600 text-white p-4 shadow-md">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-lg font-bold">OmniStudy</h1>
            <p className="text-indigo-200 text-sm">苏格拉底式网课学习伴侣</p>
          </div>
          {auth ? (
            <div className="flex items-center gap-2">
              <span className="text-xs text-indigo-100">{auth.username || "已登录"}</span>
              <button onClick={handleLogout} className="text-xs bg-indigo-500 px-2 py-1 rounded">退出</button>
            </div>
          ) : (
            <button onClick={() => setShowLogin(true)} className="text-xs bg-white text-indigo-600 px-2 py-1 rounded">
              登录
            </button>
          )}
        </div>
      </header>

      <nav className="flex border-b bg-white px-3">
        <button onClick={() => setActiveSection("learning")} className={`flex-1 py-2.5 text-sm font-medium ${activeSection === "learning" ? "border-b-2 border-indigo-600 text-indigo-600" : "text-gray-500"}`}>学习</button>
        <button onClick={() => setActiveSection("agent")} className={`flex-1 py-2.5 text-sm font-medium ${activeSection === "agent" ? "border-b-2 border-indigo-600 text-indigo-600" : "text-gray-500"}`}>Agent</button>
        <button onClick={() => setActiveSection("notes")} className={`flex-1 py-2.5 text-sm font-medium ${activeSection === "notes" ? "border-b-2 border-indigo-600 text-indigo-600" : "text-gray-500"}`}>笔记</button>
        <button onClick={() => setActiveSection("models")} className={`flex-1 py-2.5 text-sm font-medium ${activeSection === "models" ? "border-b-2 border-indigo-600 text-indigo-600" : "text-gray-500"}`}>模型</button>
        {auth?.role === "ADMIN" && <button onClick={() => setActiveSection("admin")} className={`flex-1 py-2.5 text-sm font-medium ${activeSection === "admin" ? "border-b-2 border-indigo-600 text-indigo-600" : "text-gray-500"}`}>管理</button>}
      </nav>

      {activeSection === "admin" && auth?.role === "ADMIN" ? (
        <AdminPanel />
      ) : activeSection === "models" ? (
        <AiSettingsPanel />
      ) : activeSection === "agent" ? (
        <AgentPanel sessionId={session?.id} authenticated={!!auth} />
      ) : activeSection === "notes" ? (
        <NotePanel note={session?.note ?? null} onGenerate={syncNoteNow} sessionActive={!!session && !!auth}
          refreshKey={noteRefreshKey} syncStatus={noteSyncStatus} syncMessage={noteSyncMessage} />
      ) : <>

      {session && (
        <div className="bg-white border-b px-4 py-3">
          <h2 className="font-medium text-gray-800 truncate">{session.courseTitle}</h2>
          <div className="flex items-center gap-3 text-sm text-gray-500 mt-1">
            <span className="flex items-center gap-1">
              <span className="w-2 h-2 bg-green-500 rounded-full"></span>
              {session.platform}
            </span>
            <span>问题: {session.questionCount}</span>
            <span>{formatTime(currentTime)}</span>
          </div>
        </div>
      )}

      {currentSubtitle && (
        <div className="bg-yellow-50 border-b px-4 py-2">
          <p className="text-sm text-gray-700">{currentSubtitle}</p>
        </div>
      )}

      {error && (
        <div className="bg-red-50 border-b border-red-200 px-4 py-2">
          <p className="text-sm text-red-600">{error}</p>
        </div>
      )}

      <div className="p-4 space-y-2">
        {!auth && (
          <p className="text-sm text-center text-gray-500 mb-2">
            请先登录以使用拦截功能
          </p>
        )}
        <button
          onClick={handleIntercept}
          disabled={!session || !auth || isIntercepting}
          className={`w-full py-3 px-4 rounded-lg font-medium transition-colors ${
            !session || !auth || isIntercepting
              ? "bg-gray-300 text-gray-500 cursor-not-allowed"
              : "bg-indigo-600 text-white hover:bg-indigo-700"
          }`}
        >
          {isIntercepting ? "分析中..." : "触发拦截"}
        </button>
      </div>

      <div className="flex-1 overflow-auto px-4">
        {/* 紧凑分集导航，避免连续观看较多集数时标签挤满侧边栏。 */}
        <div className="sticky top-0 z-10 bg-gray-50 py-2 mb-2">
          <div className="flex items-center gap-2 rounded-lg border border-gray-200 bg-white p-2 shadow-sm">
            <button
              type="button"
              onClick={() => setCurrentPart((part) => Math.max(1, part - 1))}
              disabled={currentPart <= 1}
              className="shrink-0 rounded-md px-3 py-2 text-xs font-medium text-gray-600 transition-colors hover:bg-gray-100 disabled:cursor-not-allowed disabled:text-gray-300 disabled:hover:bg-transparent"
              aria-label="上一集"
            >
              ‹ 上一集
            </button>

            <select
              value={currentPart}
              onChange={(event) => setCurrentPart(Number(event.target.value))}
              className="min-w-0 flex-1 rounded-md border border-gray-200 bg-gray-50 px-2 py-2 text-center text-sm font-medium text-gray-700 outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
              aria-label="选择集数"
            >
              {navigableParts.map((part) => (
                <option key={part} value={part}>
                  第 {part} 集（{questionsByPart[part]?.length || 0} 题）
                </option>
              ))}
            </select>

            <button
              type="button"
              onClick={() => setCurrentPart((part) => Math.min(maxNavigablePart, part + 1))}
              disabled={currentPart >= maxNavigablePart}
              className="shrink-0 rounded-md px-3 py-2 text-xs font-medium text-gray-600 transition-colors hover:bg-gray-100 disabled:cursor-not-allowed disabled:text-gray-300 disabled:hover:bg-transparent"
              aria-label="下一集"
            >
              下一集 ›
            </button>
          </div>
        </div>

        <h3 className="text-sm font-medium text-gray-500 mb-2">
          第{currentPart}集问题 ({currentPartQuestions.length})
        </h3>

        {currentPartQuestions.length === 0 ? (
          <div className="text-center py-8 text-gray-400">
            <p>第{currentPart}集暂无问题记录</p>
            <p className="text-sm mt-1">点击上方按钮手动触发拦截</p>
          </div>
        ) : (
          <div className="space-y-3">
            {currentPartQuestions.map((q, i) => (
              <div key={q.id || i} className="bg-white rounded-lg shadow p-3 relative">
                <button
                  onClick={() => void openDeleteDialog(q, i)}
                  disabled={deleteResolvingIndex !== null}
                  className="absolute top-2 right-2 text-gray-400 hover:text-red-500 transition-colors p-1 disabled:cursor-not-allowed disabled:opacity-40"
                  title={q.id ? "删除" : "刷新并删除旧记录"}
                  aria-label="删除问题记录"
                >
                  <span aria-hidden="true">{deleteResolvingIndex === i ? "…" : "🗑"}</span>
                </button>
                <div className="flex items-start justify-between pr-8">
                  <span className="text-xs bg-indigo-100 text-indigo-700 px-2 py-0.5 rounded">
                    {q.questionType.replace("_", " ")}
                  </span>
                  <span className="text-xs text-gray-400">{q.time}</span>
                </div>
                <p className="mt-2 text-sm text-gray-800">{q.question}</p>
                <p className="mt-1 text-xs text-gray-500">
                  置信度: {Math.round(q.confidence * 100)}%
                </p>
              </div>
            ))}
          </div>
        )}
      </div>

      <footer className="bg-gray-100 px-4 py-2 text-center text-xs text-gray-400">
        {session ? `Session: ${session.id.slice(0, 8)}...` : "等待视频连接..."}
      </footer>
      </>}

      {deleteConfirm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 max-w-sm mx-4 shadow-xl">
            <h3 className="text-lg font-bold mb-2 text-gray-800">确认删除</h3>
            <p className="text-gray-600 mb-4">确定要删除这个问题记录吗？此操作不可撤销。</p>
            {deleteError && (
              <p className="mb-4 rounded bg-red-50 p-2 text-sm text-red-600">删除失败：{deleteError}</p>
            )}
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => { setDeleteConfirm(null); setDeleteError(""); }}
                disabled={deleteInProgress}
                className="px-4 py-2 text-gray-600 hover:bg-gray-100 rounded transition-colors disabled:opacity-50"
              >
                取消
              </button>
              <button
                onClick={handleDeleteQuestion}
                disabled={deleteInProgress}
                className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600 transition-colors disabled:bg-red-300"
              >
                {deleteInProgress ? "删除中…" : "删除"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

function normalizeSession(raw: BackendSession): Session {
  return {
    ...raw,
    courseTitle: raw.courseTitle || raw.videoTitle || "未知视频",
    platform: raw.platform || "bilibili",
    questionCount: Number.isFinite(raw.questionCount) ? raw.questionCount : 0,
    note: raw.note ?? null,
  };
}

async function loadQuestions(sessionId: string, _token: string) {
  try {
    const result = await chrome.runtime.sendMessage({
      type: "question:list-session", payload: { sessionId },
    });
    if (result.success && Array.isArray(result.data)) {
      const restored = result.data.map((q: { id: string } & InterceptResult) => ({
        shouldIntercept: true,
        time: q.time,
        part: q.part || 1,
        coreConcept: q.coreConcept,
        evidence: q.evidence,
        question: q.question,
        questionType: q.questionType,
        difficulty: q.difficulty,
        confidence: q.confidence,
        options: q.options || [],
        id: q.id,
      }));
      return restored as InterceptResult[];
    }
  } catch (e) {
    console.error("加载问题记录失败:", e);
  }
  return [] as InterceptResult[];
}

/**
 * 兼容旧版本切分P时误建多个 session 的数据：同一个 BV 号下的题目
 * 合并到一份前端视图中。修复后的新数据仍会一直使用同一个 session。
 */
async function loadCourseQuestions(videoUrl: string, currentSessionId: string, token: string) {
  const courseId = extractCourseId(videoUrl);
  const sessionIds = new Set<string>([currentSessionId]);

  if (courseId) {
    try {
      const result = await chrome.runtime.sendMessage({ type: "session:list" });
      if (result?.success && Array.isArray(result.data)) {
        for (const candidate of result.data as BackendSession[]) {
          if (candidate.id && extractCourseId(candidate.videoUrl || "") === courseId) {
            sessionIds.add(candidate.id);
          }
        }
      }
    } catch (e) {
      console.error("加载课程会话失败:", e);
    }
  }

  const batches = await Promise.all(
    [...sessionIds].map((sessionId) => loadQuestions(sessionId, token)),
  );
  const seen = new Set<string>();
  return batches.flat().filter((question) => {
    const key = question.id || `${question.part}:${question.time}:${question.question}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

// 按分P分组
function groupQuestionsByPart(questions: InterceptResult[]): Record<number, InterceptResult[]> {
  return questions.reduce((acc, q) => {
    const part = q.part || 1;
    if (!acc[part]) acc[part] = [];
    acc[part].push(q);
    return acc;
  }, {} as Record<number, InterceptResult[]>);
}

// 从URL提取分P编号
function extractPartFromUrl(url: string): number {
  const match = url.match(/[?&]p=(\d+)/);
  return match ? parseInt(match[1]) : 1;
}

function extractCourseId(url: string): string {
  const match = url.match(/video\/(BV\w+)/);
  return match ? match[1] : "";
}

const delay = (ms: number) => new Promise<void>((resolve) => window.setTimeout(resolve, ms));
