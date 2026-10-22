import { afterEach, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthPage } from "./Auth";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
function mount(mode: "login" | "register", enabled: boolean) {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
    googleEnabled: enabled,
    googleAuthorizationUrl: enabled ? "http://localhost:5173/oauth2/authorization/google" : null,
  }), { headers: { "Content-Type": "application/json" } })));
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><MemoryRouter><AuthPage mode={mode}/></MemoryRouter></QueryClientProvider>);
}

it.each(["login", "register"] as const)("offers Google on %s using the server's canonical origin", async mode => {
  mount(mode, true);
  expect(await screen.findByRole("link", { name: "Continue with Google" }))
    .toHaveAttribute("href", "http://localhost:5173/oauth2/authorization/google");
});

it.each(["login", "register"] as const)("does not advertise unconfigured Google sign-in on %s", async mode => {
  mount(mode, false);
  await screen.findByRole("button", { name: mode === "login" ? "Sign in" : "Create account" });
  expect(screen.queryByRole("link", { name: "Continue with Google" })).not.toBeInTheDocument();
});
