const DEFAULT_BACKEND_ORIGIN = "http://localhost:8080";

const configuredOrigin = (import.meta.env.VITE_API_BASE_URL || DEFAULT_BACKEND_ORIGIN).trim();

export const BACKEND_ORIGIN = configuredOrigin.replace(/\/+$/, "");
export const API_BASE_URL = `${BACKEND_ORIGIN}/api`;

export function backendUrl(path: string): string {
  return `${BACKEND_ORIGIN}${path.startsWith("/") ? path : `/${path}`}`;
}
