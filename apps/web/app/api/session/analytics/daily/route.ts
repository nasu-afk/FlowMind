import { proxyWorkspace } from "@/lib/session-api";

export async function GET(request: Request) {
  const days = Math.max(1, Math.min(365, Number(new URL(request.url).searchParams.get("days") ?? 30) || 30));
  return proxyWorkspace(`/analytics/daily?days=${days}`);
}
