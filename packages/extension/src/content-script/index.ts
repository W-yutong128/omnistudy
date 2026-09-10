/**
 * Content Script 入口 - 注入到 B 站视频页
 */
import { detectBilibiliVideo } from "./bilibili/video-detector";
import { captureFrame } from "./bilibili/screenshot";
import { getCurrentSubtitles } from "./bilibili/subtitle-reader";
import type { InterceptInput, InterceptResult } from "@omnistudy/shared";

// ============ 自动拦截配置 ============
const AUTO_INTERCEPT_INTERVAL_SEC = 300; // 每 5 分钟检查一次是否值得提问
const QUESTION_COOLDOWN_SEC = 480; // 两道真实弹题至少间隔 8 分钟
const QUESTION_WARMUP_SEC = 180; // 视频开头 3 分钟不打断
const QUESTION_END_GUARD_SEC = 120; // 最后 2 分钟不打断
const MAX_QUESTIONS_PER_HOUR = 6;
const MIN_INTERCEPT_CONFIDENCE = 0.85;
const MIN_INTERCEPT_CONTEXT_CHARS = 80;
const MAX_INTERCEPT_CONTEXT_SIMILARITY = 0.85;
// =====================================

type SubtitleEntry = { t: number; text: string; part: number };
const SUBTITLE_HISTORY: SubtitleEntry[] = [];
const SUBTITLE_PENDING: SubtitleEntry[] = [];
const UPDATE_INTERVAL_MS = 1000;
const NOTE_SYNC_INTERVAL_SEC = 180;
const SUBTITLE_FLUSH_INTERVAL_SEC = 30;

let videoEl: HTMLVideoElement | null = null;
let lastCheckTime = 0;
let updateInterval: number | null = null;
let waitVideoTimeout: number | null = null;
let urlCheckInterval: number | null = null;
let isContextValid = true;
let isNavigating = false;
let lastPeriodicCheckTime = 0; // 上次定期语义检查的时间戳（秒）
let lastKnownVideoUrl = ""; // 记录上次检测到的视频 URL，用于检测分P切换
let lastKnownPart = 1;
let listenersSetup = false; // 防止重复注册消息监听器
let periodicUpdatesStarted = false; // 防止重复启动定时更新
let lastObservedVideoTime = 0;
let stagnantVideoTicks = 0;
let lastSubtitleFlushTime = 0;
let lastNoteSyncTime = 0;
let lastCapturedSubtitle = "";
let noteSyncInFlight = false;
let lastQuestionTime = Number.NEGATIVE_INFINITY;
let questionWindowStartedAt = 0;
let questionsInWindow = 0;
let questionModal: HTMLDialogElement | HTMLDivElement | null = null;
let wasPlayingBeforeQuestion = false;
let lastInterceptContextText = "";

function isContextInvalidatedError(error: unknown): boolean {
  return error instanceof Error
    ? error.message.includes("Extension context invalidated")
    : String(error).includes("Extension context invalidated");
}

function invalidateExtensionContext() {
  if (!isContextValid) return;
  isContextValid = false;
  listenersSetup = false;
  periodicUpdatesStarted = false;

  if (updateInterval !== null) {
    window.clearInterval(updateInterval);
    updateInterval = null;
  }
  if (waitVideoTimeout !== null) {
    window.clearTimeout(waitVideoTimeout);
    waitVideoTimeout = null;
  }
  if (urlCheckInterval !== null) {
    window.clearInterval(urlCheckInterval);
    urlCheckInterval = null;
  }
  videoEl?.removeEventListener("timeupdate", onTimeUpdate);
  videoEl?.removeEventListener("pause", onPause);
  videoEl?.removeEventListener("ended", onEnded);
  videoEl = null;
  closeQuestionModal(false);

  console.info("[OmniStudy] 扩展已重新加载，旧页面脚本已停止；请刷新当前页面以恢复连接");
}

// 扩展重新加载后，旧 content script 的 chrome.runtime 会永久失效。
window.addEventListener("error", (e) => {
  if (e.message?.includes("Extension context invalidated")) {
    invalidateExtensionContext();
    e.preventDefault();
    e.stopPropagation();
  }
});

