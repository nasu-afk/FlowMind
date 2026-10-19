import { proxyApi } from "@/lib/session-api";
export async function GET(){return proxyApi("/auth/mfa/status");}
