import { afterEach, expect, it, vi } from "vitest";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import App from "./App";
import { resetCsrf } from "./lib/api";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); resetCsrf(); });
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const user = { id: "owner", email: "owner@example.test", displayName: "Demo Owner", verified: true };
function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(<QueryClientProvider client={client}><MemoryRouter initialEntries={["/app"]}><App/></MemoryRouter></QueryClientProvider>);
  return client;
}
it("waits for session hydration before fetching private business records", async () => {
  let finish!: (response: Response) => void;
  const fetcher = vi.fn((url: string) => url.endsWith("/auth/me") ? new Promise<Response>(resolve => { finish = resolve; }) : Promise.resolve(reply({ googleEnabled: false })));
  vi.stubGlobal("fetch", fetcher);
  mount();
  expect(screen.getByRole("status", { name: "Loading" })).toBeInTheDocument();
  expect(fetcher.mock.calls.every(([url]) => url.endsWith("/auth/me"))).toBe(true);
  await act(async () => finish(reply({ message: "Sign in to continue." }, 401)));
  expect(await screen.findByRole("heading", { name: "Your next move starts here." })).toBeInTheDocument();
  expect(fetcher.mock.calls.some(([url]) => url.includes("/businesses/"))).toBe(false);
});
it("shows a recoverable API failure instead of presenting a sign-in or fake dashboard", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(reply({ message: "Service unavailable. Retry shortly." }, 503)));
  mount();
  expect(await screen.findByRole("alert")).toHaveTextContent("Service unavailable.");
  expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: /A clearer day/ })).not.toBeInTheDocument();
});
it("routes a verified user without a business into onboarding", async () => {
  vi.stubGlobal("fetch", vi.fn((url: string) => Promise.resolve(url.endsWith("/auth/me") ? reply(user) : reply({ message: "Not found." }, 404))));
  mount();
  expect(await screen.findByRole("heading", { name: "Let’s meet your business." })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Create my workspace" })).toBeInTheDocument();
});
it("shows an empty forecast honestly and removes private views on session expiry", async () => {
  vi.stubGlobal("fetch", vi.fn((url: string) => Promise.resolve(
    url.endsWith("/auth/me") ? reply(user) : url.endsWith("/businesses/current") ? reply({ id: "business", name: "Sample shop", currency: "INR", timezone: "Asia/Kolkata", today: "2026-10-21", currentBalance: 1000, openingBalance: 1000, openingDate: "2026-10-21", reserve: 500, demo: false }) : url.endsWith("/auth/options") ? reply({ googleEnabled: false }) : reply({ message: "No forecast yet." }, 404)
  )));
  const client = mount();
  expect(await screen.findByRole("heading", { name: "Your future cash starts here." })).toBeInTheDocument();
  expect(screen.getByText(/₹1,000.00/)).toBeInTheDocument();
  await act(async () => window.dispatchEvent(new Event("session-expired")));
  await waitFor(() => expect(client.getQueryData(["me"])).toBeNull());
  expect(await screen.findByRole("heading", { name: "Your next move starts here." })).toBeInTheDocument();
  expect(screen.queryByRole("navigation", { name: "Main navigation" })).not.toBeInTheDocument();
});
