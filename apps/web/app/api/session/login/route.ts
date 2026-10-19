import { NextResponse } from "next/server";
import { apiBaseUrl } from "@/lib/config";
import { setSessionCookies } from "@/lib/session-api";

export async function POST(request: Request) {
  const response = await fetch(`${apiBaseUrl}/api/auth/login`, { method: "POST", headers: { "content-type": "application/json", "user-agent": request.headers.get("user-agent") ?? "FlowMind web", "x-forwarded-for": request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "" }, body: await request.text(), cache: "no-store" });
  const body = await response.json().catch(() => ({ detail: "Authentication service unavailable" }));
  if (!response.ok) return NextResponse.json(body, { status: response.status });
  const result = NextResponse.json({ userId: body.userId, organizationId: body.organizationId, workspaceId: body.workspaceId });
  setSessionCookies(result, body);
  return result;
}

