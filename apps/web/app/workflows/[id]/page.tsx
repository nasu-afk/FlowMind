"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  Background, Controls, Handle, MiniMap, Position, ReactFlow, addEdge,
  useEdgesState, useNodesState, useReactFlow, type Connection, type Edge,
  type Node, type NodeProps,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import {
  Braces, Check, ChevronDown, CircleHelp, Command, GitBranch,
  History, Home, Play, Plus, Search, Settings, Shapes, Sparkles, Users,
} from "lucide-react";

type JsonSchema = { type: string; required?: string[]; additionalProperties?: boolean; properties?: Record<string, { type: string; enum?: string[] }> };
type FlowData = { label: string; kind: string; detail: string; tone: string; instructions?: string; provider?: string; model?: string; responseSchema?: JsonSchema; temperature?: number; field?: string; operator?: string; value?: string; trueBranch?: string; falseBranch?: string; assignedRole?: string; expirationSeconds?: number; expirationPolicy?: string };
type Notice = { tone: "success" | "error" | "info"; title: string; body: string };

const classificationSchema: JsonSchema = {
  type: "object",
  required: ["intent", "urgency", "sentiment"],
  additionalProperties: false,
  properties: {
    intent: { type: "string" },
    urgency: { type: "string", enum: ["low", "medium", "high", "critical"] },
    sentiment: { type: "string" },
  },
};
const sampleInput = { subject: "Locked out of account", message: "I need urgent help signing in.", priority: "critical" };
const defaultInstructions = "Classify the incoming support request by intent, urgency, and sentiment. If priority is supplied, preserve it as urgency. Return only valid JSON matching the response schema.";
const initialNodes: Node<FlowData>[] = [
  { id: "trigger", type: "flowNode", position: { x: 90, y: 190 }, data: { label: "Ticket received", kind: "TRIGGER", detail: "Webhook · POST", tone: "mint" } },
  { id: "classify", type: "flowNode", position: { x: 390, y: 190 }, data: { label: "Classify request", kind: "AI MODEL", detail: "Groq · GPT-OSS 120B", tone: "violet", instructions: defaultInstructions, provider: "groq", model: "openai/gpt-oss-120b", responseSchema: classificationSchema, temperature: 0.2 } },
  { id: "route", type: "flowNode", position: { x: 700, y: 190 }, data: { label: "Route by priority", kind: "CONDITION", detail: "critical / standard", tone: "amber", field: "outputs.classify.urgency", operator: "equals", value: "critical", trueBranch: "critical", falseBranch: "standard" } },
  { id: "approve", type: "flowNode", position: { x: 1010, y: 80 }, data: { label: "Human approval", kind: "APPROVAL", detail: "Workspace owner", tone: "blue", assignedRole: "OWNER", expirationSeconds: 86400, expirationPolicy: "FAIL" } },
  { id: "respond", type: "flowNode", position: { x: 1010, y: 300 }, data: { label: "Draft response", kind: "AI MODEL", detail: "Groq · GPT-OSS 120B", tone: "violet", instructions: "Draft a concise, helpful response using the approved knowledge base.", provider: "groq", model: "openai/gpt-oss-120b", temperature: 0.2 } },
];
const initialEdges: Edge[] = [
  { id: "e1", source: "trigger", target: "classify", animated: true },
  { id: "e2", source: "classify", target: "route", animated: true },
  { id: "e3", source: "route", target: "approve", label: "critical" },
  { id: "e4", source: "route", target: "respond", label: "standard" },
];

const paletteItems = [
  { kind: "TRIGGER", label: "Webhook", detail: "Receive HTTP requests", tone: "mint", icon: Braces },
  { kind: "AI MODEL", label: "Generate text", detail: "Create with an AI model", tone: "violet", icon: Sparkles },
  { kind: "CONDITION", label: "Condition", detail: "Branch with rules", tone: "amber", icon: GitBranch },
  { kind: "APPROVAL", label: "Human approval", detail: "Pause for an authenticated decision", tone: "blue", icon: Users },
];
const groqModels = ["openai/gpt-oss-120b", "llama-3.3-70b-versatile"];

