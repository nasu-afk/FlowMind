import { NextRequest, NextResponse } from "next/server";
import { workspaceSession } from "@/lib/session-api";

const aiBaseUrl = process.env.FLOWMIND_AI_URL ?? "http://localhost:8081";

export async function POST(request: NextRequest) {
  const session = await workspaceSession();
  if (!session.token || !session.workspaceId) {
    return NextResponse.json({ detail: "Sign in required" }, { status: 401 });
  }
  const serviceToken = process.env.FLOWMIND_AI_SERVICE_TOKEN;
  if (!serviceToken) {
    return NextResponse.json({ detail: "AI service authentication is not configured" }, { status: 503 });
  }
  const response = await fetch(`${aiBaseUrl}/v1/generate`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${serviceToken}` },
    body: await request.text(),
    cache: "no-store",
  }).catch(() => null);

  if (!response) {
    return NextResponse.json({ detail: "FlowMind AI service is unavailable" }, { status: 503 });
  }

  const body = await response.text();
  return new NextResponse(body, {
    status: response.status,
    headers: { "content-type": response.headers.get("content-type") ?? "application/json" },
  });
}