function safeSendMessage(message: { type: string; payload?: unknown }) {
  if (!isContextValid) return;
  try {
    chrome.runtime.sendMessage(message, () => {
      if (chrome.runtime.lastError?.message?.includes("Extension context invalidated")) {
        invalidateExtensionContext();
      }
    });
  } catch (e) {
    if (isContextInvalidatedError(e)) {
      invalidateExtensionContext();
      return;
    }
    console.error("[OmniStudy] safeSendMessage error:", e);
  }
}

function bindVideoElement(nextVideo: HTMLVideoElement, resetSchedule: boolean) {
  if (videoEl === nextVideo) return;

  videoEl?.removeEventListener("timeupdate", onTimeUpdate);
  videoEl?.removeEventListener("pause", onPause);
  videoEl?.removeEventListener("ended", onEnded);
  videoEl = nextVideo;
  videoEl.addEventListener("timeupdate", onTimeUpdate);
  videoEl.addEventListener("pause", onPause);
  videoEl.addEventListener("ended", onEnded);

  SUBTITLE_HISTORY.length = 0;
  lastInterceptContextText = "";
  lastCheckTime = videoEl.currentTime;
  if (resetSchedule) {
    lastPeriodicCheckTime = videoEl.currentTime;
  } else {
    // 同一分P内纠正误绑播放器时保留原来的拦截周期，避免每次换绑
    // 都把下次检查时间继续向后推迟。
    lastPeriodicCheckTime = Math.min(lastPeriodicCheckTime, videoEl.currentTime);
  }
  lastObservedVideoTime = videoEl.currentTime;
  lastSubtitleFlushTime = videoEl.currentTime;
  lastNoteSyncTime = videoEl.currentTime;
  questionWindowStartedAt = videoEl.currentTime;
  questionsInWindow = 0;
  lastQuestionTime = Number.NEGATIVE_INFINITY;
  stagnantVideoTicks = 0;
  console.log(`[OmniStudy] 已绑定有效播放器，当前时间 ${formatTime(videoEl.currentTime)}`);
}

/**
 * B 站切集时经常复用同一个 video 节点。所有依赖“本集播放时间”的状态必须
 * 一起重置，否则上一集较大的时间戳会让新一集永远达不到弹题冷却条件。
 */
function resetEpisodeState(currentTime: number) {
  SUBTITLE_HISTORY.length = 0;
  lastInterceptContextText = "";
  lastCapturedSubtitle = "";
  lastCheckTime = currentTime;
  lastPeriodicCheckTime = currentTime;
  lastObservedVideoTime = currentTime;
  lastSubtitleFlushTime = currentTime;
  lastNoteSyncTime = currentTime;
  lastQuestionTime = Number.NEGATIVE_INFINITY;
  questionWindowStartedAt = currentTime;
  questionsInWindow = 0;
  stagnantVideoTicks = 0;
}

function isVideoElementUsable(video: HTMLVideoElement): boolean {
  if (!video.isConnected) return false;
  const rect = video.getBoundingClientRect();
  const style = window.getComputedStyle(video);
  return rect.width > 1
    && rect.height > 1
    && style.display !== "none"
    && style.visibility !== "hidden"
    && Number(style.opacity || 1) > 0
    && Boolean(video.currentSrc || video.src);
}

