import { NextResponse } from "next/server";
import { workspaceSession } from "@/lib/session-api";

export async function POST() {
  const session = await workspaceSession();
  if (!session.token || !session.workspaceId) {
    return NextResponse.json({ detail: "Session expired" }, { status: 401 });
  }
  return NextResponse.json({ workspaceId: session.workspaceId });
}
