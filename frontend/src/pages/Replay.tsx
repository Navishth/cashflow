import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { GitBranch, Play, Plus, Pencil, Trash2, X } from "lucide-react";
import { api, missing } from "../lib/api";
import type { Change, ChangeKind, Comparison, Forecast, Page, Scenario } from "../lib/types";
import { dateLabel, money } from "../lib/utils";
import { useForecast, ForecastAssumptions } from "./Forecast";
import { BalanceChart } from "../components/BalanceChart";
import { Button } from "../components/ui/button";
import { Confirm, Empty, ErrorBox, Field, Loading, Modal, PageHeader } from "../components/Common";

const labels: Record<ChangeKind, string> = { RESCHEDULE: "Move a receipt or payment", AMOUNT_CHANGE: "Change an amount", ADD_CASH_FLOW: "Add a hypothetical transaction", REMOVE_CASH_FLOW: "Exclude a planned transaction" };
function ScenarioEditor({ scenario, latest, close, saved }: { scenario?: Scenario; latest: Forecast; close: () => void; saved: (s: Scenario) => void }) {
  const [name, setName] = useState(scenario?.name ?? "");
  const [description, setDescription] = useState(scenario?.description ?? "");
  const [changes, setChanges] = useState<Change[]>(scenario?.changes ?? [{ kind: "RESCHEDULE", targetEventId: latest.events[0]?.id ?? null, newDate: latest.startDate, amount: null, type: null, description: null }]);
  const baselineId = scenario?.baselineId ?? latest.id;
  const query = useQuery({ queryKey: ["baseline", baselineId], queryFn: () => api<Forecast>(`/forecasts/${baselineId}`), initialData: baselineId === latest.id ? latest : undefined });
  const mutation = useMutation({ mutationFn: () => api<Scenario>(scenario ? `/scenarios/${scenario.id}` : "/scenarios", scenario ? "PATCH" : "POST", { name, description, baselineId, changes }), onSuccess: saved });
  const update = (i: number, patch: Partial<Change>) => setChanges(list => list.map((c, j) => j === i ? { ...c, ...patch } : c));
  return <Modal title={scenario ? "Edit your scenario" : "Try a different tomorrow"} close={close}><form className="form-stack" onSubmit={e => { e.preventDefault(); mutation.mutate(); }}>
    <Field label="Scenario name"><input required maxLength={120} value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Client payment arrives two weeks late"/></Field>
    <Field label="Notes (optional)"><input maxLength={500} value={description} onChange={e => setDescription(e.target.value)}/></Field>
    <p className="muted small">Uses the saved baseline from {query.data ? dateLabel(query.data.startDate) : "…"} and the same model estimates. Dates after the horizon remove that movement from this 30-day comparison.</p>
    <ErrorBox error={query.error}/>
    {changes.map((change, i) => <div className="change-card" key={i}><div className="change-heading"><strong>Change {i + 1}</strong>{changes.length > 1 && <Button type="button" variant="ghost" size="small" aria-label={`Remove change ${i + 1}`} onClick={() => setChanges(c => c.filter((_, j) => j !== i))}><X size={15}/></Button>}</div>
      <Field label="What would you like to change?"><select value={change.kind} onChange={e => update(i, { kind: e.target.value as ChangeKind })}>{Object.entries(labels).map(([key, label]) => <option value={key} key={key}>{label}</option>)}</select></Field>
      {change.kind !== "ADD_CASH_FLOW" && <Field label="Scheduled transaction"><select required value={change.targetEventId ?? ""} onChange={e => update(i, { targetEventId: e.target.value })}><option value="">Select a transaction</option>{query.data?.events.map(event => <option key={event.id} value={event.id}>{event.description} · {dateLabel(event.date)} · {money(event.amount)}</option>)}</select></Field>}
      {(change.kind === "RESCHEDULE" || change.kind === "ADD_CASH_FLOW") && <Field label={change.kind === "RESCHEDULE" ? "New expected date" : "Hypothetical date"}><input required type="date" min={query.data?.startDate} value={change.newDate ?? ""} onChange={e => update(i, { newDate: e.target.value })}/></Field>}
      {(change.kind === "AMOUNT_CHANGE" || change.kind === "ADD_CASH_FLOW") && <Field label={change.kind === "AMOUNT_CHANGE" ? "Replacement amount (₹)" : "Amount (₹)"} hint={change.kind === "AMOUNT_CHANGE" ? "Enter the full new amount, not the difference." : undefined}><input required type="number" min="0.01" max="999999999999.99" step="0.01" value={change.amount ?? ""} onChange={e => update(i, { amount: e.target.value === "" ? null : Number(e.target.value) })}/></Field>}
      {change.kind === "ADD_CASH_FLOW" && <><Field label="Direction"><select required value={change.type ?? ""} onChange={e => update(i, { type: e.target.value as "INFLOW" | "OUTFLOW" })}><option value="">Choose a direction</option><option value="INFLOW">Money in</option><option value="OUTFLOW">Money out</option></select></Field><Field label="Description"><input maxLength={200} value={change.description ?? ""} onChange={e => update(i, { description: e.target.value })}/></Field></>}
    </div>)}
    <Button type="button" variant="secondary" disabled={changes.length >= 20} onClick={() => setChanges(c => [...c, { kind: "ADD_CASH_FLOW", targetEventId: null, newDate: query.data?.startDate ?? "", amount: null, type: "OUTFLOW", description: "" }])}><Plus size={16}/> Add another change</Button>
    <ErrorBox error={mutation.error}/><div className="actions"><Button type="button" variant="secondary" onClick={close}>Cancel</Button><Button disabled={mutation.isPending || !query.data}>{mutation.isPending ? "Saving…" : "Save scenario"}</Button></div>
  </form></Modal>;
}
export default function Replay() {
  const client = useQueryClient(); const { query: baseline, generate } = useForecast();
  const [selected, setSelected] = useState<Scenario | null>(null);
  const [editor, setEditor] = useState<Scenario | "new" | null>(null); const [deleting, setDeleting] = useState<Scenario | null>(null);
  const [page, setPage] = useState(0);
  const list = useQuery({ queryKey: ["scenarios", page], queryFn: () => api<Page<Scenario>>(`/scenarios?page=${page}`) });
  const comparison = useQuery({ queryKey: ["comparison", selected?.id], queryFn: () => api<Comparison>(`/scenarios/${selected?.id}/compare`), enabled: !!selected?.simulatedAt });
  const simulate = useMutation({ mutationFn: (id: string) => api<Comparison>(`/scenarios/${id}/simulate`, "POST"), onSuccess: data => { client.setQueryData(["comparison", data.scenario.id], data); setSelected(data.scenario); void client.invalidateQueries({ queryKey: ["scenarios"] }); } });
  const remove = useMutation({ mutationFn: (id: string) => api<void>(`/scenarios/${id}`, "DELETE"), onSuccess: () => { if (selected?.id === deleting?.id) setSelected(null); setDeleting(null); void client.invalidateQueries({ queryKey: ["scenarios"] }); } });
  const saved = (s: Scenario) => { setEditor(null); setSelected(s); void client.invalidateQueries({ queryKey: ["scenarios"] }); simulate.reset(); };
  const result = comparison.data;
  return <>
    <PageHeader eyebrow="DECISION REPLAY" title="What if you chose differently?" action={baseline.data && <Button onClick={() => setEditor("new")}><Plus size={17}/> New scenario</Button>}>A safe space to explore decisions. Your actual records stay unchanged.</PageHeader>
    <ErrorBox error={generate.error}/>
    {baseline.isPending ? <Loading/> : baseline.error && !missing(baseline.error) ? <ErrorBox error={baseline.error}/> : !baseline.data ? <Empty title="First, give your decision a baseline." action={<Button disabled={generate.isPending} onClick={() => generate.mutate()}>Generate baseline</Button>}>A saved 30-day forecast gives every scenario the same starting point.</Empty> :
    <div className="replay-layout"><aside className="card scenario-list"><div className="section-heading"><h2>Your scenarios</h2><GitBranch size={18}/></div><ErrorBox error={list.error}/>{list.isPending ? <Loading/> : !list.data?.items.length ? <p className="muted small">No scenarios yet. Try delaying a receipt or adding an expense.</p> : list.data.items.map(s => <div className={`scenario-row ${selected?.id === s.id ? "selected" : ""}`} key={s.id}><button className="scenario-select" onClick={() => { setSelected(s); simulate.reset(); }}><strong>{s.name}</strong><small>{s.changes.length} change{s.changes.length === 1 ? "" : "s"} · {s.simulatedAt ? "Simulated" : "Ready to simulate"}</small></button><div className="row-actions"><Button variant="ghost" size="small" aria-label={`Edit ${s.name}`} onClick={() => setEditor(s)}><Pencil size={14}/></Button><Button variant="ghost" size="small" aria-label={`Delete ${s.name}`} onClick={() => { remove.reset(); setDeleting(s); }}><Trash2 size={14}/></Button></div></div>)}<div className="actions"><Button size="small" variant="ghost" disabled={!page} onClick={() => setPage(p => p - 1)}>Previous</Button><Button size="small" variant="ghost" disabled={page + 1 >= (list.data?.totalPages ?? 0)} onClick={() => setPage(p => p + 1)}>Next</Button></div><p className="small muted">Each scenario keeps its original baseline and assumptions, even when your financial records change.</p></aside>
    <div className="comparison-area">{!selected ? <section className="card"><Empty title="A small change. A different future." action={<Button onClick={() => setEditor("new")}><GitBranch size={16}/> Build your first scenario</Button>}>Select a saved scenario or create one to compare your next 30 days.</Empty></section> : <>
      <section className="card"><div className="section-heading"><div><p className="eyebrow">YOUR ALTERNATIVE</p><h2>{selected.name}</h2>{selected.description && <p className="muted">{selected.description}</p>}</div><Button disabled={simulate.isPending} onClick={() => simulate.mutate(selected.id)}><Play size={15}/>{simulate.isPending ? "Simulating…" : selected.simulatedAt ? "Run again" : "Simulate"}</Button></div>
      <ul className="change-summary">{selected.changes.map((c, i) => <li key={i}>{labels[c.kind]}{c.newDate ? ` · ${dateLabel(c.newDate)}` : ""}{c.amount ? ` · ${money(c.amount)}` : ""}</li>)}</ul><ErrorBox error={simulate.error}/></section>
      {selected.simulatedAt && (comparison.isPending ? <Loading/> : comparison.error ? <ErrorBox error={comparison.error}/> : result && <>
        {(result.newerBaselineAvailable || result.baseline.stale) && <div className="notice warning">This comparison uses an older saved baseline. Create a new scenario to use the latest forecast.</div>}
        <div className="comparison-metrics"><article className="metric"><span>Baseline minimum</span><strong>{money(result.baseline.summary.minimumBalance)}</strong><small>{dateLabel(result.baseline.summary.minimumDate)}</small></article><article className="metric scenario-metric"><span>Scenario minimum</span><strong>{money(result.summary.minimumBalance)}</strong><small>{dateLabel(result.summary.minimumDate)}</small></article><article className="metric"><span>Change in minimum</span><strong className={result.minimumBalanceChange < 0 ? "outflow" : "inflow"}>{money(result.minimumBalanceChange)}</strong><small>Versus the same baseline</small></article></div>
        <section className="card"><h2>Two paths. One starting point.</h2><BalanceChart forecast={result.baseline} scenario={result.points}/><div className="notice">{result.explanation}</div><div className="breach-comparison"><span>Baseline reserve breach: <strong>{result.baseline.summary.reserveBreachDate ? dateLabel(result.baseline.summary.reserveBreachDate) : "None projected"}</strong></span><span>Scenario reserve breach: <strong>{result.summary.reserveBreachDate ? dateLabel(result.summary.reserveBreachDate) : "None projected"}</strong></span></div><ForecastAssumptions forecast={result.baseline}/></section>
        <section className="card"><h2>Daily impact</h2><div className="table-scroll"><table><thead><tr><th>Date</th><th>Baseline</th><th>Scenario</th><th>Difference</th></tr></thead><tbody>{result.points.map((p, i) => <tr key={p.date}><td>{dateLabel(p.date)}</td><td>{money(result.baseline.points[i].closingBalance)}</td><td>{money(p.closingBalance)}</td><td className={result.deltas[i].difference < 0 ? "outflow" : "inflow"}>{money(result.deltas[i].difference)}</td></tr>)}</tbody></table></div></section>
      </>)}
    </>}</div></div>}
    {editor && baseline.data && <ScenarioEditor scenario={editor === "new" ? undefined : editor} latest={baseline.data} close={() => setEditor(null)} saved={saved}/>}
    {deleting && <Confirm title="Delete this scenario?" close={() => setDeleting(null)} confirm={() => remove.mutate(deleting.id)} pending={remove.isPending} error={remove.error}>{deleting.name} and its saved simulation will be deleted.</Confirm>}
  </>;
}
