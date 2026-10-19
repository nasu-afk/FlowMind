import type { ZodType } from "zod";

export type AuthStrategy = "none"|"api-key"|"basic"|"oauth2";
export interface ConnectorContext { workspaceId:string; executionId:string; nodeId:string; attempt:number; idempotencyKey:string; signal:AbortSignal; }
export interface ConnectorResult<T> { data:T; externalRequestId?:string; rateLimit?:RateLimit; }
export interface RateLimit { limit?:number; remaining?:number; resetAt?:Date; retryAfterMs?:number; }
export interface ConnectorDefinition<I,O> {
  readonly id:string; readonly version:number; readonly displayName:string; readonly auth:AuthStrategy;
  readonly inputSchema:ZodType<I>; readonly outputSchema:ZodType<O>; readonly sideEffecting:boolean;
  execute(input:I, context:ConnectorContext, credential:Readonly<Record<string,string>>|null):Promise<ConnectorResult<O>>;
}
export class ConnectorError extends Error {
  constructor(message:string,readonly code:string,readonly retryable:boolean,readonly retryAfterMs?:number){super(message);this.name="ConnectorError";}
}
export function parseRateLimit(headers:Headers,now=new Date()):RateLimit {
  const number=(name:string)=>{const value=headers.get(name);return value===null?undefined:Number(value)};
  const retry=number("retry-after"); const reset=number("x-ratelimit-reset");
  return { limit:number("x-ratelimit-limit"),remaining:number("x-ratelimit-remaining"),resetAt:Number.isFinite(reset)?new Date((reset as number)*1000):undefined,retryAfterMs:Number.isFinite(retry)?(retry as number)*1000:undefined };
}

