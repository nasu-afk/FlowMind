import { proxyWorkspace } from "@/lib/session-api";

export async function GET(request: Request) {
  const status = new URL(request.url).searchParams.get("status");
  return proxyWorkspace(`/approvals${status ? `?status=${encodeURIComponent(status)}` : ""}`);
}
