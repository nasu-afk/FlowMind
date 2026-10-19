import { proxyWorkspace } from "@/lib/session-api";

export async function POST(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  return proxyWorkspace(`/workflows/${encodeURIComponent(id)}/executions`, { method: "POST", headers: { "content-type": "application/json" }, body: await request.text() });
}
