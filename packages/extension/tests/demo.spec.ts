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
        auth: { token: "demo-token", userId: "demo-user", username: "local-user" },
      });
    });
    await page.reload();
    await expect(page.getByRole("button", { name: "学习" })).toBeVisible();
    await page.waitForTimeout(1_000);
    await page.getByRole("button", { name: "Agent" }).click();
    await expect(page.getByText("学习 Agent", { exact: true })).toBeVisible();
    await page.waitForTimeout(1_000);
    await page.getByRole("button", { name: "笔记", exact: true }).click();
    await page.waitForTimeout(1_000);
    await page.getByRole("button", { name: "模型", exact: true }).click();
    await expect(page.getByText("模型配置", { exact: true })).toBeVisible();
    await page.waitForTimeout(1_500);
  } finally {
    if (page && !page.isClosed()) await page.close();
    if (video) await video.saveAs(path.join(artifactDir, "omnistudy-ui-demo.webm"));
    await context.close();
  }

  if (!video) throw new Error("Playwright did not create a video stream");
});
