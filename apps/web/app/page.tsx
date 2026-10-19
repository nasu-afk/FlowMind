import Link from "next/link";
import { ArrowRight, Braces, CheckCircle2, GitBranch, ShieldCheck, Sparkles } from "lucide-react";

const capabilities = [
  [GitBranch, "Visual orchestration", "Compose webhook triggers, AI steps, explicit condition branches, and human approvals."],
  [Sparkles, "Schema-bound Groq AI", "Run Groq models through a private service boundary with structured outputs and durable execution traces."],
  [ShieldCheck, "Governed by default", "Tenant boundaries, immutable versions, auditable decisions, MFA, and isolated workers are structural."],
];

export default function LandingPage() {
  return <main className="landing">
    <nav className="topnav"><Link className="brand" href="/"><span className="brandmark">F</span>FlowMind</Link><div className="navlinks"><a href="#platform">Platform</a><Link href="/settings/security">Security</Link><Link href="/workflows">Workflows</Link></div><Link className="button ghost" href="/workflows">Open workspace</Link></nav>
    <section className="hero"><div className="eyebrow"><span className="pulse"/>Execution intelligence for serious teams</div><h1>Automation that<br/><em>explains itself.</em></h1><p>Design AI-powered workflows, ship them with confidence, and see every decision from trigger to outcome.</p><div className="heroActions"><Link className="button primary" href="/workflows/demo">Build a workflow <ArrowRight size={17}/></Link><a className="button text" href="#platform">Explore the platform</a></div><div className="trustline"><CheckCircle2 size={15}/> Versioned, observable, and built for human control</div></section>
    <section className="productFrame" id="platform"><div className="windowbar"><span/><span/><span/><small>Support triage · Draft</small></div><div className="miniApp"><aside><div className="miniLogo">FM</div>{["Overview","Workflows","Approvals","Executions","Security"].map((x,i)=><div className={i===1?"miniNav active":"miniNav"} key={x}>{x}</div>)}</aside><div className="miniCanvas"><div className="canvasTitle"><div><small>WORKFLOW PREVIEW</small><strong>Customer support triage</strong></div><span className="previewBadge">Published version</span></div><div className="flowPreview"><div className="flowNode trigger"><Braces/> <span><small>TRIGGER</small>New ticket</span></div><i className="line one"/><div className="flowNode ai"><Sparkles/> <span><small>AI</small>Classify intent</span></div><i className="line two"/><div className="flowNode branch"><GitBranch/> <span><small>LOGIC</small>Route by urgency</span></div></div></div></div></section>
    <section className="capabilities">{capabilities.map(([Icon,title,text])=><article key={String(title)}><Icon size={22}/><h2>{String(title)}</h2><p>{String(text)}</p></article>)}</section>
    <footer><span>© 2026 FlowMind</span><span>Built for accountable automation.</span></footer>
  </main>;
}