function resolvePath(path: string, input: Record<string, unknown>, outputs: Record<string, unknown>) {
  const source = path.startsWith("outputs.") ? outputs : input;
  const normalized = path.replace(/^(outputs|input)\./, "");
  return normalized.split(".").reduce<unknown>((value, key) => value && typeof value === "object" ? (value as Record<string, unknown>)[key] : undefined, source);
}

function compareCondition(actual: unknown, expected: string | undefined, operator: string | undefined) {
  switch (operator) {
    case "exists": return actual !== undefined && actual !== null;
    case "not_equals": return String(actual) !== String(expected);
    case "contains": return Array.isArray(actual) ? actual.includes(expected) : String(actual).includes(String(expected));
    case "gt": return Number(actual) > Number(expected);
    case "gte": return Number(actual) >= Number(expected);
    case "lt": return Number(actual) < Number(expected);
    case "lte": return Number(actual) <= Number(expected);
    default: return String(actual) === String(expected);
  }
}

function normalizeAiContent(content: string) {
  const unfenced = content.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
  try { return JSON.stringify(JSON.parse(unfenced), null, 2); } catch { return content; }
}

function FlowNode({ id, data, selected }: NodeProps<Node<FlowData>>) {
  const { deleteElements } = useReactFlow();
  return <div className={`editorNode ${data.tone} ${selected ? "selected" : ""}`}>
    <Handle type="target" position={Position.Left}/>
    <div className="nodeIcon">{data.kind === "AI MODEL" ? <Sparkles/> : data.kind === "CONDITION" ? <GitBranch/> : data.kind === "APPROVAL" ? <Users/> : <Braces/>}</div>
    <div><small>{data.kind}</small><strong>{data.label}</strong><span>{data.detail}</span></div>
    <button aria-label={`Delete ${data.label}`} title="Delete node" onClick={event=>{event.stopPropagation();void deleteElements({nodes:[{id}]});}}>×</button><Handle type="source" position={Position.Right}/>
  </div>;
}
const nodeTypes = { flowNode: FlowNode };

function CanvasToolbar({ focusSearch }: { focusSearch: () => void }) {
  const { zoomIn, zoomOut, getZoom } = useReactFlow();
  const [zoom, setZoom] = useState(100);
  const changeZoom = async (direction: "in" | "out") => {
    if (direction === "in") await zoomIn({ duration: 180 }); else await zoomOut({ duration: 180 });
    setZoom(Math.round(getZoom() * 100));
  };
  return <div className="canvasToolbar"><button onClick={focusSearch}><Command size={15}/> Command</button><span/><button aria-label="Zoom out" onClick={()=>changeZoom("out")}>−</button><b>{zoom}%</b><button aria-label="Zoom in" onClick={()=>changeZoom("in")}>+</button></div>;
}

