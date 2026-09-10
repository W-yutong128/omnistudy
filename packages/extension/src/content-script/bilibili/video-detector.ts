/**
 * 检测 B 站视频元素
 */

export interface VideoContext {
  video: HTMLVideoElement;
  subtitles: HTMLElement | null;
  progress: HTMLElement | null;
}

export function detectBilibiliVideo(): VideoContext | null {
  const videos = Array.from(document.querySelectorAll<HTMLVideoElement>("video"));
  const video = videos
    .filter(candidate => candidate.isConnected)
    .sort((a, b) => scoreVideo(b) - scoreVideo(a))[0];
  if (!video) return null;

  return {
    video,
    subtitles: document.querySelector<HTMLElement>(".bpx-player-subtitle-wrap"),
    progress: document.querySelector<HTMLElement>(".bpx-player-progress"),
  };
}

/**
 * B站 SPA 切集时可能同时保留新旧多个 video。优先选择播放器区域内、
 * 可见、已有媒体数据且正在播放的元素，避免绑定到 currentTime=0 的旧节点。
 */
function scoreVideo(video: HTMLVideoElement): number {
  const rect = video.getBoundingClientRect();
  const style = window.getComputedStyle(video);
  const visible = rect.width > 1
    && rect.height > 1
    && style.display !== "none"
    && style.visibility !== "hidden"
    && Number(style.opacity || 1) > 0;
  const inMainPlayer = Boolean(video.closest(
    "#bilibili-player, .bpx-player-container, .bpx-player-video-area, .bilibili-player-video",
  ));
  const hasMedia = Boolean(video.currentSrc || video.src);
  const hasDuration = Number.isFinite(video.duration) && video.duration > 0;

  return (inMainPlayer ? 100 : 0)
    + (visible ? 50 : 0)
    + (!video.paused && !video.ended ? 30 : 0)
    + (video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA ? 20 : 0)
    + (hasDuration ? 10 : 0)
    + (hasMedia ? 5 : 0)
    + (video.currentTime > 0 ? 2 : 0);
}