async function init() {
  const waitVideo = () => {
    if (!isContextValid) return;
    const ctx = detectBilibiliVideo();
    if (ctx?.video) {
      const navigationSettling = isNavigating;
      const detectedPart = getCurrentPart();
      const partChanged = detectedPart !== lastKnownPart;
      // 检查是否是新的视频元素（分P切换）
      const currentVideoSrc = ctx.video.src || location.href;
      if (videoEl !== ctx.video) {
        bindVideoElement(ctx.video, true);
        console.log("[OmniStudy] 视频切换/重新检测，重新监听");
      }

      // B站经常复用同一个 video 元素。无论元素是否变化，只要是切集后的
      // 重新检测，都要清空上一集字幕并从新一集当前时间重新计时。
      if (navigationSettling || partChanged) {
        resetEpisodeState(ctx.video.currentTime);
        isNavigating = false;
        console.log(`[OmniStudy] 新分P已就绪，自动拦截计时从 ${formatTime(ctx.video.currentTime)} 重新开始`);
      }
      
      // 检查 URL 是否变化（分P 切换）
      const currentUrl = location.href;
      if ((lastKnownVideoUrl && currentUrl !== lastKnownVideoUrl) || navigationSettling || partChanged) {
        console.log("[OmniStudy] 检测到 URL 变化（分P切换），发送 context:ready");
        safeSendMessage({ type: "context:ready", payload: { url: currentUrl, part: detectedPart, totalParts: getTotalParts() } });
      }
      if (!lastKnownVideoUrl) {
        safeSendMessage({ type: "context:ready", payload: { url: currentUrl, part: detectedPart, totalParts: getTotalParts() } });
      }
      lastKnownVideoUrl = currentUrl;
      lastKnownPart = detectedPart;
      
      if (!isContextValid) return;
      setupListeners(ctx);
      startPeriodicUpdates();
    } else {
      waitVideoTimeout = window.setTimeout(waitVideo, 2000);
    }
  };
  
  // 监听 URL 变化（B站 SPA 分P 切换不会完全刷新页面）
  urlCheckInterval = window.setInterval(() => {
    const currentUrl = location.href;
    if (lastKnownVideoUrl && currentUrl !== lastKnownVideoUrl) {
      lastKnownVideoUrl = currentUrl;
      console.log("[OmniStudy] URL 变化，等待页面稳定后重新检测视频元素");
      // URL 监听必须始终保留，否则只能处理第一次切集。
      isNavigating = true;
      closeQuestionModal(false);
      SUBTITLE_HISTORY.length = 0;
      lastInterceptContextText = "";
      // 延迟检测，等 B站页面完成跳转
      if (waitVideoTimeout !== null) window.clearTimeout(waitVideoTimeout);
      waitVideoTimeout = window.setTimeout(waitVideo, 2000);
    }
  }, 1000);
  
  waitVideo();
}

function setupListeners(ctx: ReturnType<typeof detectBilibiliVideo>) {
  if (!videoEl || !ctx) return;
  if (listenersSetup) return; // 防止重复注册
  listenersSetup = true;

  videoEl.addEventListener("timeupdate", onTimeUpdate);
  videoEl.addEventListener("pause", onPause);

  try {
    chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
      if (!isContextValid) {
        sendResponse({ error: "Extension context invalidated" });
        return false;
      }
      if (msg.type === "video:pause") {
        videoEl?.pause();
        sendResponse({ paused: true });
      } else if (msg.type === "video:resume") {
        videoEl?.play();
        sendResponse({ playing: true });
      } else if (msg.type === "trigger:intercept") {
        handleIntercept(msg.difficulty).then(sendResponse).catch((e) => sendResponse({ error: e.message }));
        return true;
      } else if (msg.type === "get:title") {
        const title = document.title.replace("_哔哩哔哩 (゜-゜)つロ 干杯~-bilibili", "").trim();
        sendResponse({ title });
      } else if (msg.type === "get:currentTime") {
        sendResponse({ currentTime: videoEl?.currentTime ?? 0, part: getCurrentPart(), totalParts: getTotalParts() });
      } else if (msg.type === "agent:get-context") {
        const time = videoEl?.currentTime ?? 0;
        const recent = SUBTITLE_HISTORY.filter(item => time - item.t <= 120);
        const split = Math.max(0, recent.length - 1);
        sendResponse({
          currentTime: time,
          part: getCurrentPart(),
          before: recent.slice(0, split).map(item => item.text).join(" "),
          current: recent[split]?.text || getCurrentSubtitles()?.current || "",
          after: "",
          screenshot: videoEl ? captureFrame(videoEl) : "",
        });
      } else if (msg.type === "agent:seek") {
        if (!videoEl || typeof msg.time !== "number" || !Number.isFinite(msg.time)) {
          sendResponse({ success: false, error: "无法定位当前播放器" });
        } else {
          videoEl.currentTime = Math.max(0, Math.min(msg.time, Number.isFinite(videoEl.duration) ? videoEl.duration : msg.time));
          sendResponse({ success: true, currentTime: videoEl.currentTime });
        }
      } else if (msg.type === "notes:flush-current") {
        // 手动刷新笔记时，不等待 30 秒批处理周期，先把当前字幕立即送到后端。
        const current = getCurrentSubtitles()?.current;
        if (current && current !== lastCapturedSubtitle) {
          recordSubtitle(videoEl?.currentTime ?? 0, current);
          lastCapturedSubtitle = current;
        }
        flushSubtitles()
          .then(() => sendResponse({ success: true }))
          .catch((e) => sendResponse({ success: false, error: (e as Error).message }));
        return true;
      }
      return false;
    });
  } catch (e) {
    if (isContextInvalidatedError(e)) invalidateExtensionContext();
  }
}

