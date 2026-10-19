import { NextResponse } from "next/server";
import { apiBaseUrl } from "@/lib/config";
import { setSessionCookies } from "@/lib/session-api";

const enabled = process.env.FLOWMIND_DEMO_ENABLED === "true";

export async function GET() {
  return NextResponse.json({ enabled });
}

export async function POST(request: Request) {
  const email=process.env.FLOWMIND_DEMO_EMAIL;
  const password=process.env.FLOWMIND_DEMO_PASSWORD;
  if(!enabled||!email||!password)return NextResponse.json({detail:"Demo sign-in is not enabled"},{status:404});
  const response=await fetch(`${apiBaseUrl}/api/auth/login`,{method:"POST",headers:{"content-type":"application/json","user-agent":request.headers.get("user-agent")??"FlowMind demo","x-forwarded-for":request.headers.get("x-forwarded-for")?.split(",")[0]?.trim()??""},body:JSON.stringify({email,password}),cache:"no-store"});
  const body=await response.json().catch(()=>({detail:"Demo authentication service unavailable"}));
  if(!response.ok)return NextResponse.json(body,{status:response.status});
  const result=NextResponse.json({userId:body.userId,organizationId:body.organizationId,workspaceId:body.workspaceId});
  setSessionCookies(result,body);
  return result;
}
