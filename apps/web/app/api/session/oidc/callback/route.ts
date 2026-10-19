import { NextResponse } from "next/server";
import { apiBaseUrl } from "@/lib/config";
import { setSessionCookies } from "@/lib/session-api";

export async function GET(request:Request){
  const code=new URL(request.url).searchParams.get("code");
  if(!code)return NextResponse.redirect(new URL("/sign-in?error=oidc_missing_code",request.url));
  const response=await fetch(`${apiBaseUrl}/api/auth/oidc/exchange`,{method:"POST",headers:{"content-type":"application/json","user-agent":request.headers.get("user-agent")??"FlowMind web"},body:JSON.stringify({code}),cache:"no-store"});
  const body=await response.json().catch(()=>({}));
  if(!response.ok)return NextResponse.redirect(new URL("/sign-in?error=oidc_exchange_failed",request.url));
  const result=NextResponse.redirect(new URL("/dashboard",request.url));
  setSessionCookies(result,body);
  return result;
}