function startPeriodicUpdates() {
  if (!isContextValid) return;
  if (periodicUpdatesStarted) return; // 防止重复启动
  periodicUpdatesStarted = true;
  if (updateInterval) clearInterval(updateInterval);
  // 初始化为当前视频时间，避免首次启动时立即触发
  lastPeriodicCheckTime = videoEl?.currentTime ?? 0;
  updateInterval = window.setInterval(() => {
    if (!videoEl || !isContextValid) return;

    const observedTime = videoEl.currentTime;
    const timeIsAdvancing = observedTime > lastObservedVideoTime + 0.05;
    if (!videoEl.paused && !videoEl.ended && !timeIsAdvancing) {
      stagnantVideoTicks += 1;
    } else {
      stagnantVideoTicks = 0;
    }
    lastObservedVideoTime = observedTime;

    // 当前播放器正常走时就保持绑定，防止多个候选元素评分波动造成反复换绑。
    // 仅当当前节点不可用，或连续多秒停滞且另一个候选正在播放时纠正。
    const detected = detectBilibiliVideo();
    const candidateIsActive = detected?.video
      && !detected.video.paused
      && !detected.video.ended
      && detected.video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA;
    const shouldRebind = detected?.video
      && detected.video !== videoEl
      && (!isVideoElementUsable(videoEl) || (stagnantVideoTicks >= 3 && candidateIsActive));
    if (shouldRebind && detected) {
      bindVideoElement(detected.video, false);
      isNavigating = false;
      console.log("[OmniStudy] 当前播放器已失效或停滞，已自动重新绑定");
    }

    const t = videoEl.currentTime;
    const detectedPart = getCurrentPart();
    if (detectedPart !== lastKnownPart) {
      lastKnownPart = detectedPart;
      resetEpisodeState(t);
      safeSendMessage({
        type: "context:ready",
        payload: { url: location.href, part: detectedPart, totalParts: getTotalParts() },
      });
      console.log(`[OmniStudy] 检测到播放器列表切至第 ${detectedPart} 集`);
    }
    // 分P切换时 B站可能既不更换 video 元素，也短暂不更新 URL，但播放
    // 时间会从上一集末尾跳回开头。用时间回退作为第三层兜底。
    if (t + 5 < lastCheckTime || t + 5 < lastPeriodicCheckTime) {
      resetEpisodeState(t);
      console.log(`[OmniStudy] 检测到播放时间回退，自动拦截计时从 ${formatTime(t)} 重新开始`);
    }
    safeSendMessage({ type: "time:update", payload: t });

    const subs = getCurrentSubtitles();
    if (subs?.current) {
      safeSendMessage({ type: "subtitle:update", payload: subs.current });
      if (subs.current !== lastCapturedSubtitle) {
        recordSubtitle(t, subs.current);
        lastCapturedSubtitle = subs.current;
      }
    }

    if (t - lastSubtitleFlushTime >= SUBTITLE_FLUSH_INTERVAL_SEC) {
      lastSubtitleFlushTime = t;
      flushSubtitles().catch(() => {});
    }
    if (t - lastNoteSyncTime >= NOTE_SYNC_INTERVAL_SEC) {
      lastNoteSyncTime = t;
      autoSyncNote(false).catch(() => {});
    }

    // ============ 定期语义综合判断 ============
    if (!isNavigating && canCheckQuestion(t) && t - lastPeriodicCheckTime >= AUTO_INTERCEPT_INTERVAL_SEC) {
      lastPeriodicCheckTime = t;
      triggerPeriodicIntercept(t).catch(() => {});
    }
    // =========================================
  }, UPDATE_INTERVAL_MS);
}