export default function WorkflowEditor() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [workflowId, setWorkflowId] = useState(params.id);
  const [lockVersion, setLockVersion] = useState(0);
  const [loadingWorkflow, setLoadingWorkflow] = useState(true);
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<FlowData>>(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
  const [panel, setPanel] = useState(true);
  const [paletteOpen, setPaletteOpen] = useState(true);
  const [selectedId, setSelectedId] = useState("classify");
  const [query, setQuery] = useState("");
  const [tab, setTab] = useState<"Configure" | "Input" | "Output">("Configure");
  const [notice, setNotice] = useState<Notice | null>(null);
  const [result, setResult] = useState<string>("");
  const [running, setRunning] = useState(false);
  const [published, setPublished] = useState(false);
  const [saved, setSaved] = useState(true);
  const [advanced, setAdvanced] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);
  const selected = nodes.find(node => node.id === selectedId) ?? nodes[0];

  const apiGraph = useCallback(() => ({
    nodes: nodes.map(node => ({ id: node.id, type: node.data.kind === "AI MODEL" ? "ai.generate" : node.data.kind === "TRIGGER" ? "trigger.webhook" : node.data.kind === "CONDITION" ? "logic.condition" : node.data.kind === "APPROVAL" ? "approval.human" : "core.node", version: 1, sideEffecting: node.data.kind === "APPROVAL", configuration: { ...node.data, position: node.position } })),
    edges: edges.map(edge => ({ id: edge.id, source: edge.source, target: edge.target, branch: typeof edge.label === "string" ? edge.label : null })),
  }), [edges, nodes]);

  const persistDraft = useCallback(async () => {
    const creating = workflowId === "demo";
    const response = await fetch(creating ? "/api/session/workflows" : `/api/session/workflows/${workflowId}`, { method: creating ? "POST" : "PUT", headers: { "content-type": "application/json" }, body: JSON.stringify({ name: "Customer support triage", description: "Production support classification workflow", graph: apiGraph(), ...(!creating && { lockVersion }) }) });
    const body = await response.json().catch(() => ({}));
    if (response.status === 401) { router.replace("/sign-in?next=/workflows/demo"); throw new Error("Sign in required"); }
    if (!response.ok) throw new Error(body.detail ?? "The workflow could not be saved.");
    setWorkflowId(body.id); setLockVersion(body.lockVersion); setSaved(true);
    if (creating) window.history.replaceState(null, "", `/workflows/${body.id}`);
    return String(body.id);
  }, [apiGraph, lockVersion, router, workflowId]);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      if (params.id === "demo") { setLoadingWorkflow(false); setSaved(true); return; }
      const response = await fetch(`/api/session/workflows/${params.id}`, { cache: "no-store" });
      if (response.status === 401) { router.replace(`/sign-in?next=/workflows/${params.id}`); return; }
      const body = await response.json().catch(() => ({}));
      if (!response.ok) { setNotice({ tone: "error", title: "Workflow unavailable", body: body.detail ?? "The workflow could not be loaded." }); setLoadingWorkflow(false); return; }
      if (cancelled) return;
      setNodes(body.graph.nodes.map((node: { id: string; type: string; configuration: FlowData & { position?: { x: number; y: number } } }, index: number) => ({ id: node.id, type: "flowNode", position: node.configuration.position ?? { x: 100 + index * 260, y: 190 }, data: node.configuration })));
      setEdges(body.graph.edges.map((edge: { id: string; source: string; target: string; branch?: string }) => ({ ...edge, label: edge.branch, animated: !edge.branch })));
      setLockVersion(body.lockVersion); setPublished(body.publishedVersion != null); setSaved(true); setLoadingWorkflow(false);
    };
    void load();
    return () => { cancelled = true; };
  }, [params.id, router, setEdges, setNodes]);

  useEffect(() => {
    if (saved || loadingWorkflow) return;
    const timer = window.setTimeout(async () => {
      try { await persistDraft(); }
      catch (failure) { setNotice({ tone: "error", title: "Save failed", body: failure instanceof Error ? failure.message : "The workflow could not be saved." }); }
    }, 700);
    return () => window.clearTimeout(timer);
  }, [loadingWorkflow, persistDraft, saved]);

  useEffect(() => {
    const shortcut = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") { event.preventDefault(); searchRef.current?.focus(); }
      if (event.key === "Escape") setNotice(null);
    };
    window.addEventListener("keydown", shortcut);
    return () => window.removeEventListener("keydown", shortcut);
  }, []);

  const markChanged = () => { setSaved(false); setPublished(false); };
  const connect = useCallback((connection: Connection) => { setEdges(current => addEdge({ ...connection, animated: true }, current)); setSaved(false); setPublished(false); }, [setEdges]);
  const filteredItems = useMemo(() => paletteItems.filter(item => `${item.label} ${item.detail} ${item.kind}`.toLowerCase().includes(query.toLowerCase())), [query]);

  const addNode = (item: typeof paletteItems[number]) => {
    const id = `${item.kind.toLowerCase().replace(/\s+/g, "-")}-${Date.now()}`;
    const node: Node<FlowData> = { id, type: "flowNode", position: { x: 420 + (nodes.length % 3) * 270, y: 100 + (nodes.length % 4) * 120 }, data: { label: item.label, kind: item.kind, detail: item.kind === "AI MODEL" ? "Groq · GPT-OSS 120B" : item.detail, tone: item.tone, instructions: item.kind === "AI MODEL" ? defaultInstructions : undefined, provider: item.kind === "AI MODEL" ? "groq" : undefined, model: item.kind === "AI MODEL" ? "openai/gpt-oss-120b" : undefined, temperature: item.kind === "AI MODEL" ? 0.2 : undefined, ...(item.kind === "CONDITION" && {field:"priority",operator:"equals",value:"critical",trueBranch:"critical",falseBranch:"standard"}), ...(item.kind === "APPROVAL" && {assignedRole:"OWNER",expirationSeconds:86400,expirationPolicy:"FAIL"}) } };
    setNodes(current => [...current, node]); setSelectedId(id); setPanel(true); setTab("Configure"); markChanged();
    setNotice({ tone: "success", title: `${item.label} added`, body: "Drag it into place and connect its handles to another node." });
  };

  const updateSelected = (patch: Partial<FlowData>) => {
    setNodes(current => current.map(node => node.id === selectedId ? { ...node, data: { ...node.data, ...patch } } : node));
    markChanged();
  };

  const runAiNode = async (node: Node<FlowData>) => {
    const response = await fetch("/api/ai/generate", {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({
        provider: "groq",
        model: node.data.model ?? "openai/gpt-oss-120b",
        temperature: node.data.temperature ?? 0.2,
        max_tokens: 1024,
        ...(node.data.responseSchema && { response_format: { type: "json_schema", json_schema: { name: "flowmind_node_output", strict: true, schema: node.data.responseSchema } } }),
        messages: [
          { role: "system", content: node.data.instructions ?? defaultInstructions },
          { role: "user", content: JSON.stringify(sampleInput) },
        ],
      }),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      const detail = typeof body.detail === "string" ? body.detail : body.detail?.message;
      throw new Error(detail ?? "Groq request failed");
    }
    return normalizeAiContent(String(body.choices?.[0]?.message?.content ?? JSON.stringify(body, null, 2)));
  };

  const testNode = async () => {
    if (!selected) return;
    setRunning(true); setResult(""); setTab("Output");
    try {
      if (selected.data.kind === "AI MODEL") {
        setResult(await runAiNode(selected));
        setNotice({ tone: "success", title: "Groq node completed", body: `${selected.data.label} returned a live provider response.` });
      } else if (selected.data.kind === "CONDITION") {
        const actualValue = resolvePath(selected.data.field ?? "priority", sampleInput, { classify: { intent: "account access", urgency: "critical", sentiment: "negative" } });
        const matched = compareCondition(actualValue, selected.data.value, selected.data.operator);
        const selectedBranch = matched ? selected.data.trueBranch ?? "true" : selected.data.falseBranch ?? "false";
        setResult(JSON.stringify({ actualValue, matched, selectedBranch }, null, 2));
        setNotice({ tone: "success", title: "Condition evaluated", body: `${selected.data.label} selected the ${selectedBranch} branch from sample data.` });
      } else {
        setResult(JSON.stringify({ mode: "preview", nodeType: selected.data.kind, input: sampleInput }, null, 2));
        setNotice({ tone: "info", title: "Node preview", body: `${selected.data.label} is exercised by Test run; this preview does not claim backend execution.` });
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "AI request failed";
      setResult(`Error: ${message}`);
      setNotice({ tone: "error", title: "Groq test failed", body: message.includes("awaiting credentials") ? "Add GROQ_API_KEY to .env and restart the AI service." : message });
    } finally { setRunning(false); }
  };

  const testRun = async () => {
    if (nodes.length === 0 || !nodes.some(node => node.data.kind === "TRIGGER")) {
      setNotice({ tone: "error", title: "Workflow is not runnable", body: "Add at least one trigger node before starting a test run." }); return;
    }
    setRunning(true); setNotice({ tone: "info", title: "Queued execution", body: `The worker will execute ${nodes.length} nodes with sample ticket data.` });
    try {
      const activeWorkflowId = !saved || workflowId === "demo" ? await persistDraft() : workflowId;
      if (!published) {
        const publishResponse = await fetch(`/api/session/workflows/${activeWorkflowId}/publish`, { method: "POST" });
        if (!publishResponse.ok) { const error = await publishResponse.json().catch(() => ({})); throw new Error(error.detail ?? "Publish failed"); }
        setPublished(true);
      }
      const response = await fetch(`/api/session/workflows/${activeWorkflowId}/executions`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ idempotencyKey: crypto.randomUUID(), input: sampleInput }) });
      const started = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(started.detail ?? "Execution could not be queued");
      let execution = started;
      for (let attempt = 0; attempt < 60 && ["QUEUED", "RUNNING", "CREATED"].includes(execution.state); attempt += 1) {
        await new Promise(resolve => window.setTimeout(resolve, 1000));
        const status = await fetch(`/api/session/executions/${started.id}`, { cache: "no-store" });
        execution = await status.json();
      }
      setResult(JSON.stringify(execution.output ?? execution.error ?? execution, null, 2));
      if (execution.state === "WAITING") { setNotice({ tone: "info", title: "Approval required", body: "The execution is safely paused. Open the approval inbox to approve or reject it." }); return; }
      if (execution.state !== "COMPLETED") throw new Error(execution.error?.message ?? `Execution ended in ${execution.state}`);
      setNotice({ tone: "success", title: "Execution completed", body: `${nodes.length} nodes ran through the durable worker queue.` });
    } catch (error) {
      const message = error instanceof Error ? error.message : "AI request failed";
      setNotice({ tone: "error", title: "Test run stopped", body: message.includes("awaiting credentials") ? "Add GROQ_API_KEY to .env and restart the AI service." : message });
    } finally { setRunning(false); }
  };

  const publish = async () => {
    if (!nodes.some(node => node.data.kind === "TRIGGER")) { setNotice({ tone: "error", title: "Publish blocked", body: "This workflow needs a trigger." }); return; }
    let activeWorkflowId=workflowId;
    try { if(!saved || workflowId === "demo") activeWorkflowId=await persistDraft(); }
    catch(failure){setNotice({tone:"error",title:"Save failed",body:failure instanceof Error?failure.message:"The workflow could not be saved."});return;}
    const response = await fetch(`/api/session/workflows/${activeWorkflowId}/publish`, { method: "POST" });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) { setNotice({ tone: "error", title: "Publish failed", body: body.detail ?? "The workflow could not be published." }); return; }
    setPublished(true);
    setNotice({ tone: "success", title: "Workflow published", body: `Immutable version ${body.version} is active and ready for execution.` });
  };

  const restore = () => {
    if (workflowId === "demo") { setNotice({ tone: "info", title: "Not saved yet", body: "Make a change to create this workflow in your workspace." }); return; }
    window.location.reload();
  };

  return <div className="appShell">
    <aside className="sidebar"><Link href="/" className="brand compact"><span className="brandmark">F</span></Link><div className="sideGroup"><Link className="sideLink" title="Home" href="/dashboard"><Home/></Link><button className="current" title="Workflows"><Shapes/></button><Link className="sideLink" title="Approvals" href="/approvals"><Users/></Link></div><div className="sideBottom"><button onClick={()=>setNotice({tone:"info",title:"Keyboard shortcuts",body:"Ctrl/⌘ + K searches nodes. Drag nodes and connect their handles."})}><CircleHelp/></button><button onClick={()=>setNotice({tone:"info",title:"Workspace settings",body:"Workflow data is stored in PostgreSQL through the authenticated API."})}><Settings/></button><div className="avatar">HK</div></div></aside>
    <main className="workspace"><header className="editorHeader"><div className="crumb"><span>Workflows</span><b>/</b><strong>Customer support triage</strong><button onClick={()=>setNotice({tone:"info",title:"Backend workflow",body:"Drafts, versions, and executions are stored in your authenticated workspace."})}><ChevronDown size={14}/></button><i className={published ? "published" : ""}>{published ? "Published" : "Draft"}</i></div><div className="headerActions"><button className="iconButton" aria-label="Reload saved workflow" onClick={restore}><History/></button><button className="runButton" disabled={running||loadingWorkflow||(!saved&&workflowId!=="demo")} onClick={testRun}><Play size={15}/> {running ? "Running…" : "Test run"}</button><button className="publishButton" disabled={loadingWorkflow||(!saved&&workflowId!=="demo")} onClick={()=>void publish()}>{published ? <><Check size={14}/> Published</> : "Publish"}</button></div></header>
      <div className="editorBody">{paletteOpen && <aside className="palette"><div className="paletteHead"><strong>Nodes</strong><button aria-label="Close node palette" onClick={()=>setPaletteOpen(false)}>×</button></div><label className="search"><Search/><input ref={searchRef} value={query} onChange={event=>setQuery(event.target.value)} aria-label="Search nodes" placeholder="Search nodes"/><kbd>⌘ K</kbd></label><div className="category"><small>ADD NODE</small>{filteredItems.map(item=><button className="paletteItem" key={item.label} onClick={()=>addNode(item)}><span><item.icon/></span><div><strong>{item.label}</strong><small>{item.detail}</small></div><Plus/></button>)}{filteredItems.length===0&&<p className="emptySearch">No matching nodes</p>}</div><div className="category"><small>BROWSE</small>{[["Triggers","trigger"],["Logic","condition"],["AI models","ai model"],["Approvals","approval"]].map(([category,filter])=><button className="browse" key={category} onClick={()=>setQuery(filter)}>{category}<span>›</span></button>)}</div></aside>}
        <section className="canvas"><ReactFlow nodes={nodes} edges={edges} onNodesChange={changes=>{onNodesChange(changes); if(changes.some(change=>change.type==="remove"||change.type==="add"||change.type==="replace"||(change.type==="position"&&change.dragging===false))) markChanged();}} onEdgesChange={changes=>{onEdgesChange(changes); if(changes.some(change=>change.type!=="select")) markChanged();}} onConnect={connect} onNodeClick={(_,node)=>{setSelectedId(node.id);setPanel(true);setTab("Configure");}} nodeTypes={nodeTypes} fitView colorMode="light"><CanvasToolbar focusSearch={()=>{setPaletteOpen(true);window.setTimeout(()=>searchRef.current?.focus(),0);}}/><Background color="#d9dfdd" gap={22}/><Controls/><MiniMap pannable zoomable nodeColor="#a6b8b0"/></ReactFlow><div className="saveStatus"><span className={saved ? "" : "saving"}/> {workflowId === "demo" && saved ? "Unsaved draft — edit to create" : saved ? "All changes saved to workspace" : "Saving to workspace…"}</div>{!paletteOpen&&<button className="openPalette" onClick={()=>setPaletteOpen(true)}><Plus/> Nodes</button>}</section>
        {panel && selected && <aside className="inspector"><div className="inspectorHead"><div className={`nodeIcon ${selected.data.tone}`}><Sparkles/></div><div><small>{selected.data.kind}</small><strong>{selected.data.label}</strong></div><button aria-label="Close inspector" onClick={()=>setPanel(false)}>×</button></div><div className="tabs">{(["Configure","Input","Output"] as const).map(name=><button className={tab===name?"active":""} onClick={()=>setTab(name)} key={name}>{name}</button>)}</div>
          {tab==="Configure"&&<div className="form"><label>Node name<input value={selected.data.label} onChange={event=>updateSelected({label:event.target.value})}/></label>{selected.data.kind==="AI MODEL"&&<><label>Model provider<input value="Groq" disabled/></label><label>Model<select value={selected.data.model??groqModels[0]} onChange={event=>updateSelected({provider:"groq",model:event.target.value,detail:`Groq · ${event.target.value}`})}>{groqModels.map(value=><option value={value} key={value}>{value}</option>)}</select></label><label>Instructions<textarea rows={6} value={selected.data.instructions??""} onChange={event=>updateSelected({instructions:event.target.value})}/></label><label>Response schema<div className="codeBox"><code>{selected.data.responseSchema ? JSON.stringify(selected.data.responseSchema, null, 2) : "No structured response schema"}</code></div></label></>}{selected.data.kind==="CONDITION"&&<><label>Input field<input value={selected.data.field??""} placeholder="priority or outputs.classify.urgency" onChange={event=>updateSelected({field:event.target.value})}/></label><label>Operator<select value={selected.data.operator??"equals"} onChange={event=>updateSelected({operator:event.target.value})}><option value="equals">Equals</option><option value="not_equals">Does not equal</option><option value="contains">Contains</option><option value="exists">Exists</option><option value="gt">Greater than</option><option value="gte">Greater than or equal</option><option value="lt">Less than</option><option value="lte">Less than or equal</option></select></label><label>Compare with<input value={selected.data.value??""} onChange={event=>updateSelected({value:event.target.value})}/></label><label>Matching branch<input value={selected.data.trueBranch??"critical"} onChange={event=>updateSelected({trueBranch:event.target.value,detail:`${event.target.value} / ${selected.data.falseBranch??"standard"}`})}/></label><label>Fallback branch<input value={selected.data.falseBranch??"standard"} onChange={event=>updateSelected({falseBranch:event.target.value,detail:`${selected.data.trueBranch??"critical"} / ${event.target.value}`})}/></label></>}{selected.data.kind==="APPROVAL"&&<><label>Assigned role<select value={selected.data.assignedRole??"OWNER"} onChange={event=>updateSelected({assignedRole:event.target.value,detail:`Workspace ${event.target.value.toLowerCase()}`})}><option value="OWNER">Owner</option><option value="ADMIN">Admin</option><option value="EDITOR">Editor</option><option value="OPERATOR">Operator</option></select></label><label>Expires after (seconds)<input type="number" min="60" value={selected.data.expirationSeconds??86400} onChange={event=>updateSelected({expirationSeconds:Number(event.target.value)})}/></label><label>On expiration<select value={selected.data.expirationPolicy??"FAIL"} onChange={event=>updateSelected({expirationPolicy:event.target.value})}><option value="FAIL">Fail execution</option><option value="CONTINUE">Continue workflow</option></select></label></>}<button className={`advanced ${advanced?"open":""}`} onClick={()=>setAdvanced(value=>!value)}><span>Advanced options</span><ChevronDown/></button>{advanced&&<div className="advancedFields">{selected.data.kind==="AI MODEL"?<label>Temperature <b>{(selected.data.temperature??0.2).toFixed(1)}</b><input aria-label="Temperature" type="range" min="0" max="1" step="0.1" value={selected.data.temperature??0.2} onChange={event=>updateSelected({temperature:Number(event.target.value)})}/></label>:<p>Advanced runtime settings are not available for this node type.</p>}</div>}</div>}
          {tab==="Input"&&<div className="tabContent"><small>SAMPLE INPUT</small><pre>{JSON.stringify(sampleInput, null, 2)}</pre></div>}
          {tab==="Output"&&<div className="tabContent"><small>LATEST TEST OUTPUT</small>{running?<div className="loadingLine">Running node…</div>:result?<pre>{result}</pre>:<p>Run this node to inspect its structured output.</p>}</div>}
          <div className="testNode"><button disabled={running} onClick={testNode}><Play/> {running ? "Testing…" : "Test this node"}</button></div></aside>}
      </div>
    </main>
    {notice&&<div className={`notice ${notice.tone}`} role="status"><button aria-label="Dismiss notification" onClick={()=>setNotice(null)}>×</button><strong>{notice.title}</strong><span>{notice.body}</span></div>}
  </div>;
}
