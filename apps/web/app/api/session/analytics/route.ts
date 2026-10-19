import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { apiBaseUrl } from "@/lib/config";

function claim(token: string, name: string): string | undefined {
  try { return JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString("utf8"))[name]; } catch { return undefined; }
}
export async function GET() {
  const token = (await cookies()).get("flowmind_access")?.value;
  const workspaceId = token ? claim(token, "workspace_id") : undefined;
  if (!token || !workspaceId) return NextResponse.json({ detail: "Sign in required" }, { status: 401 });
  const response = await fetch(`${apiBaseUrl}/api/workspaces/${workspaceId}/analytics/summary`, { headers: { authorization: `Bearer ${token}` }, cache: "no-store" });
  return new NextResponse(await response.text(), { status: response.status, headers: { "content-type": "application/json" } });
}

