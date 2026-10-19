import { NextResponse } from "next/server";
import { apiBaseUrl } from "@/lib/config";
export async function GET(){const response=await fetch(`${apiBaseUrl}/api/auth/oidc/status`,{cache:"no-store"});return new NextResponse(await response.text(),{status:response.status,headers:{"content-type":"application/json"}});}
