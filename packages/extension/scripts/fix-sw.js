import { readFileSync, writeFileSync } from "fs";
import { join, dirname } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const distDir = join(__dirname, "..", "dist");

// Service worker with proper error handling
const swContent = `console.log("OmniStudy Service Worker loaded");

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
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  console.log("[SW] Message received:", message.type);

  let didRespond = false;
  const respondOnce = (data) => {
    if (didRespond) return;
    didRespond = true;
    sendResponse(data);
  };

  // Handle auth:login from side panel → call backend → store token
  if (message.type === "auth:login") {
    (async () => {
      try {
        const res = await fetch("http://localhost:8080/api/auth/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(message.payload ?? {}),
        });
        const json = await res.json();
        if (!res.ok || !json.success) {
          respondOnce({ success: false, error: json.error ?? \`HTTP \${res.status}\` });
          return;
        }
        await chrome.storage.local.set({
          auth: { token: json.data.token, userId: json.data.userId },
        });
        respondOnce({ success: true, data: json.data });
      } catch (e) {
        respondOnce({ success: false, error: e.message });
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

  // Forward to side panel if needed (from content script)
  if (sender.tab && sender.tab.id && message.type) {
    chrome.runtime.sendMessage({
      ...message,
      from: sender.tab.url
    }).catch(() => {
      // Side panel might not be open, ignore
    });
  }

  respondOnce({ received: true });
  return false;
});
`;

// Vite has already bundled the real service worker. Do not replace it with the
// legacy inline implementation above: that implementation does not contain the
// auto:intercept route and silently drops automatic interception requests.
const swPath = join(distDir, "src", "serviceworker.js");
readFileSync(swPath, "utf-8");
console.log("Kept Vite-generated serviceworker.js");

// Update manifest.json
const manifestPath = join(distDir, "manifest.json");
const manifest = JSON.parse(readFileSync(manifestPath, "utf-8"));

if (manifest.background?.service_worker) {
  manifest.background.service_worker = "src/serviceworker.js";
  delete manifest.background.type;
}

// Add action for side panel click
if (!manifest.action) {
  manifest.action = { default_title: "OmniStudy" };
}

writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));
console.log("Updated manifest.json");
