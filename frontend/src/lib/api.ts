export class ApiError extends Error {
  constructor(public status: number, message: string, public fieldErrors: Record<string, string> = {}) { super(message); }
}
export const apiBase = (import.meta.env.VITE_API_URL as string | undefined) ?? "";
let csrf: { token: string; headerName: string } | undefined;
let csrfPending: Promise<void> | undefined;
export function resetCsrf() { csrf = undefined; csrfPending = undefined; }
async function loadCsrf() {
  if (csrf) return;
  if (!csrfPending) csrfPending = (async () => {
    const response = await fetch(`${apiBase}/api/v1/auth/csrf`, { credentials: "include" });
    if (!response.ok) throw new ApiError(response.status, "Could not establish a secure session. Refresh and retry.");
    csrf = await response.json() as { token: string; headerName: string };
  })().finally(() => { csrfPending = undefined; });
  await csrfPending;
}
export async function api<T>(path: string, method = "GET", body?: unknown): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (method !== "GET") { await loadCsrf(); if (csrf) headers[csrf.headerName] = csrf.token; }
  let response: Response;
  try {
    response = await fetch(`${apiBase}/api/v1${path}`, { method, credentials: "include", headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch { throw new ApiError(0, "Cannot reach the server. Check your connection and try again."); }
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as { message?: string; fieldErrors?: Record<string, string> };
    if (response.status === 401) window.dispatchEvent(new Event("session-expired"));
    if (response.status === 403) resetCsrf();
    throw new ApiError(response.status, error.message ?? "The request failed. Please retry.", error.fieldErrors);
  }
  if (path === "/auth/login" || path === "/auth/logout") resetCsrf();
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
export const missing = (e: unknown) => e instanceof ApiError && e.status === 404;
