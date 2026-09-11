import { chromium, expect, test, type Page, type Video } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import path from "node:path";

test("record the local-first extension walkthrough", async () => {
  test.skip(!process.env.OMNISTUDY_RECORD_DEMO, "Run through scripts/record-demo");

  const extensionPath = path.resolve(import.meta.dirname, "../dist");
  const artifactDir = path.resolve(import.meta.dirname, "../../../artifacts/demo");
  const videoDir = path.join(artifactDir, "raw");
  await mkdir(videoDir, { recursive: true });

  const context = await chromium.launchPersistentContext("", {
    channel: "chromium",
    headless: true,
    slowMo: 350,
    viewport: { width: 760, height: 1200 },
    recordVideo: { dir: videoDir, size: { width: 760, height: 1200 } },
    args: [`--disable-extensions-except=${extensionPath}`, `--load-extension=${extensionPath}`],
  });

  await context.addInitScript(() => {
    if (!globalThis.chrome?.runtime?.sendMessage) return;
    const originalSendMessage = chrome.runtime.sendMessage.bind(chrome.runtime);
    const now = "2026-09-11T09:30:00+08:00";
    const sessionId = "6d743a16-4509-4bfd-a4a8-132788fa2051";
    const session = {
      id: sessionId,
      courseId: "BV1DemoCourse",
      courseTitle: "Agent 架构与工程实践",
      platform: "bilibili",
      videoUrl: "https://www.bilibili.com/video/BV1DemoCourse?p=1",
      startedAt: now,
      endedAt: null,
      questionCount: 2,
      note: null,
    };
    const knowledge = (id: string, name: string, masteryScore: number, due = false) => ({
      id, name, normalizedName: name.toLowerCase(), mastery: masteryScore >= 0.7 ? "掌握的" : "模糊的",
      firstSeen: now, lastReviewed: now, masteryScore,
      nextReviewAt: due ? now : "2026-09-18T09:30:00+08:00",
      reviewIntervalDays: due ? 1 : 7, correctStreak: due ? 0 : 2,
      sources: ["Agent 架构与工程实践"], noteCount: 1,
    });
    const note = {
      id: "80ac4829-5cc8-42bb-ad82-3d06ac2392b8", sessionId,
      title: "Agent 的 Planning、Memory 与 Tool Use",
      markdown: "## 核心结构\n- Planning 将目标拆成可执行步骤\n- Memory 保存长期偏好与学习轨迹\n- Tool Use 必须具备参数边界和副作用声明",
      courseName: "Agent 架构与工程实践", chapterName: "核心模块",
      sourceTitle: "Agent 架构与工程实践", sourceUrl: session.videoUrl, sourceTimestamp: 302,
      tags: ["Agent", "Planning", "Memory"], linkedNoteIds: [], contentStatus: "organized",
      masteryStatus: "learning", inbox: false, nextReviewAt: "2026-09-12T09:30:00+08:00",
      studyMaterials: { flashcards: [], questions: [] }, createdAt: now, updatedAt: now,
    };
    const responses: Record<string, unknown> = {
      "auth:me": { success: true, data: { id: "demo-user", username: "开源体验用户", role: "USER" } },
      "ai-settings:get": { success: true, data: {
        configured: true, source: "USER", provider: "dashscope",
        baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
        maskedApiKey: "sk-****demo", fastVisionModel: "qwen3-vl-flash", strongTextModel: "qwen-plus",
        connectionStatus: "VERIFIED", lastVerifiedAt: now,
      } },
      "session:list": { success: true, data: [session] },
      "question:list-session": { success: true, data: [{
        id: "d3fe14fd-ea38-4451-99c6-a76e8f636517", shouldIntercept: true,
        time: "5:02", part: 1, coreConcept: "Planning", evidence: "Planning 模块负责分解目标",
        question: "在 Agent 架构中，Planning 模块最核心的职责是什么？",
        questionType: "definition", difficulty: 1, confidence: 0.98,
        options: [{ id: "A", text: "分解目标并安排执行步骤" }, { id: "B", text: "永久保存所有对话原文" }],
      }] },
      "notes:list": { success: true, data: [note] },
      "notes:session-status": { success: true, data: {
        id: note.id, status: "done", contentStatus: "organized", generatedAt: now,
      } },
      "insights:usage": { success: true, data: {
        periodStart: "2026-09-11", requests: 18, inputTokens: 12460, outputTokens: 3280,
        cachedTokens: 6150, totalTokens: 15740, estimatedCostMicros: 23800,
        succeeded: 18, failed: 0, features: [
          { feature: "INTERCEPT", requests: 6, inputTokens: 4800, outputTokens: 920, cachedTokens: 2200, totalTokens: 5720, estimatedCostMicros: 8400, succeeded: 6, failed: 0 },
          { feature: "AGENT_V2", requests: 5, inputTokens: 3910, outputTokens: 1260, cachedTokens: 1850, totalTokens: 5170, estimatedCostMicros: 7900, succeeded: 5, failed: 0 },
          { feature: "NOTE_FINALIZE", requests: 1, inputTokens: 2100, outputTokens: 780, cachedTokens: 1300, totalTokens: 2880, estimatedCostMicros: 5200, succeeded: 1, failed: 0 },
        ],
      } },
      "insights:weak": { success: true, data: [
        knowledge("weak-1", "上下文压缩", 0.46), knowledge("weak-2", "工具副作用边界", 0.58),
      ] },
      "insights:due": { success: true, data: [knowledge("due-1", "Agent Memory", 0.64, true)] },
      "insights:traces": { success: true, data: [{
        id: "trace-1", state: "COMPLETED", skill: "current-course-tutor", model: "qwen-plus",
        promptVersion: "v2", framework: "AgentScope Java 2.0", inputTokens: 860, outputTokens: 244,
        cachedTokens: 420, steps: 3, latencyMs: 1280, success: true, createdAt: now,
      }, {
        id: "trace-2", state: "COMPLETED", tool: "search_notes", model: "qwen-plus",
        promptVersion: "v2", framework: "AgentScope Java 2.0", inputTokens: 510, outputTokens: 136,
        cachedTokens: 260, steps: 2, latencyMs: 740, success: true, createdAt: now,
      }] },
    };
    Object.defineProperty(chrome.runtime, "sendMessage", {
      configurable: true,
      value: (...args: unknown[]) => {
        const message = args[0] as { type?: string } | undefined;
        const response = message?.type ? responses[message.type] : undefined;
        return response === undefined ? originalSendMessage(...args as Parameters<typeof originalSendMessage>) : Promise.resolve(response);
      },
    });
  });

  let video: Video | null = null;
  let page: Page | null = null;
  try {
    let workers = context.serviceWorkers();
    if (!workers.length) workers = [await context.waitForEvent("serviceworker")];
    const extensionId = new URL(workers[0].url()).host;
    page = await context.newPage();
    video = page.video();
    await page.goto(`chrome-extension://${extensionId}/sidepanel.html`);

    await expect(page.getByRole("heading", { name: "OmniStudy - 登录" })).toBeVisible();
    await page.waitForTimeout(1_000);
    await page.getByRole("button", { name: "没有账号？立即注册" }).click();
    await expect(page.getByRole("heading", { name: "创建账号" })).toBeVisible();
    await page.waitForTimeout(1_000);
    await page.getByRole("button", { name: "已有账号？返回登录" }).click();

    await page.evaluate(async () => {
      await chrome.storage.local.set({
        auth: { token: "demo-token", userId: "demo-user", username: "开源体验用户", role: "USER" },
        currentSession: {
          id: "6d743a16-4509-4bfd-a4a8-132788fa2051",
          courseId: "BV1DemoCourse",
          courseTitle: "Agent 架构与工程实践",
          platform: "bilibili",
          videoUrl: "https://www.bilibili.com/video/BV1DemoCourse?p=1",
          startedAt: "2026-09-11T09:30:00+08:00",
          endedAt: null,
          questionCount: 2,
          note: null,
        },
      });
    });
    await page.reload();
    await expect(page.getByRole("button", { name: "学习" })).toBeVisible();
    await expect(page.getByText("Agent 架构与工程实践", { exact: true })).toBeVisible();
    await page.waitForTimeout(1_800);
    await page.getByRole("button", { name: "Agent" }).click();
    await expect(page.getByText("学习 Agent", { exact: true })).toBeVisible();
    await page.waitForTimeout(1_600);
    await page.getByRole("button", { name: "笔记", exact: true }).click();
    await expect(page.getByText("Agent 的 Planning、Memory 与 Tool Use", { exact: true })).toBeVisible();
    await page.waitForTimeout(1_800);
    await page.getByRole("button", { name: "概览", exact: true }).click();
    await expect(page.getByText("今日学习概览", { exact: true })).toBeVisible();
    await expect(page.getByText("15,740", { exact: true })).toBeVisible();
    await page.waitForTimeout(3_000);
    await page.getByRole("button", { name: "模型", exact: true }).click();
    await expect(page.getByText("模型配置", { exact: true })).toBeVisible();
    await expect(page.getByText(/已验证.*正在使用你的 Key/)).toBeVisible();
    await page.waitForTimeout(1_800);
  } finally {
    if (page && !page.isClosed()) await page.close();
    if (video) await video.saveAs(path.join(artifactDir, "omnistudy-ui-demo.webm"));
    await context.close();
  }

  if (!video) throw new Error("Playwright did not create a video stream");
});
