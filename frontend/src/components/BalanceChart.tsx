import { Area, CartesianGrid, ComposedChart, Legend, Line, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { Forecast, Point } from "../lib/types";
import { dateLabel, money } from "../lib/utils";
export function BalanceChart({ forecast, scenario }: { forecast: Forecast; scenario?: Point[] }) {
  const data = forecast.points.map((p, i) => ({ ...p, scenario: scenario?.[i]?.closingBalance }));
  return <div className="chart" role="img" aria-label="30-day projected closing cash balance. Exact values are available in the forecast table.">
    <ResponsiveContainer width="100%" height="100%">
      <ComposedChart data={data} margin={{ top: 18, right: 12, left: 0, bottom: 0 }}>
        <defs><linearGradient id="cashFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#338264" stopOpacity={0.19}/><stop offset="100%" stopColor="#338264" stopOpacity={0.01}/></linearGradient></defs>
        <CartesianGrid strokeDasharray="3 5" vertical={false} stroke="#e6e9e5"/>
        <XAxis dataKey="date" tickFormatter={dateLabel} tickLine={false} axisLine={false} minTickGap={35} tick={{ fontSize: 11, fill: "#6d7972" }}/>
        <YAxis tickFormatter={v => money(Number(v), true)} tickLine={false} axisLine={false} width={72} tick={{ fontSize: 11, fill: "#6d7972" }} domain={["auto", "auto"]}/>
        <Tooltip labelFormatter={v => dateLabel(String(v))} formatter={v => money(Number(v))} contentStyle={{ borderRadius: 12, border: "1px solid #e4e8e2", fontSize: 13 }}/>
        <ReferenceLine y={forecast.reserve} ifOverflow="extendDomain" stroke="#b48839" strokeDasharray="5 5" label={{ value: "Reserve", position: "insideTopRight", fill: "#8a6c37", fontSize: 11 }}/>
        <Area type="linear" dataKey="closingBalance" name="Baseline" stroke="#287856" fill="url(#cashFill)" strokeWidth={2.5}/>
        {scenario && <Line type="linear" dataKey="scenario" name="Scenario" stroke="#7865ba" dot={false} strokeWidth={2.5}/>}
        <Legend iconType="circle" wrapperStyle={{ fontSize: 12, paddingTop: 12 }}/>
      </ComposedChart>
    </ResponsiveContainer>
  </div>;
}
