import { NextResponse } from "next/server";
import { publicApiBaseUrl } from "@/lib/config";
export async function GET(){return NextResponse.redirect(`${publicApiBaseUrl.replace(/\/$/, "")}/oauth2/authorization/enterprise`);}