function onPause() {
  safeSendMessage({ type: "video:paused", payload: { t: videoEl?.currentTime } });
}

function onEnded() {
  flushSubtitles()
    .then(() => autoSyncNote(true))
    .finally(() => safeSendMessage({ type: "video:ended", payload: { t: videoEl?.currentTime } }));
}

async function flushSubtitles() {
  const sessionId = await getSessionIdSafe();
  if (!sessionId || SUBTITLE_PENDING.length === 0) return;
  const pending = SUBTITLE_PENDING.splice(0);
  const grouped = new Map<number, SubtitleEntry[]>();
  for (const item of pending) {
    grouped.set(item.part, [...(grouped.get(item.part) || []), item]);
  }
  const failed: SubtitleEntry[] = [];
  for (const [part, items] of grouped) {
    const chunks = items.map((item) => ({
      tStart: item.t, tEnd: item.t + 5, text: item.text,
    }));
    const result = await chrome.runtime.sendMessage({
      type: "notes:capture-subtitles", payload: { sessionId, part, chunks },
    });
    if (!result?.success) failed.push(...items);
  }
  if (failed.length > 0) SUBTITLE_PENDING.unshift(...failed);
}

async function autoSyncNote(finalize: boolean) {
  if (noteSyncInFlight && !finalize) return;
  const sessionId = await getSessionIdSafe();
  if (!sessionId) return;
  noteSyncInFlight = true;
  try {
    await chrome.runtime.sendMessage({ type: "notes:auto-sync", payload: { sessionId, finalize } });
    safeSendMessage({ type: "note:sync", payload: { sessionId, finalize } });
  } finally {
    noteSyncInFlight = false;
  }
}

/**
 * 定期语义综合判断触发拦截
 * 收集近 2 分钟字幕上下文 + 当前截图，通过 service-worker 转发给后端 LLM
 */
