"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Activity, ArrowUpRight, CheckCircle2, Clock3, GitBranch, Plus, Sparkles, TriangleAlert } from "lucide-react";
import type { LucideIcon } from "lucide-react";

type Summary = { totalExecutions: number; completed: number; failed: number; averageDurationMs: number };
type Daily = { day: string; total: number; completed: number; failed: number };

function formatDuration(milliseconds: number) {
  if (milliseconds < 1000) return `${Math.round(milliseconds)} ms`;
  const seconds = Math.round(milliseconds / 1000);
  if (seconds < 60) return `${seconds} s`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  return `${hours} h ${minutes % 60} min`;
}

export default function Dashboard() {
  const [data, setData] = useState<Summary | null>(null);
  const [daily, setDaily] = useState<Daily[]>([]);
  const [error, setError] = useState("");
  useEffect(() => {
    Promise.all([
      fetch("/api/session/analytics", { cache: "no-store" }),
      fetch("/api/session/analytics/daily?days=30", { cache: "no-store" }),
    ]).then(async ([summaryResponse, dailyResponse]) => {
      if (!summaryResponse.ok || !dailyResponse.ok) throw new Error(summaryResponse.status === 401 || dailyResponse.status === 401 ? "Sign in to load workspace data" : "Analytics service unavailable");
      const [summary, activity] = await Promise.all([summaryResponse.json(), dailyResponse.json()]);
      setData(summary); setDaily(activity);
    }).catch(failure => setError(failure.message));
  }, []);
  const metrics: Array<{ icon: LucideIcon; label: string; value: string | number | undefined }> = [
    { icon: Activity, label: "Executions", value: data?.totalExecutions },
    { icon: CheckCircle2, label: "Completed", value: data?.completed },
    { icon: TriangleAlert, label: "Failed", value: data?.failed },
    { icon: Clock3, label: "Avg. duration", value: data ? formatDuration(data.averageDurationMs) : undefined },
  ];
  const starts = [
    { icon: GitBranch, title: "Design a workflow", text: "Compose triggers, logic, and actions", href: "/workflows/demo" },
    { icon: Sparkles, title: "Add a Groq AI node", text: "Configure schema-bound model output", href: "/workflows" },
    { icon: CheckCircle2, title: "Inspect execution history", text: "Review every persisted run state", href: "/executions" },
  ];
  const maxDaily = useMemo(() => Math.max(1, ...daily.map(item => item.total)), [daily]);
  return <main className="dash"><header><Link href="/" className="brand"><span className="brandmark">F</span>FlowMind</Link><nav><span className="active">Overview</span><Link href="/workflows">Workflows</Link><Link href="/executions">Executions</Link><span title="Agent Studio is not available yet">Agents</span><Link href="/settings/security">Security</Link></nav><div className="avatar">HK</div></header>
    <section className="dashBody"><div className="dashTitle"><div><span>OPERATIONS</span><h1>Good morning.</h1><p>Here’s how your automation system is performing.</p></div><Link href="/workflows/demo" className="newFlow"><Plus/> New workflow</Link></div>
      {error ? <div className="dashNotice"><TriangleAlert/><div><strong>Workspace data unavailable</strong><p>{error}. Start the API and PostgreSQL stack, then sign in.</p></div></div> : <div className="metrics">{metrics.map(({ icon: Icon, label, value }) => <article key={label}><div><Icon/><span>{label}</span></div><strong>{value === undefined ? "—" : String(value)}</strong><small>Persisted workspace data</small></article>)}</div>}
      <section className="dashGrid"><article className="activityPanel"><div className="panelHead"><div><span>EXECUTION ACTIVITY</span><h2>Latest automation health</h2></div><span className="rangeBadge">Last 30 days</span></div>
        {daily.length === 0 ? <div className="emptyChart"><Activity/><strong>No executions in this range</strong><p>Run a published workflow to populate activity.</p></div> : <div className="activityChart" aria-label="Execution activity over the last 30 days">{daily.map(item => <div className="activityDay" key={item.day} title={`${new Date(item.day).toLocaleDateString()}: ${item.total} executions, ${item.completed} completed, ${item.failed} failed`}><div className="bar" style={{ height: `${Math.max(10, item.total / maxDaily * 100)}%` }}><i style={{ height: `${item.total ? item.completed / item.total * 100 : 0}%` }}/></div><small>{new Date(item.day).toLocaleDateString(undefined, { month: "short", day: "numeric" })}</small></div>)}</div>}
      </article><article className="quickPanel"><span>QUICK START</span><h2>Build your first reliable flow</h2>{starts.map(({ icon: Icon, title, text, href }, index) => <Link href={href} key={title}><b>{index + 1}</b><Icon/><div><strong>{title}</strong><small>{text}</small></div><ArrowUpRight/></Link>)}</article></section>
    </section>
  </main>;
}
