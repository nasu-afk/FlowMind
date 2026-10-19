import { proxyWorkspace } from "@/lib/session-api";

export async function GET() {
  return proxyWorkspace("/executions");
}
