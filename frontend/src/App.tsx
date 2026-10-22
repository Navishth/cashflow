import { useEffect } from "react";
import { Link, Navigate, NavLink, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeftRight, ArrowUpRight, Building2, ChartNoAxesCombined, GitBranch, LayoutDashboard, LogOut, Orbit, Settings2 } from "lucide-react";
import { api, ApiError, missing } from "./lib/api";
import type { Business, User } from "./lib/types";
import Landing from "./pages/Landing";
import { AuthPage, RecoveryPage } from "./pages/Auth";
import { Onboarding, Settings, type WorkspaceContext } from "./pages/Business";
import { ForecastPage } from "./pages/Forecast";
import CashFlows from "./pages/CashFlows";
import Replay from "./pages/Replay";
import { Button } from "./components/ui/button";
import { ErrorBox, Loading } from "./components/Common";

function Guard() {
  const client = useQueryClient(); const location = useLocation();
  const user = useQuery({ queryKey: ["me"], queryFn: () => api<User>("/auth/me") });
  useEffect(() => {
    const expired = () => client.setQueryData(["me"], null);
    window.addEventListener("session-expired", expired);
    return () => window.removeEventListener("session-expired", expired);
  }, [client]);
  if (user.isPending) return <div className="standalone"><Loading/></div>;
  if (user.error && !(user.error instanceof ApiError && user.error.status === 401)) return <div className="standalone"><ErrorBox error={user.error} retry={() => void user.refetch()}/></div>;
  if (!user.data) return <Navigate to="/login" replace state={{ from: location.pathname }}/>;
  return <Outlet context={user.data}/>;
}
function Workspace() {
  const client = useQueryClient();
  const user = client.getQueryData<User>(["me"]);
  const business = useQuery({ queryKey: ["business"], queryFn: () => api<Business>("/businesses/current") });
  const logout = useMutation({ mutationFn: () => api<void>("/auth/logout", "POST"), onSuccess: () => { client.clear(); window.location.assign("/login"); } });
  if (business.isPending) return <div className="standalone"><Loading/></div>;
  if (missing(business.error)) return <Navigate to="/onboarding" replace/>;
  if (business.error || !business.data || !user) return <div className="standalone"><ErrorBox error={business.error} retry={() => void business.refetch()}/></div>;
  const nav = [{ to: "/app", title: "Overview", icon: LayoutDashboard, end: true }, { to: "/app/cash-flows", title: "Cash flows", icon: ArrowLeftRight }, { to: "/app/forecast", title: "Forecast", icon: ChartNoAxesCombined }, { to: "/app/replay", title: "Decision Replay", icon: GitBranch }];
  return <div className="workspace"><aside className="sidebar"><Link to="/app" className="brand"><Orbit/> CashFlow </Link><div className="business-tag"><span><Building2 size={19}/></span><div><strong>{business.data.name}</strong><small>{business.data.demo ? "Demo workspace" : "Your workspace"} · INR</small></div></div><span className="nav-label">WORKSPACE</span><nav aria-label="Main navigation">{nav.map(item => <NavLink key={item.to} to={item.to} end={item.end} className={({ isActive }) => isActive ? "active" : ""}><item.icon size={18}/>{item.title}{item.title === "Decision Replay" && <span className="nav-new">TRY IT</span>}</NavLink>)}</nav><div className="sidebar-bottom"><div className="sidebar-note"><span className="live-dot"/> A little foresight.<br/><strong>A more confident next step.</strong></div><NavLink to="/app/settings"><Settings2 size={18}/> Settings</NavLink><Button variant="ghost" onClick={() => logout.mutate()} disabled={logout.isPending}><LogOut size={17}/> Sign out</Button><ErrorBox error={logout.error}/></div></aside><div className="workspace-body"><div className="topbar"><span><span className="live-dot"/> Your cash. A clearer perspective.</span><div className="topbar-user"><span>{user.displayName}</span><span className="avatar">{user.displayName.charAt(0).toUpperCase()}</span></div></div><main className="workspace-content"><Outlet context={{ business: business.data, user } satisfies WorkspaceContext}/></main><footer className="workspace-footer"><span>CashFlow · Clarity for what comes next.</span><span>Estimates, never guarantees.</span></footer></div></div>;
}
export default function App() {
  return <Routes>
    <Route path="/" element={<Landing/>}/><Route path="/login" element={<AuthPage mode="login"/>}/><Route path="/register" element={<AuthPage mode="register"/>}/>
    <Route path="/verify-email" element={<RecoveryPage mode="verify-email"/>}/><Route path="/forgot-password" element={<RecoveryPage mode="forgot-password"/>}/><Route path="/reset-password" element={<RecoveryPage mode="reset-password"/>}/>
    <Route element={<Guard/>}><Route path="/onboarding" element={<Onboarding/>}/><Route path="/app" element={<Workspace/>}><Route index element={<ForecastPage dashboard/>}/><Route path="cash-flows" element={<CashFlows/>}/><Route path="forecast" element={<ForecastPage/>}/><Route path="replay" element={<Replay/>}/><Route path="settings" element={<Settings/>}/></Route></Route>
    <Route path="*" element={<main className="standalone"><h1>This page isn’t here.</h1><Button asChild><Link to="/app">Back to your workspace <ArrowUpRight size={16}/></Link></Button></main>}/>
  </Routes>;
}
