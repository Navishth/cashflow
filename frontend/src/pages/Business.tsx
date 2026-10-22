import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useOutletContext } from "react-router-dom";
import { ArrowUpRight, Building2 } from "lucide-react";
import { api } from "../lib/api";
import { businessSchema, type BusinessForm } from "../lib/schemas";
import type { Business, User } from "../lib/types";
import { todayIndia } from "../lib/utils";
import { Button } from "../components/ui/button";
import { ErrorBox, Field, PageHeader } from "../components/Common";
export interface WorkspaceContext { business: Business; user: User }
export function BusinessEditor({ business }: { business?: Business }) {
  const client = useQueryClient(); const navigate = useNavigate();
  const { register, handleSubmit, watch, formState: { errors } } = useForm<BusinessForm>({ resolver: zodResolver(businessSchema), defaultValues: {
    name: business?.name ?? "", timezone: business?.timezone ?? "Asia/Kolkata", openingDate: business?.openingDate ?? todayIndia(),
    openingBalance: String(business?.openingBalance ?? "0"), reserve: String(business?.reserve ?? "50000"), demo: business?.demo ?? false,
  } });
  const mutation = useMutation({ mutationFn: (v: BusinessForm) => api<Business>(business ? "/businesses/current" : "/businesses", business ? "PATCH" : "POST", { ...v, currency: "INR", openingBalance: Number(v.openingBalance), reserve: Number(v.reserve) }), onSuccess: async result => { client.setQueryData(["business"], result); await client.invalidateQueries({ queryKey: ["forecast"] }); if (!business) navigate("/app"); } });
  return <form className="form-stack" onSubmit={handleSubmit(v => mutation.mutate(v))}>
    <Field label="Business name" error={errors.name?.message}><input {...register("name")} placeholder="e.g. Studio Banyan" maxLength={120}/></Field>
    <div className="form-grid"><Field label="Currency"><input value="INR · Indian rupee" disabled/></Field><Field label="Business timezone" error={errors.timezone?.message}><select {...register("timezone")}><option>Asia/Kolkata</option><option>Asia/Dubai</option><option>Europe/London</option><option>America/New_York</option><option>UTC</option></select></Field></div>
    <div className="form-grid"><Field label="Opening cash balance (₹)" error={errors.openingBalance?.message} hint="Cash at the start of the opening date."><input {...register("openingBalance")} inputMode="decimal"/></Field><Field label="Opening date" error={errors.openingDate?.message}><input {...register("openingDate")} type="date" max={todayIndia()}/></Field></div>
    <Field label="Minimum cash reserve (₹)" error={errors.reserve?.message} hint="The cash cushion you want to keep."><input {...register("reserve")} inputMode="decimal"/></Field>
    {!business && <label className="checkbox-card"><input {...register("demo")} type="checkbox"/><span><strong>Explore with a sample business</strong><small>Creates an isolated, clearly labeled demo with synthetic history. Opening balance becomes ₹1,80,000 at the start of its history.</small></span></label>}
    <div className="notice">{watch("demo") ? "This business contains synthetic sample data and will stay labeled as a demo." : "Forecast quality depends on complete records. Only settled transactions change current cash. Planned transactions shape the forecast."}</div>
    <ErrorBox error={mutation.error}/>{mutation.isSuccess && business && <p className="notice success" role="status">Business settings saved.</p>}
    <Button disabled={mutation.isPending}>{mutation.isPending ? "Saving…" : business ? "Save settings" : "Create my workspace"}<ArrowUpRight size={17}/></Button>
  </form>;
}
export function Onboarding() { return <main className="standalone"><section className="card onboarding"><div className="empty-icon"><Building2/></div><p className="eyebrow">YOUR BUSINESS, YOUR STARTING POINT</p><h1>Let’s meet your business.</h1><p className="muted">A few details now. A clearer view of what comes next.</p><BusinessEditor/></section></main>; }
export function Settings() {
  const { business, user } = useOutletContext<WorkspaceContext>();
  return <><PageHeader eyebrow="MAKE IT YOURS" title="Business & account">Keep your starting point and cash cushion up to date.</PageHeader><div className="settings-grid"><section className="card"><h2>Business settings</h2><BusinessEditor business={business}/></section><aside className="card settings-account"><h2>Your account</h2><div className="avatar">{user.displayName.slice(0, 1).toUpperCase()}</div><h3>{user.displayName}</h3><p>{user.email}</p><span className="badge">Email verified</span><p className="muted">Use password recovery to change your password. Existing sessions expire after a reset.</p><Button variant="secondary" onClick={() => window.location.assign("/forgot-password")}>Reset password</Button></aside></div></>;
}
