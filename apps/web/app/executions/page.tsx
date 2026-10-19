"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Activity, ArrowLeft, CheckCircle2, Clock3, TriangleAlert, XCircle } from "lucide-react";

type Execution = { id: string; state: string; createdAt: string };

export default function ExecutionsPage() {
  const [items, setItems] = useState<Execution[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    fetch("/api/session/executions", { cache: "no-store" })
      .then(async response => {
        if (!response.ok) throw new Error(response.status === 401 ? "Sign in to inspect executions" : "Execution history is unavailable");
        return response.json();
      })
      .then(setItems).catch(failure => setError(failure.message)).finally(() => setLoading(false));
  }, []);
  const icon = (state: string) => state === "COMPLETED" ? <CheckCircle2/> : state === "FAILED" ? <XCircle/> : state === "WAITING" ? <Clock3/> : <Activity/>;
  return <main className="executionPage"><header><Link href="/dashboard" className="brand"><span className="brandmark">F</span>FlowMind</Link><Link href="/dashboard"><ArrowLeft/>Dashboard</Link></header><section>
    <div className="executionTitle"><div><span>OPERATIONS</span><h1>Execution history</h1><p>Live workflow runs persisted by the durable worker.</p></div><Activity/></div>
    {error ? <div className="dashNotice"><TriangleAlert/><div><strong>Executions unavailable</strong><p>{error}</p></div></div> : loading ? <div className="executionEmpty">Loading executions…</div> : items.length === 0 ? <div className="executionEmpty"><Activity/><strong>No executions yet</strong><p>Publish and test a workflow to create the first run.</p></div> : <div className="executionList">{items.map(item => <article key={item.id}><div className={`executionState ${item.state.toLowerCase()}`}>{icon(item.state)}</div><div><span>{item.state}</span><code>{item.id}</code><small>{new Date(item.createdAt).toLocaleString()}</small></div></article>)}</div>}
  </section></main>;
}
