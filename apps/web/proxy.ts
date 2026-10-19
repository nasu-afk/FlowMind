import { NextRequest, NextResponse } from "next/server";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

export function isCrossSiteRequest(origin: string | null, publicHost: string, fetchSite: string | null, production: boolean) {
  if (origin) {
    try { return new URL(origin).host !== publicHost; }
    catch { return true; }
  }
  return production && fetchSite === "cross-site";
}

export function proxy(request: NextRequest) {
  if (SAFE_METHODS.has(request.method)) return NextResponse.next();
  const fetchSite = request.headers.get("sec-fetch-site");
  const origin = request.headers.get("origin");
  const publicHost = process.env.FLOWMIND_PUBLIC_HOST || request.headers.get("host") || request.nextUrl.host;
  if (isCrossSiteRequest(origin, publicHost, fetchSite, process.env.NODE_ENV === "production")) {
    return NextResponse.json({ detail: "Cross-site request rejected" }, { status: 403 });
  }
  return NextResponse.next();
}

export const config = { matcher: ["/api/session/:path*", "/api/ai/:path*"] };
