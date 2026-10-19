import { proxyWorkspace } from "@/lib/session-api";

export async function POST(_: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  return proxyWorkspace(`/workflows/${encodeURIComponent(id)}/publish`, { method: "POST" });
}
