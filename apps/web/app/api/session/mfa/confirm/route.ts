import { proxyApi } from "@/lib/session-api";
export async function POST(request:Request){return proxyApi("/auth/mfa/totp/confirm",{method:"POST",headers:{"content-type":"application/json"},body:await request.text()});}
