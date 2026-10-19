import { describe, expect, it } from "vitest";
import { isCrossSiteRequest } from "./proxy";

describe("CSRF origin validation", () => {
  it("accepts the configured public host", () => {
    expect(isCrossSiteRequest("http://localhost:3002", "localhost:3002", "same-origin", true)).toBe(false);
  });

  it("rejects foreign and malformed origins", () => {
    expect(isCrossSiteRequest("https://evil.example", "localhost:3002", "cross-site", true)).toBe(true);
    expect(isCrossSiteRequest("not a URL", "localhost:3002", null, true)).toBe(true);
  });

  it("does not depend on forwarded host headers", () => {
    expect(isCrossSiteRequest("https://evil.example", "localhost:3002", null, true)).toBe(true);
  });
});