async function triggerPeriodicIntercept(t: number) {
  const sessionId = await getSessionIdSafe();
  if (!sessionId) {
    console.log("[OmniStudy] 定期检查跳过：无 sessionId（可能视频开始后才打开侧边栏）");
    return;
  }

  const authToken = await getAuthTokenSafe();
  if (!authToken) {
    console.log("[OmniStudy] 定期检查跳过：未登录");
    return;
  }

  // 收集近 2 分钟的字幕上下文（至少要有字幕才开始判断）
  if (SUBTITLE_HISTORY.length < 2) {
    console.log("[OmniStudy] 定期检查跳过：字幕历史不足（字幕可能未开启或刚开启）");
    return;
  }

  const recentSubs = SUBTITLE_HISTORY.filter(item => t - item.t <= 120);
  if (recentSubs.length === 0) {
    console.log("[OmniStudy] 定期检查跳过：120秒内无字幕");
    return;
  }

  const beforeText = recentSubs.slice(0, Math.ceil(recentSubs.length / 2)).map(i => i.text).join(" ");
  const afterText = recentSubs.slice(Math.ceil(recentSubs.length / 2)).map(i => i.text).join(" ");
  const currentText = recentSubs[recentSubs.length - 1]?.text ?? "";
  const normalizedContext = normalizeSemanticContext(recentSubs.map(item => item.text).join(" "));
  if (normalizedContext.length < MIN_INTERCEPT_CONTEXT_CHARS) {
    console.log(`[OmniStudy] 定期检查跳过：有效字幕仅 ${normalizedContext.length} 字`);
    return;
  }
  const similarity = semanticSimilarity(lastInterceptContextText, normalizedContext);
  if (lastInterceptContextText && similarity >= MAX_INTERCEPT_CONTEXT_SIMILARITY) {
    console.log(`[OmniStudy] 定期检查跳过：与上次语义窗口重复 ${(similarity * 100).toFixed(0)}%`);
    return;
  }
  // 只在真正准备请求模型时推进语义指纹；短字幕可以继续在下一窗口积累。
  lastInterceptContextText = normalizedContext;

  const screenshot = captureFrame(videoEl!);
  // 格式化为 M:SS，LLM 会原样返回
  const timeFormatted = formatTime(t);

  console.log(`[OmniStudy] 定期语义检查触发，字幕: "${currentText.slice(0, 15)}..."，历史条数: ${recentSubs.length}`);

  // 在请求发出前固定本集编号，避免 SPA 在模型响应期间又发生切集。
  const requestedPart = getCurrentPart();

  try {
    const result = await chrome.runtime.sendMessage({
      type: "auto:intercept",
      payload: {
        sessionId,
        time: timeFormatted,
        screenshot,
        before: beforeText,
        current: currentText,
        after: afterText,
        difficulty: 1,
        part: requestedPart,
      },
    });

    if (result?.success) {
      const response = result.data?.response;
      if (response?.shouldIntercept && response.confidence >= MIN_INTERCEPT_CONFIDENCE && response.options?.length === 2) {
        console.log(`[OmniStudy] LLM 拦截成功，置信度: ${response.confidence}`);
        const question = {
          ...response,
          id: result.data?.questionId,
          part: response.part ?? requestedPart,
        } as InterceptResult;
        if (!question.id) return;
        lastQuestionTime = t;
        questionsInWindow += 1;
        showChoiceQuestion(question);
        // 自动拦截问题需要立即携带后端生成的 ID，否则删除按钮只能在
        // 重新打开侧边栏、从后端重新加载问题后才会生效。
        safeSendMessage({
          type: "question:added",
          payload: question,
        });
      } else {
        console.log(`[OmniStudy] LLM 无需拦截: ${response?.reason ?? "N/A"}`);
      }
    } else {
      console.log(`[OmniStudy] 拦截请求失败: ${result?.error ?? "未知错误"}`);
    }
  } catch (e) {
    if (isContextInvalidatedError(e)) {
      invalidateExtensionContext();
      return;
    }
    console.log(`[OmniStudy] 拦截请求异常: ${(e as Error).message}`);
  }
}

function canCheckQuestion(t: number): boolean {
  if (!videoEl || questionModal) return false;
  if (t < QUESTION_WARMUP_SEC) return false;
  if (Number.isFinite(videoEl.duration) && videoEl.duration - t <= QUESTION_END_GUARD_SEC) return false;
  if (t - lastQuestionTime < QUESTION_COOLDOWN_SEC) return false;
  if (t - questionWindowStartedAt >= 3600) {
    questionWindowStartedAt = t;
    questionsInWindow = 0;
  }
  return questionsInWindow < MAX_QUESTIONS_PER_HOUR;
}

function normalizeSemanticContext(value: string): string {
  return value.toLowerCase().replace(/[\s\p{P}\p{S}]+/gu, "").slice(-2_000);
}

function semanticSimilarity(previous: string, current: string): number {
  if (!previous || !current) return 0;
  const previousBigrams = bigrams(previous);
  const currentBigrams = bigrams(current);
  if (previousBigrams.size === 0 || currentBigrams.size === 0) return previous === current ? 1 : 0;
  let intersection = 0;
  for (const gram of currentBigrams) if (previousBigrams.has(gram)) intersection += 1;
  const union = previousBigrams.size + currentBigrams.size - intersection;
  return union === 0 ? 0 : intersection / union;
}

function bigrams(value: string): Set<string> {
  const result = new Set<string>();
  for (let index = 0; index < value.length - 1; index += 1) {
    result.add(value.slice(index, index + 2));
  }
  return result;
}

