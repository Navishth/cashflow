import { afterEach, describe, expect, it, vi } from "vitest";
import { api, resetCsrf } from "./api";
afterEach(() => { resetCsrf(); vi.unstubAllGlobals(); });
describe("Session API client", () => {
  it("bootstraps CSRF and includes credentials on mutations", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ token: "test-token", headerName: "X-CSRF-TOKEN" }))).mockResolvedValueOnce(new Response(JSON.stringify({ id: "1" })));
    vi.stubGlobal("fetch", fetchMock);
    await api("/cash-flows", "POST", { amount: 10 });
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/v1/cash-flows", expect.objectContaining({ credentials: "include", headers: expect.objectContaining({ "X-CSRF-TOKEN": "test-token" }) }));
  });
  it("surfaces server errors and never supplies fake financial data", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "Service unavailable." }), { status: 503 })));
    await expect(api("/forecasts/latest")).rejects.toThrow("Service unavailable.");
  });
  it("does not automatically retry unsafe requests", async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ token: "test-token", headerName: "X-CSRF-TOKEN" }))).mockResolvedValueOnce(new Response(JSON.stringify({ message: "Refresh." }), { status: 403 }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(api("/scenarios", "POST", {})).rejects.toThrow("Refresh.");
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
