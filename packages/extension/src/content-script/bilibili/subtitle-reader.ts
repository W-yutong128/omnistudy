/**
 * 解析 B 站字幕 DOM
 * B 站字幕结构：.bili-subtitle-x-subtitle-panel-text
 */

interface SubtitleWindow {
  before: string;
  current: string;
  after: string;
}

export function getCurrentSubtitles(): SubtitleWindow | null {
  // 当前字幕：.bili-subtitle-x-subtitle-panel-text 内的内容
  const current = document.querySelector(".bili-subtitle-x-subtitle-panel-text")?.textContent?.trim() ?? "";

  if (!current) return null;

  // 前后字幕：B 站字幕组件通常只显示一行，before/after 留空字符串
  // 后续如需更精细，可结合 subtitle-history 数组（由 Content Script 维护）
  return {
    before: "",
    current,
    after: "",
  };
}