function showChoiceQuestion(question: InterceptResult) {
  if (!videoEl || questionModal) return;
  wasPlayingBeforeQuestion = !videoEl.paused;
  videoEl.pause();

  const shuffled = [...question.options].sort(() => Math.random() - 0.5);
  const overlay = document.createElement("dialog");
  overlay.id = "omnistudy-choice-overlay";
  Object.assign(overlay.style, {
    position: "fixed", inset: "0", zIndex: "2147483647", background: "rgba(15,23,42,.62)",
    width: "100vw", height: "100vh", maxWidth: "none", maxHeight: "none", margin: "0",
    border: "0", boxSizing: "border-box", display: "flex", alignItems: "center", justifyContent: "center", padding: "24px",
    fontFamily: "-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif",
  });
  // 不允许 Esc 绕过答题；dialog 的 Top Layer 能覆盖浏览器原生全屏元素。
  overlay.addEventListener("cancel", (event) => event.preventDefault());
  const card = document.createElement("div");
  Object.assign(card.style, {
    width: "min(560px, 92vw)", background: "white", borderRadius: "18px", padding: "24px",
    boxShadow: "0 24px 70px rgba(15,23,42,.3)", color: "#1e293b",
  });
  const badge = document.createElement("div");
  badge.textContent = `OmniStudy · ${question.coreConcept || "知识检查"}`;
  Object.assign(badge.style, { color: "#4f46e5", fontSize: "13px", fontWeight: "700", marginBottom: "12px" });
  const title = document.createElement("div");
  title.textContent = question.question;
  Object.assign(title.style, { fontSize: "20px", fontWeight: "700", lineHeight: "1.5", marginBottom: "18px" });
  const optionBox = document.createElement("div");
  Object.assign(optionBox.style, { display: "grid", gap: "10px" });
  const feedback = document.createElement("div");
  Object.assign(feedback.style, { display: "none", marginTop: "16px", padding: "14px", borderRadius: "12px", fontSize: "14px", lineHeight: "1.6" });

  for (const option of shuffled) {
    const button = document.createElement("button");
    button.textContent = option.text;
    button.dataset.optionId = option.id;
    Object.assign(button.style, {
      width: "100%", padding: "14px 16px", border: "1px solid #cbd5e1", borderRadius: "12px",
      background: "#f8fafc", color: "#1e293b", textAlign: "left", cursor: "pointer", fontSize: "15px",
    });
    button.onclick = () => submitChoice(question, option.id, optionBox, feedback);
    optionBox.appendChild(button);
  }
  card.append(badge, title, optionBox, feedback);
  overlay.appendChild(card);
  document.documentElement.appendChild(overlay);
  try {
    overlay.showModal();
  } catch (error) {
    // 极旧浏览器的兜底；Chrome MV3 正常会走 showModal Top Layer。
    console.warn("[OmniStudy] 无法启用 dialog Top Layer，使用普通覆盖层", error);
    overlay.setAttribute("open", "");
  }
  questionModal = overlay;
}

async function submitChoice(question: InterceptResult, optionId: string, optionBox: HTMLDivElement, feedback: HTMLDivElement) {
  const buttons = Array.from(optionBox.querySelectorAll("button"));
  buttons.forEach((button) => { button.disabled = true; button.style.cursor = "default"; });
  try {
    const result = await chrome.runtime.sendMessage({ type: "choice:answer", payload: { questionId: question.id, optionId } });
    if (!result?.success) throw new Error(result?.error || "提交答案失败");
    const answer = result.data;
    buttons.forEach((button) => {
      if (button.dataset.optionId === answer.correctOptionId) {
        button.style.background = "#dcfce7"; button.style.borderColor = "#22c55e";
      } else if (button.dataset.optionId === optionId) {
        button.style.background = "#fee2e2"; button.style.borderColor = "#ef4444";
      }
    });
    if (answer.correct) {
      feedback.textContent = "回答正确，继续学习";
      Object.assign(feedback.style, { display: "block", background: "#dcfce7", color: "#166534" });
      window.setTimeout(() => closeQuestionModal(true), 800);
    } else {
      feedback.replaceChildren();
      const explanation = document.createElement("div");
      explanation.textContent = `正确解析：${answer.explanation}`;
      const continueButton = document.createElement("button");
      continueButton.textContent = "我知道了，继续播放";
      Object.assign(continueButton.style, {
        display: "block", width: "100%", marginTop: "12px", padding: "11px", border: "0",
        borderRadius: "10px", background: "#4f46e5", color: "white", cursor: "pointer", fontWeight: "600",
      });
      continueButton.onclick = () => closeQuestionModal(true);
      feedback.append(explanation, continueButton);
      Object.assign(feedback.style, { display: "block", background: "#fff7ed", color: "#9a3412" });
    }
  } catch (e) {
    feedback.textContent = `提交失败：${(e as Error).message}`;
    Object.assign(feedback.style, { display: "block", background: "#fee2e2", color: "#991b1b" });
    buttons.forEach((button) => { button.disabled = false; button.style.cursor = "pointer"; });
  }
}

