import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowLeft, ArrowUpRight, Orbit, ShieldCheck } from "lucide-react";
import { api } from "../lib/api";
import type { User } from "../lib/types";
import { Button } from "../components/ui/button";
import { ErrorBox, Field } from "../components/Common";

const schema = z.object({ email: z.email("Enter a valid email."), password: z.string().min(12, "Use at least 12 characters.").max(72), displayName: z.string().max(100) });
type Values = z.infer<typeof schema>;
export function AuthPage({ mode }: { mode: "login" | "register" }) {
  const registering = mode === "register";
  const navigate = useNavigate(); const client = useQueryClient();
  const { register, handleSubmit, formState: { errors } } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: "", password: "", displayName: "" } });
  const options = useQuery({ queryKey: ["auth-options"], queryFn: () => api<{ googleEnabled: boolean; googleAuthorizationUrl: string | null }>("/auth/options") });
  const mutation = useMutation({ mutationFn: (v: Values) => api<User | { message: string }>(`/auth/${mode}`, "POST", v), onSuccess: async (data) => {
    if (!registering) { client.setQueryData(["me"], data); await client.invalidateQueries({ queryKey: ["business"] }); navigate("/app"); }
  } });
  return <div className="auth-layout"><aside className="auth-story"><Link className="brand" to="/"><Orbit/> CashFlow </Link><div><p className="eyebrow">A little clarity goes a long way.</p><h1>Know your cash.<br/>Choose your<br/><em>next move.</em></h1><p>Your next 30 days, made clearer. Bring your receipts, commitments, and possibilities into one view.</p><div className="story-line"/><span className="small">Built for the business you’re building.</span></div><span className="small">INR · Thoughtful decisions · Your business</span></aside>
    <main className="auth-main"><Link to="/" className="back-link"><ArrowLeft size={16}/> Back to home</Link><div className="auth-card"><span className="eyebrow">{registering ? "LET’S GET YOU STARTED" : "WELCOME BACK"}</span><h1>{registering ? "Make room for clarity." : "Your next move starts here."}</h1><p className="muted">{registering ? "Create your account to start understanding your cash flow." : "Sign in to see where your business is heading."}</p>
      {mutation.isSuccess && registering ? <div className="notice success" role="status">If this account can be registered, a verification message has been queued. Check your email to continue. <Link to="/login">Go to sign in</Link></div> :
      <form onSubmit={handleSubmit(v => mutation.mutate(v))} className="form-stack">
        {registering && <Field label="Your name" error={errors.displayName?.message}><input {...register("displayName")} autoComplete="name" required maxLength={100}/></Field>}
        <Field label="Email address" error={errors.email?.message}><input {...register("email")} type="email" autoComplete="email" placeholder="you@yourbusiness.com"/></Field>
        <Field label="Password" error={errors.password?.message} hint={registering ? "At least 12 characters. Use a unique passphrase." : undefined}><input {...register("password")} type="password" autoComplete={registering ? "new-password" : "current-password"}/></Field>
        {!registering && <Link className="form-link" to="/forgot-password">Forgot password?</Link>}
        <ErrorBox error={mutation.error}/>
        <Button disabled={mutation.isPending}>{mutation.isPending ? "Please wait…" : registering ? "Create account" : "Sign in"}<ArrowUpRight size={17}/></Button>
      </form>}
      {options.data?.googleEnabled && options.data.googleAuthorizationUrl && <Button className="full-width" variant="secondary" asChild><a href={options.data.googleAuthorizationUrl}>Continue with Google</a></Button>}
      {new URLSearchParams(window.location.search).has("oauth") && <p role="alert" className="field-error">Google sign-in could not complete. Try your original sign-in method.</p>}
      <p className="auth-switch">{registering ? "Already have an account?" : "New to CashFlow?"} <Link to={registering ? "/login" : "/register"}>{registering ? "Sign in" : "Create an account"}</Link></p>
      <div className="auth-security"><ShieldCheck size={16}/> Protected sessions. Your business data stays yours.</div>
    </div></main></div>;
}

export function RecoveryPage({ mode }: { mode: "verify-email" | "forgot-password" | "reset-password" }) {
  const [token] = useState(() => {
    const value = new URLSearchParams(window.location.hash.slice(1)).get("token") ?? "";
    if (value) window.history.replaceState(null, "", window.location.pathname);
    return value;
  });
  const [email, setEmail] = useState(""); const [password, setPassword] = useState("");
  const [manualToken, setManualToken] = useState(token);
  const mutation = useMutation({ mutationFn: () => api<{ message: string }>(`/auth/${mode}`, "POST", mode === "forgot-password" ? { email } : mode === "reset-password" ? { token: manualToken, password } : { token: manualToken }) });
  const resend = useMutation({ mutationFn: () => api<{ message: string }>("/auth/resend-verification", "POST", { email }) });
  const title = mode === "verify-email" ? "Verify your email" : mode === "forgot-password" ? "Let’s get you back in." : "Choose a new password";
  return <main className="standalone"><Link className="brand" to="/"><Orbit/> CashFlow </Link><section className="card recovery-card"><h1>{title}</h1><p className="muted">{mode === "verify-email" ? "Confirm your email to activate your account." : "Secure access to your business starts here."}</p>
    <form className="form-stack" onSubmit={e => { e.preventDefault(); mutation.mutate(); }}>
      {mode === "forgot-password" ? <Field label="Email"><input type="email" required value={email} onChange={e => setEmail(e.target.value)}/></Field> : <Field label="Verification link token"><input required value={manualToken} onChange={e => setManualToken(e.target.value)} autoComplete="off"/></Field>}
      {mode === "reset-password" && <Field label="New password" hint="At least 12 characters."><input type="password" autoComplete="new-password" minLength={12} maxLength={72} required value={password} onChange={e => setPassword(e.target.value)}/></Field>}
      <ErrorBox error={mutation.error}/>{mutation.data && <p role="status" className="notice success">{mutation.data.message}</p>}
      <Button disabled={mutation.isPending || (mutation.isSuccess && mode !== "forgot-password")}>{mutation.isPending ? "Working…" : mode === "forgot-password" ? "Send recovery link" : "Confirm"}</Button>
    </form>
    {mode === "verify-email" && <details><summary>Need another verification message?</summary><form className="form-stack" onSubmit={e => { e.preventDefault(); resend.mutate(); }}><Field label="Email"><input type="email" required value={email} onChange={e => setEmail(e.target.value)}/></Field><Button variant="secondary" disabled={resend.isPending}>Resend verification</Button><ErrorBox error={resend.error}/>{resend.data && <p role="status">{resend.data.message}</p>}</form></details>}
    <Link className="back-link" to="/login">Back to sign in</Link></section></main>;
}
