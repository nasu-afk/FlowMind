import { proxyWorkspace } from "@/lib/session-api";

export async function GET() { return proxyWorkspace("/workflows"); }
export async function POST(request: Request) {
  return proxyWorkspace("/workflows", { method: "POST", headers: { "content-type": "application/json" }, body: await request.text() });
}
