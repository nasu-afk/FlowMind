import { cookies, headers } from "next/headers";
import { NextResponse } from "next/server";
import { apiBaseUrl } from "@/lib/config";

type Session = { token?: string; workspaceId?: string };
type TokenResponse = {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
};

function claim(token: string, name: string): string | undefined {
  try { return JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString("utf8"))[name]; }
  catch { return undefined; }
}

function expiresSoon(token: string): boolean {
  const expiresAt = Number(claim(token, "exp"));
  return !Number.isFinite(expiresAt) || expiresAt * 1000 <= Date.now() + 30_000;
}

function sessionCookieOptions() {
  return { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax" as const, path: "/" };
}

export function setSessionCookies(response: NextResponse, tokens: TokenResponse) {
  response.cookies.set("flowmind_access", tokens.accessToken, { ...sessionCookieOptions(), expires: new Date(tokens.accessTokenExpiresAt) });
  response.cookies.set("flowmind_refresh", tokens.refreshToken, { ...sessionCookieOptions(), sameSite: "strict", path: "/api/session", maxAge: 30 * 24 * 60 * 60 });
}

async function refreshSession(): Promise<Session> {
  const store = await cookies();
  const requestHeaders = await headers();
  const refreshToken = store.get("flowmind_refresh")?.value;
  if (!refreshToken) return {};
  const response = await fetch(`${apiBaseUrl}/api/auth/refresh`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "user-agent": requestHeaders.get("user-agent") ?? "FlowMind web session",
      "x-forwarded-for": requestHeaders.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "",
    },
    body: JSON.stringify({ refreshToken }),
    cache: "no-store",
  }).catch(() => null);
  if (!response?.ok) return {};
  const tokens = await response.json() as TokenResponse;
  store.set("flowmind_access", tokens.accessToken, { ...sessionCookieOptions(), expires: new Date(tokens.accessTokenExpiresAt) });
  store.set("flowmind_refresh", tokens.refreshToken, { ...sessionCookieOptions(), sameSite: "strict", path: "/api/session", maxAge: 30 * 24 * 60 * 60 });
  return { token: tokens.accessToken, workspaceId: claim(tokens.accessToken, "workspace_id") };
}

export async function workspaceSession(): Promise<Session> {
  const token = (await cookies()).get("flowmind_access")?.value;
  if (!token || expiresSoon(token)) return refreshSession();
  return { token, workspaceId: claim(token, "workspace_id") };
}

export async function proxyWorkspace(path: string, init: RequestInit = {}) {
  const { token, workspaceId } = await workspaceSession();
  if (!token || !workspaceId) return NextResponse.json({ detail: "Sign in required" }, { status: 401 });
  const response = await fetch(`${apiBaseUrl}/api/workspaces/${workspaceId}${path}`, {
    ...init,
    headers: { ...init.headers, authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  const text = await response.text();
  return new NextResponse(text || null, { status: response.status, headers: text ? { "content-type": response.headers.get("content-type") ?? "application/json" } : undefined });
}

export async function proxyApi(path: string, init: RequestInit = {}) {
  const { token } = await workspaceSession();
  if (!token) return NextResponse.json({ detail: "Sign in required" }, { status: 401 });
  const response = await fetch(`${apiBaseUrl}/api${path}`, {
    ...init,
    headers: { ...init.headers, authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  const text = await response.text();
  return new NextResponse(text || null, { status: response.status, headers: text ? { "content-type": response.headers.get("content-type") ?? "application/json" } : undefined });
}
