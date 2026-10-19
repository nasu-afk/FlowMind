import { proxyApi } from "@/lib/session-api";
export async function POST(){return proxyApi("/auth/mfa/totp/setup",{method:"POST"});}