function closeQuestionModal(resume: boolean) {
  if (questionModal instanceof HTMLDialogElement && questionModal.open) questionModal.close();
  questionModal?.remove();
  questionModal = null;
  if (resume && wasPlayingBeforeQuestion) videoEl?.play().catch(() => {});
  wasPlayingBeforeQuestion = false;
}

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

async function getSessionIdSafe(): Promise<string> {
  const { sessionId } = await chrome.storage.local.get("sessionId");
  return sessionId ?? "";
}

async function getAuthTokenSafe(): Promise<string> {
  const { auth } = await chrome.storage.local.get("auth");
  return auth?.token ?? "";
}

async function onTimeUpdate() {
  if (!videoEl) return;
  const t = videoEl.currentTime;

  if (t - lastCheckTime >= 5) {
    lastCheckTime = t;
    pushSubtitleHistory(t);
  }
}

function pushSubtitleHistory(t: number) {
  const subs = getCurrentSubtitles();
  if (subs && subs.current) {
    if (subs.current !== lastCapturedSubtitle) {
      recordSubtitle(t, subs.current);
      lastCapturedSubtitle = subs.current;
    }
  }
}

function recordSubtitle(t: number, text: string) {
  const item = { t, text, part: getCurrentPart() };
  SUBTITLE_HISTORY.push(item);
  SUBTITLE_PENDING.push(item);
  while (SUBTITLE_HISTORY.length > 240) SUBTITLE_HISTORY.shift();
}

export async function handleIntercept(difficulty: number): Promise<InterceptInput> {
  if (!videoEl) throw new Error("video element not found");

  const t = videoEl.currentTime;
  const subs = getCurrentSubtitles();
  const screenshot = captureFrame(videoEl);
  const sessionId = await getSessionId();

  return {
    sessionId,
    time: formatTime(t),
    screenshot,
    before: subs?.before ?? "",
    current: subs?.current ?? "",
    after: subs?.after ?? "",
    difficulty,
    part: getCurrentPart(),
  };
}

function getCurrentPart(): number {
  const urlPart = Number(new URL(location.href).searchParams.get("p"));
  if (Number.isInteger(urlPart) && urlPart > 0) return urlPart;

  // OmniStudy 的 part 表示同一个 BV 视频内的分 P。合集/课程和番剧列表中的
  // 每一项通常是不同视频，必须各自从第 1 集开始，不能把列表位置当成分 P。
  return 1;
}

/** 只统计当前 BV 下的多 P 链接，避免把合集、番剧或推荐列表误算为分 P。 */
function getTotalParts(): number {
  const current = getCurrentPart();
  const currentVideoId = location.pathname.match(/\/video\/(BV\w+)/i)?.[1]?.toLowerCase();
  if (!currentVideoId) return current;

  const hrefParts = Array.from(document.querySelectorAll<HTMLAnchorElement>('a[href*="p="]'))
    .map(link => {
      try {
        const url = new URL(link.href, location.href);
        const linkedVideoId = url.pathname.match(/\/video\/(BV\w+)/i)?.[1]?.toLowerCase();
        return linkedVideoId === currentVideoId ? Number(url.searchParams.get("p")) : 0;
      }
      catch { return 0; }
    })
    .filter(part => Number.isInteger(part) && part > 0);
  return Math.max(current, ...hrefParts, 1);
}

async function getSessionId(): Promise<string> {
  const { sessionId } = await chrome.storage.local.get("sessionId");
  if (!sessionId) throw new Error("没有进行中的 session");
  return sessionId;
}

(globalThis as any).__triggerIntercept = handleIntercept;

init();
