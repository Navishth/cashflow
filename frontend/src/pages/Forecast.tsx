import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useOutletContext } from "react-router-dom";
import { ArrowDownLeft, ArrowUpRight, GitBranch, RefreshCw, ShieldCheck, TrendingUp } from "lucide-react";
import { api, missing } from "../lib/api";
import type { Forecast as ForecastData } from "../lib/types";
import { dateLabel, money } from "../lib/utils";
import type { WorkspaceContext } from "./Business";
import { Button } from "../components/ui/button";
import { Empty, ErrorBox, Loading, PageHeader } from "../components/Common";
import { BalanceChart } from "../components/BalanceChart";

export function useForecast() {
  const client = useQueryClient();
  const query = useQuery({ queryKey: ["forecast"], queryFn: () => api<ForecastData>("/forecasts/latest") });
  const generate = useMutation({ mutationFn: () => api<ForecastData>("/forecasts/generate", "POST"), onSuccess: result => { client.setQueryData(["forecast"], result); } });
  return { query, generate };
}
export function ForecastAssumptions({ forecast }: { forecast: ForecastData }) {
  return <details className="assumptions"><summary>About this forecast · {forecast.modelName}</summary><p className="small muted">Generated {new Date(forecast.generatedAt).toLocaleString("en-IN", { timeZone: "Asia/Kolkata" })} IST · Version {forecast.modelVersion}</p><ul>{forecast.limitations.map((note, i) => <li key={i}>{note}</li>)}</ul><p>Scheduled transactions and recurring commitments are included separately from estimated unplanned movement. Current cash includes settled transactions through today.</p></details>;
}
export function ForecastPage({ dashboard = false }: { dashboard?: boolean }) {
  const { business, user } = useOutletContext<WorkspaceContext>();
  const { query, generate } = useForecast();
  const forecast = query.data;
  const inflows = forecast?.points.reduce((total, p) => total + p.scheduledInflows, 0) ?? 0;
  const outflows = forecast?.points.reduce((total, p) => total + p.scheduledOutflows, 0) ?? 0;
  return <>
    <PageHeader eyebrow={dashboard ? `YOUR BUSINESS AT A GLANCE · ${dateLabel(business.today).toUpperCase()}` : "LOOK A LITTLE FURTHER"}
      title={dashboard ? `A clearer day, ${user.displayName.split(" ")[0]}.` : "Your next 30 days"}
      action={<Button disabled={generate.isPending} onClick={() => generate.mutate()}><RefreshCw size={16} className={generate.isPending ? "spin" : ""}/>{generate.isPending ? "Generating…" : forecast ? "Refresh forecast" : "Generate forecast"}</Button>}>
      {dashboard ? "Here’s where your cash stands, and where it could go next." : "Known commitments, estimated movement, and a daily view of your cash."}
    </PageHeader>
    <ErrorBox error={generate.error}/>
    {business.demo && <div className="demo-banner"><span className="badge">SAMPLE BUSINESS</span> You’re exploring synthetic data. Your demo stays separate from real business records.</div>}
    {forecast?.stale && <div className="notice warning">Your records or forecast dates have changed. Refresh the forecast to include the latest information.</div>}
    {query.isPending ? <Loading/> : query.error && !missing(query.error) ? <ErrorBox error={query.error} retry={() => void query.refetch()}/> :
      !forecast ? <Empty title="Your future cash starts here." action={<Button onClick={() => generate.mutate()} disabled={generate.isPending}>Generate your first forecast <ArrowUpRight size={16}/></Button>}>Add expected receipts and expenses, then see the next 30 days. Your current cash is {money(business.currentBalance)}.</Empty> : <>
      <div className="metric-grid">
        <article className="metric primary-metric"><div><span>Current cash position</span><span className="metric-icon"><TrendingUp size={17}/></span></div><strong>{money(business.currentBalance)}</strong><small>Settled records through {dateLabel(business.today)}</small></article>
        <article className="metric"><div><span>Expected inflows</span><ArrowDownLeft size={18} className="inflow"/></div><strong>{money(inflows)}</strong><small>Scheduled over the next 30 days</small></article>
        <article className="metric"><div><span>Expected outflows</span><ArrowUpRight size={18} className="outflow"/></div><strong>{money(outflows)}</strong><small>Including recurring commitments</small></article>
        <article className="metric"><div><span>Lowest projected cash</span><ShieldCheck size={18}/></div><strong>{money(forecast.summary.minimumBalance)}</strong><small>{dateLabel(forecast.summary.minimumDate)} · {forecast.summary.reserveBreachDate ? "Below your reserve" : "Above or at your reserve"}</small></article>
      </div>
      <section className="card forecast-card"><div className="section-heading"><div><p className="eyebrow">THE ROAD AHEAD</p><h2>Cash, in perspective.</h2><p className="muted small">{dateLabel(forecast.startDate)} – {dateLabel(forecast.endDate)} · Projected daily closing balance</p></div><span className={`badge ${forecast.summary.reserveBreachDate ? "badge-warning" : ""}`}>{forecast.summary.reserveBreachDate ? `Reserve breach · ${dateLabel(forecast.summary.reserveBreachDate)}` : "Reserve maintained in this forecast"}</span></div><BalanceChart forecast={forecast}/><div className="chart-footer"><span>Minimum reserve <strong>{money(forecast.reserve)}</strong></span><span>Projected closing cash <strong>{money(forecast.summary.finalBalance)}</strong></span></div><ForecastAssumptions forecast={forecast}/></section>
      {dashboard ? <div className="dashboard-bottom"><section className="card upcoming"><div className="section-heading"><h2>Coming up next</h2><Link className="text-link" to="/app/cash-flows">Manage cash flows <ArrowUpRight size={15}/></Link></div>
        {forecast.events.length === 0 ? <p className="muted">No scheduled transactions yet. Add what you know is coming.</p> : forecast.events.slice(0, 5).map(event => <div className="upcoming-row" key={event.id}><span className={`flow-icon ${event.type === "INFLOW" ? "inflow" : "outflow"}`}>{event.type === "INFLOW" ? <ArrowDownLeft size={18}/> : <ArrowUpRight size={18}/>}</span><div><strong>{event.description}</strong><small>{dateLabel(event.date)} · {event.origin === "RECURRING" ? "Recurring" : "Planned"}</small></div><span className={event.type === "INFLOW" ? "inflow" : ""}>{event.type === "INFLOW" ? "+" : "−"}{money(event.amount)}</span></div>)}
      </section><aside className="replay-promo"><span className="promo-icon"><GitBranch/></span><span className="eyebrow">DECISION REPLAY</span><h2>Try a different<br/><em>tomorrow.</em></h2><p>What if a customer pays late? Or you invest in new equipment? See the impact before you decide.</p><Button asChild><Link to="/app/replay">Explore a scenario <ArrowUpRight size={17}/></Link></Button><small>Your actual records stay unchanged.</small></aside></div> :
      <section className="card"><div className="section-heading"><h2>The daily detail</h2><span className="small muted">All amounts in INR</span></div><div className="table-scroll"><table><thead><tr><th>Date</th><th>Scheduled inflows</th><th>Scheduled outflows</th><th>Estimated unplanned net</th><th>Closing cash</th></tr></thead><tbody>{forecast.points.map(point => <tr key={point.date} className={point.closingBalance < forecast.reserve ? "breach-row" : ""}><td>{dateLabel(point.date)}</td><td>{money(point.scheduledInflows)}</td><td>{money(point.scheduledOutflows)}</td><td>{money(point.predictedUnplannedNet)}</td><td><strong>{money(point.closingBalance)}</strong>{point.closingBalance < forecast.reserve && <span className="sr-only"> below reserve</span>}</td></tr>)}</tbody></table></div></section>}
    </>}
  </>;
}
