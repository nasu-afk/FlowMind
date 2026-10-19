"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ArrowLeft, Check, Clock3, ShieldCheck, X } from "lucide-react";

type Approval = {
  id: string;
  executionId: string;
  nodeId: string;
  assignedRole?: string;
  status: string;
  expiresAt?: string;
  createdAt: string;
};

export default function ApprovalsPage() {
  const [items,setItems]=useState<Approval[]>([]);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState("");
  const [busy,setBusy]=useState<string>();

  const load=useCallback(async()=>{
    setLoading(true);setError("");
    try{
      const response=await fetch("/api/session/approvals",{cache:"no-store"});
      const body=await response.json().catch(()=>[]);
      if(!response.ok)throw new Error(body.detail??"Could not load approvals");
      setItems(body);
    }catch(failure){setError(failure instanceof Error?failure.message:"Could not load approvals");}
    finally{setLoading(false);}
  },[]);

  useEffect(()=>{void load();},[load]);

  const decide=async(item:Approval,decision:"APPROVED"|"REJECTED")=>{
    const comment=decision==="REJECTED"?window.prompt("Why is this execution being rejected?")??"":"Approved in FlowMind";
    if(decision==="REJECTED"&&!comment)return;
    setBusy(item.id);setError("");
    try{
      const response=await fetch(`/api/session/approvals/${item.id}/decision`,{method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify({decision,comment})});
      const body=await response.json().catch(()=>({}));
      if(!response.ok)throw new Error(body.detail??"Decision could not be saved");
      await load();
    }catch(failure){setError(failure instanceof Error?failure.message:"Decision could not be saved");}
    finally{setBusy(undefined);}
  };

  const pending=items.filter(item=>item.status==="PENDING");
  return <main className="approvalPage">
    <header><Link href="/dashboard" className="brand"><span className="brandmark">F</span>FlowMind</Link><Link href="/workflows"><ArrowLeft/>Workflows</Link></header>
    <section>
      <div className="approvalTitle"><div><span>HUMAN-IN-THE-LOOP</span><h1>Approval inbox</h1><p>Review paused executions. Decisions are authenticated, audited, and resume through the durable worker queue.</p></div><ShieldCheck/></div>
      {error?<div className="approvalEmpty"><ShieldCheck/><strong>{error}</strong><p>Sign in to review approval requests for your workspace.</p><Link className="button primary" href="/sign-in">Sign in</Link></div>:loading?<div className="approvalEmpty">Loading approval requests…</div>:pending.length===0?<div className="approvalEmpty"><Check/><strong>No approvals are waiting</strong><p>New requests appear here when a workflow reaches a human approval node.</p></div>:<div className="approvalList">{pending.map(item=><article key={item.id}><div className="approvalIcon"><Clock3/></div><div><span>{item.assignedRole??"ASSIGNED USER"}</span><h2>{item.nodeId}</h2><p>Execution <code>{item.executionId}</code></p><small>Requested {new Date(item.createdAt).toLocaleString()} · expires {item.expiresAt?new Date(item.expiresAt).toLocaleString():"never"}</small></div><div className="approvalActions"><button disabled={busy===item.id} className="reject" onClick={()=>void decide(item,"REJECTED")}><X/>Reject</button><button disabled={busy===item.id} className="approve" onClick={()=>void decide(item,"APPROVED")}><Check/>Approve</button></div></article>)}</div>}
    </section>
  </main>;
}
