import { chromium, expect, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

test("keeps authenticated backend access in the service worker", () => {
  const sidePanelSource = fs.readFileSync(path.resolve(import.meta.dirname, "../src/sidepanel/index.tsx"), "utf8");
  const workerSource = fs.readFileSync(path.resolve(import.meta.dirname, "../src/serviceworker.ts"), "utf8");

  expect(sidePanelSource).not.toContain("fetch(");
  expect(sidePanelSource).toContain('type: "session:start"');
  expect(sidePanelSource).toContain('type: "manual:intercept"');
  expect(workerSource).toContain('message.type === "session:start"');
  expect(workerSource).toContain('message.type === "manual:intercept"');
});

test("removes every listener when rebinding the video element", () => {
  const source = fs.readFileSync(path.resolve(import.meta.dirname, "../src/content-script/index.ts"), "utf8");
  const start = source.indexOf("function bindVideoElement");
  const end = source.indexOf("function ", start + 10);
  const bindFunction = source.slice(start, end);

  expect(bindFunction).toContain('removeEventListener("timeupdate", onTimeUpdate)');
  expect(bindFunction).toContain('removeEventListener("pause", onPause)');
  expect(bindFunction).toContain('removeEventListener("ended", onEnded)');
});

test("requires authentication before rendering the workspace", async () => {
  const extensionPath = path.resolve(import.meta.dirname, "../dist");
  const context = await chromium.launchPersistentContext("", {
    // Playwright's Chromium channel uses the extension-capable new headless mode.
    channel: "chromium",
    headless: true,
    args: [`--disable-extensions-except=${extensionPath}`, `--load-extension=${extensionPath}`],
  });
  try {
    let workers = context.serviceWorkers();
    if (!workers.length) workers = [await context.waitForEvent("serviceworker")];
    const extensionId = new URL(workers[0].url()).host;
    const page = await context.newPage();
    await page.goto(`chrome-extension://${extensionId}/sidepanel.html`);

    await expect(page.getByRole("heading", { name: "OmniStudy - 登录" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "登录", exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "学习" })).toHaveCount(0);
    await page.getByRole("button", { name: "没有账号？立即注册" }).click();
    await expect(page.getByRole("heading", { name: "创建账号" })).toBeVisible();
    await expect(page.getByPlaceholder("邮箱（可选）")).toBeVisible();
    await page.getByRole("button", { name: "已有账号？返回登录" }).click();
    await expect(page.getByRole("heading", { name: "登录", exact: true })).toBeVisible();

    await page.evaluate(async () => {
      await chrome.storage.local.set({
        auth: { token: "e2e-token", userId: "e2e-user", username: "e2e" },
      });
    });
    await page.reload();
    await expect(page.getByRole("button", { name: "学习" })).toBeVisible();
    await expect(page.getByRole("button", { name: "笔记" })).toBeVisible();
    await page.getByRole("button", { name: "Agent" }).click();
    await expect(page.getByText("学习 Agent", { exact: true })).toBeVisible();
    await expect(page.getByText("IDEA 项目设置（可选）")).toBeVisible();
  } finally {
    await context.close();
  }
});
