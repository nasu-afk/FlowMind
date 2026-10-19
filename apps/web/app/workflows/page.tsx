"use client";

import "./styles.css";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowRight, GitBranch, Plus, TriangleAlert } from "lucide-react";

type Workflow = { id: string; name: string; description?: string; publishedVersion?: number; updatedAt: string };

export default function WorkflowsPage() {
  const [workflows, setWorkflows] = useState<Workflow[]>([]);
  const [error, setError] = useState("");
  useEffect(() => {
    fetch("/api/session/workflows", { cache: "no-store" })
      .then(async response => {
        if (!response.ok) throw new Error(response.status === 401 ? "Sign in to view workflows" : "Could not load workflows");
        return response.json();
      })
      .then(setWorkflows)
      .catch(failure => setError(failure.message));
  }, []);
  return <main className="workflowListPage">
    <header><Link href="/dashboard" className="brand"><span className="brandmark">F</span>FlowMind</Link><Link href="/dashboard">Dashboard</Link></header>
    <section>
      <div className="workflowListTitle"><div><span>WORKFLOWS</span><h1>Automation library</h1><p>Open an existing workflow or intentionally create a new draft.</p></div><Link href="/workflows/demo" className="newFlow"><Plus/> New workflow</Link></div>
      {error && <div className="dashNotice"><TriangleAlert/><div><strong>Workflows unavailable</strong><p>{error}</p></div></div>}
      {!error && workflows.length === 0 && <div className="workflowEmpty"><GitBranch/><strong>No workflows yet</strong><p>Create your first draft when you are ready.</p><Link href="/workflows/demo">Create workflow</Link></div>}
      <div className="workflowCards">{workflows.map(workflow => <Link href={`/workflows/${workflow.id}`} key={workflow.id}>
        <div className="nodeIcon"><GitBranch/></div><div><span>{workflow.publishedVersion ? `PUBLISHED · V${workflow.publishedVersion}` : "DRAFT"}</span><h2>{workflow.name}</h2><p>{workflow.description || "No description"}</p><small>Updated {new Date(workflow.updatedAt).toLocaleString()}</small></div><ArrowRight/>
      </Link>)}</div>
    </section>
  </main>;
}
