import http from "k6/http";
import { check, sleep } from "k6";

const apiBase = __ENV.API_BASE_URL || "http://host.docker.internal:8080";

export const options = {
  scenarios: {
    public_api_smoke: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || "20s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500"],
    checks: ["rate>0.99"],
  },
};

export default function () {
  const readiness = http.get(`${apiBase}/actuator/health/readiness`, { tags: { endpoint: "readiness" } });
  check(readiness, { "readiness is UP": (response) => response.status === 200 && response.json("status") === "UP" });

  const oidc = http.get(`${apiBase}/api/auth/oidc/status`, { tags: { endpoint: "oidc-status" } });
  check(oidc, { "OIDC status is available": (response) => response.status === 200 && typeof response.json("enabled") === "boolean" });
  sleep(0.2);
}
