import { proxyWorkspace } from "@/lib/session-api";

export async function GET(_: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  return proxyWorkspace(`/executions/${encodeURIComponent(id)}`);
}
