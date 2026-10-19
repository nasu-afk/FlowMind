import { describe,expect,it } from "vitest";
import { parseRateLimit } from "./index.js";
describe("parseRateLimit",()=>{it("normalizes provider headers",()=>{const headers=new Headers({"x-ratelimit-limit":"100","x-ratelimit-remaining":"9","x-ratelimit-reset":"1893456000","retry-after":"3"});expect(parseRateLimit(headers)).toMatchObject({limit:100,remaining:9,retryAfterMs:3000});});});

