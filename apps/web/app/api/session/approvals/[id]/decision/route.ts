import { proxyApi } from "@/lib/session-api";

export async function POST(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  return proxyApi(`/approvals/${encodeURIComponent(id)}/decision`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: await request.text(),
  });
}
