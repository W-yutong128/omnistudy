import { backendUrl } from "./config";

console.log("OmniStudy Service Worker loaded");

type StoredAuth = {
  token: string;
  refreshToken?: string;
  userId: string;
  username?: string;
  role?: "USER" | "ADMIN";
  expiresAt?: string;
};

type ApiEnvelope<T = unknown> = { success: boolean; data?: T; error?: string };

async function readApiResponse<T = unknown>(response: Response): Promise<ApiEnvelope<T>> {
  const text = await response.text();
  if (!text.trim()) {
    return { success: false, error: `后端未返回内容（HTTP ${response.status || "网络异常"}），请稍后重试` };
  }
  try {
    return JSON.parse(text) as ApiEnvelope<T>;
  } catch {
    return { success: false, error: `后端返回了无法解析的内容（HTTP ${response.status}）` };
  }
}

async function authenticatedFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const { auth } = await chrome.storage.local.get("auth") as { auth?: StoredAuth };
  if (!auth?.token) return new Response(JSON.stringify({ success: false, error: "未登录" }), { status: 401 });
  const execute = (token: string) => {
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${token}`);
    return fetch(url, { ...init, headers });
  };
  let response = await execute(auth.token);
  // 兼容旧版后端把“未认证”返回为空 body 403 的行为；带内容的真实 403 不续签。
  const legacyUnauthenticated = response.status === 403
    && !(await response.clone().text()).trim();
  if ((response.status !== 401 && !legacyUnauthenticated) || !auth.refreshToken) return response;

  const refresh = await fetch(backendUrl("/api/auth/refresh"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken: auth.refreshToken }),
  });
  const refreshed = await readApiResponse<StoredAuth>(refresh);
  if (!refresh.ok || !refreshed.success) {
    await chrome.storage.local.remove("auth");
    return new Response(JSON.stringify({ success: false, error: "登录已过期，请重新登录" }), {
      status: 401,
      headers: { "Content-Type": "application/json" },
    });
  }
  const nextAuth: StoredAuth = { ...auth, ...refreshed.data };
  await chrome.storage.local.set({ auth: nextAuth });
  return execute(nextAuth.token);
}

chrome.runtime.onInstalled.addListener(() => {
  console.log("OmniStudy extension installed");
});

// sidePanel API (Chrome 114+)
if (chrome.sidePanel) {
  try {
    chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => {});
    chrome.sidePanel.setOptions({ path: "sidepanel.html" }).catch(() => {});
  } catch {
    // API not available
  }

  chrome.action.onClicked.addListener(async (tab) => {
    if (tab.id) {
      try {
        await chrome.sidePanel.open({ tabId: tab.id });
      } catch {
        // Ignore
      }
    }
  });
}

// Message router: content script <-> side panel
chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  let didRespond = false;
  const respondOnce = (data: unknown) => {
    if (didRespond) return;
    didRespond = true;
    sendResponse(data);
  };

  // Handle auto:intercept from content script (bypass CORS)
  if (message.type === "auto:intercept") {
    (async () => {
      try {
        const { sessionId, time, screenshot, before, current, after, difficulty, part } = message.payload ?? {};
        const { auth } = await chrome.storage.local.get("auth");
        if (!auth?.token) {
          respondOnce({ success: false, error: "未登录" });
          return;
        }
        const res = await authenticatedFetch(backendUrl("/api/intercept"), {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${auth.token}`,
          },
          body: JSON.stringify({ sessionId, time, screenshot, before, current, after, difficulty, auto: true, part }),
        });
        const json = await readApiResponse(res);
        respondOnce(json);
      } catch (e) {
        respondOnce({ success: false, error: (e as Error).message });
      }
    })();
    return true;
  }

  if (message.type === "choice:answer") {
    (async () => {
      try {
        const { auth } = await chrome.storage.local.get("auth");
        if (!auth?.token) return respondOnce({ success: false, error: "未登录" });
        const { questionId, optionId } = message.payload ?? {};
        const res = await authenticatedFetch(backendUrl(`/api/intercept/${questionId}/answer`), {
          method: "POST",
          headers: { "Content-Type": "application/json", "Authorization": `Bearer ${auth.token}` },
          body: JSON.stringify({ optionId }),
        });
        const json = await readApiResponse(res);
        respondOnce(res.ok && json.success ? { success: true, data: json.data } : { success: false, error: json.error ?? `HTTP ${res.status}` });
      } catch (e) {
        respondOnce({ success: false, error: (e as Error).message });
      }
    })();
    return true;
  }

  if (message.type === "review:answer") {
    (async () => {
      try {
        const { auth } = await chrome.storage.local.get("auth");
        if (!auth?.token) return respondOnce({ success: false, error: "未登录" });
        const { questionId, optionId } = message.payload ?? {};
        const res = await authenticatedFetch(backendUrl(`/api/intercept/${questionId}/answer`), {
          method: "POST",
          headers: { "Content-Type": "application/json", "Authorization": `Bearer ${auth.token}` },
          body: JSON.stringify({ optionId }),
        });
        const json = await readApiResponse(res);
        respondOnce(res.ok && json.success ? { success: true, data: json.data } : { success: false, error: json.error ?? `HTTP ${res.status}` });
      } catch (e) {
        respondOnce({ success: false, error: (e as Error).message });
      }
    })();
    return true;
  }

  if (message.type === "question:delete") {
    (async () => {
      try {
        const questionId = message.payload?.questionId;
        if (typeof questionId !== "string" || !questionId.trim()) {
          return respondOnce({ success: false, error: "问题记录缺少 ID，请刷新侧边栏后重试" });
        }
        const res = await authenticatedFetch(backendUrl(`/api/intercept/${encodeURIComponent(questionId)}`), {
          method: "DELETE",
        });
        const json = await readApiResponse(res);
        respondOnce(res.ok && json.success
          ? { success: true }
          : { success: false, error: json.error ?? `HTTP ${res.status}` });
      } catch (e) {
        respondOnce({ success: false, error: (e as Error).message });
      }
    })();
    return true;
  }

  if (message.type === "question:list-session" || message.type === "session:list") {
    (async () => {
      try {
        const path = message.type === "session:list"
          ? "/api/session"
          : `/api/intercept/session/${encodeURIComponent(message.payload?.sessionId || "")}`;
        const res = await authenticatedFetch(backendUrl(path));
        const json = await readApiResponse(res);
        respondOnce(res.ok && json.success
          ? { success: true, data: json.data }
          : { success: false, error: json.error ?? `HTTP ${res.status}` });
      } catch (e) {
        respondOnce({ success: false, error: (e as Error).message });
      }
    })();
    return true;
  }

  if (message.type?.startsWith("question-bank:")) {
    (async () => {
      try {
        const { auth } = await chrome.storage.local.get("auth");
        if (!auth?.token) return respondOnce({ success: false, error: "未登录" });
        const payload = message.payload ?? {};
        let path = "/api/question-bank";
        let method = "GET";
        let body: unknown;
        if (message.type === "question-bank:search") {
          const params = new URLSearchParams();
          if (payload.query) params.set("query", payload.query);
          if (payload.subject) params.set("subject", payload.subject);
          params.set("limit", String(payload.limit || 20));
          path += `?${params}`;
        } else if (message.type === "question-bank:deliver") {
          method = "POST"; path += `/${payload.id}/deliver`;
          body = { knowledgePointId: payload.knowledgePointId || null };
        }
        const res = await authenticatedFetch(backendUrl(path), {
          method,
          headers: { "Content-Type": "application/json", "Authorization": `Bearer ${auth.token}` },
          ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        });
        const json = await readApiResponse(res);
        respondOnce(res.ok && json.success ? { success: true, data: json.data } : { success: false, error: json.error ?? `HTTP ${res.status}` });
      } catch (e) {
        respondOnce({ success: false, error: (e as Error).message });
      }
    })();
    return true;
  }

  // Authentication responses are stored only in chrome.storage, never in page localStorage.
  if (message.type === "auth:login" || message.type === "auth:register") {
    (async () => {
      try {
        const endpoint = message.type === "auth:register" ? "register" : "login";
        const res = await fetch(backendUrl(`/api/auth/${endpoint}`), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(message.payload ?? {}),
        });
        const json = await readApiResponse(res);
        if (!res.ok || !json.success) {
          respondOnce({ success: false, error: json.error ?? `HTTP ${res.status}` });
          return;
        }
        await chrome.storage.local.set({
          auth: json.data,
        });
        respondOnce({ success: true, data: json.data });
      } catch (e) {
        respondOnce({ success: false, error: (e as Error).message });
      }
    })();
    return true;
  }

  if (message.type === "auth:me") {
    (async () => {
      try {
        const res = await authenticatedFetch(backendUrl("/api/auth/me"));
        const json = await readApiResponse(res);
        respondOnce(res.ok && json.success ? { success: true, data: json.data } : { success: false, error: json.error ?? `HTTP ${res.status}` });
      } catch (e) {
        respondOnce({ success: false, error: (e as Error).message });
      }
    })();
    return true;
  }

  if (message.type === "auth:logout") {
    (async () => {
      try {
        const { auth } = await chrome.storage.local.get("auth") as { auth?: StoredAuth };
        if (auth?.refreshToken) {
          await fetch(backendUrl("/api/auth/logout"), {
            method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken: auth.refreshToken }),
          });
        }
      } finally {
        await chrome.storage.local.remove("auth");
        respondOnce({ success: true });
      }
    })();
    return true;
  }

  if (message.type?.startsWith("ai-settings:")) {
    (async () => {
      try {
        let method = "GET";
        let path = "/api/settings/ai-provider";
        if (message.type === "ai-settings:save") method = "PUT";
        if (message.type === "ai-settings:delete") method = "DELETE";
        if (message.type === "ai-settings:test") { method = "POST"; path += "/test"; }
        const res = await authenticatedFetch(backendUrl(path), {
          method,
          headers: { "Content-Type": "application/json" },
          ...(method === "PUT" ? { body: JSON.stringify(message.payload ?? {}) } : {}),
        });
        const json = await readApiResponse(res);
        respondOnce(res.ok && json.success
          ? { success: true, data: json.data }
          : { success: false, error: json.error ?? `HTTP ${res.status}` });
      } catch (e) {
        respondOnce({ success: false, error: (e as Error).message });
      }
    })();
    return true;
  }

  if (message.type?.startsWith("admin:")) {
    (async () => {
      try {
        const payload = message.payload ?? {};
        let path = "/api/admin/overview";
        let method = "GET";
        let body: unknown;
        if (message.type === "admin:users") path = "/api/admin/users";
        if (message.type === "admin:update-user") {
          path = `/api/admin/users/${payload.userId}`;
          method = "PUT";
          body = payload.update;
        }
        const res = await authenticatedFetch(backendUrl(path), {
          method,
          headers: { "Content-Type": "application/json" },
          ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        });
        const json = await readApiResponse(res);
        respondOnce(res.ok && json.success ? { success: true, data: json.data } : { success: false, error: json.error ?? `HTTP ${res.status}` });
      } catch (e) {
        respondOnce({ success: false, error: (e as Error).message });
      }
    })();
    return true;
  }

  if (message.type === "agent:chat") {
    (async () => {
      try {
        const { auth } = await chrome.storage.local.get("auth");
        if (!auth?.token) return respondOnce({ success: false, error: "请先登录" });
        const res = await authenticatedFetch(backendUrl("/api/agent-v2/chat"), {
          method: "POST",
          headers: { "Content-Type": "application/json", "Authorization": `Bearer ${auth.token}` },
          body: JSON.stringify(message.payload ?? {}),
        });
        const json = await readApiResponse(res);
        respondOnce(res.ok && json.success
          ? { success: true, data: json.data }
          : { success: false, error: json.error ?? `HTTP ${res.status}` });
      } catch (e) {
        respondOnce({ success: false, error: (e as Error).message });
      }
    })();
    return true;
  }

  // A native application is opened only after an explicit click in AgentPanel.
  if (message.type === "idea:open") {
    (async () => {
      try {
        const response = await chrome.runtime.sendNativeMessage("com.omnistudy.idea", {
          action: "open",
          projectPath: message.payload?.projectPath,
          filePath: message.payload?.filePath,
        });
        respondOnce(response?.success
          ? { success: true, data: response }
          : { success: false, error: response?.error || "IDEA 桥接程序返回失败" });
      } catch (error) {
        respondOnce({ success: false, error: `IDEA 桥接不可用：${(error as Error).message}` });
      }
    })();
    return true;
  }

  if (message.type?.startsWith("notes:")) {
    (async () => {
      try {
        const { auth } = await chrome.storage.local.get("auth");
        if (!auth?.token) return respondOnce({ success: false, error: "请先登录" });
        const payload = message.payload ?? {};
        let path = "/api/note";
        let method = "GET";
        let body: unknown;
        if (message.type === "notes:list") {
          const params = new URLSearchParams();
          if (payload.query) params.set("query", payload.query);
          if (payload.inbox) params.set("inbox", "true");
          if (payload.review) params.set("review", "true");
          if (params.toString()) path += `?${params}`;
        } else if (message.type === "notes:session-status") {
          path += `/session/${payload.sessionId}`;
        } else if (message.type === "notes:capture-subtitles") {
          method = "POST"; path += `/session/${payload.sessionId}/subtitles`; body = { part: payload.part, chunks: payload.chunks };
        } else if (message.type === "notes:auto-sync") {
          method = "POST"; path += `/session/${payload.sessionId}/auto-sync?finalize=${payload.finalize === true}`;
        } else if (message.type === "notes:create") {
          method = "POST"; body = payload;
        } else if (message.type === "notes:update") {
          method = "PUT"; path += `/${payload.id}`; body = payload.note;
        } else if (message.type === "notes:delete") {
          method = "DELETE"; path += `/${payload.id}`;
        } else if (message.type === "notes:generate-material") {
          method = "POST"; path += `/${payload.id}/study-materials`; body = { kind: payload.kind };
        }
        const res = await authenticatedFetch(backendUrl(path), {
          method,
          headers: { "Content-Type": "application/json", "Authorization": `Bearer ${auth.token}` },
          ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        });
        const json = await readApiResponse(res);
        respondOnce(res.ok && json.success ? { success: true, data: json.data } : { success: false, error: json.error ?? `HTTP ${res.status}` });
      } catch (e) {
        respondOnce({ success: false, error: (e as Error).message });
      }
    })();
    return true;
  }

  // Handle trigger:intercept from side panel
  if (message.type === "trigger:intercept") {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      if (tabs[0]?.id) {
        chrome.tabs.sendMessage(tabs[0].id, message)
          .then(respondOnce)
          .catch((err) => {
            console.error("[SW] Send to content script failed:", err);
            respondOnce({ error: err.message });
          });
        return;
      }
      respondOnce({ error: "No active tab" });
    });
    return true;
  }

  // runtime.sendMessage already delivers content-script events to extension
  // pages (including the side panel). Re-broadcasting here makes
  // context:ready arrive twice and can create two competing sessions.
  sendResponse({ received: true });
  return false;
});
