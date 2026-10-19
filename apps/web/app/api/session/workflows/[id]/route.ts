import { proxyWorkspace } from "@/lib/session-api";

export async function GET(_: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  return proxyWorkspace(`/workflows/${encodeURIComponent(id)}`);
}
export async function PUT(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  return proxyWorkspace(`/workflows/${encodeURIComponent(id)}`, { method: "PUT", headers: { "content-type": "application/json" }, body: await request.text() });
}
