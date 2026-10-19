# FlowMind

FlowMind is a multi-tenant workflow automation and AI-agent platform. This repository is being built as a production-oriented modular control plane with isolated execution workers.

## Current usable vertical slice

The first slice includes a typed workflow schema, graph validation/topological ordering, a Spring Boot control-plane API, Flyway schema, transactional outbox records, a Next.js workflow dashboard/editor shell, a FastAPI AI-provider gateway, and a local PostgreSQL/Redis/RabbitMQ/MinIO stack.

The authenticated workflow/Groq vertical slice is live-tested, including condition branches, resumable human approvals, a separate worker process, stale-worker recovery, encrypted TOTP MFA, optional OIDC and distributed authentication throttling. The complete enterprise platform still has acceptance gates. See [PROJECT_STATUS.md](PROJECT_STATUS.md) and [REQUIREMENTS_TRACEABILITY.md](REQUIREMENTS_TRACEABILITY.md).

## Quick start

1. Copy `.env.example` to `.env`, set `GROQ_API_KEY`, and replace development-only values.
2. Start the application: `docker compose --profile app up -d --build`.
3. Open `http://localhost:3002/sign-up`, create a workspace, then open the workflow editor. Approval tasks are available at `http://localhost:3002/approvals`. Port 3002 avoids a local port-3000 conflict; override it with `FLOWMIND_WEB_PORT`.

For host-based development, run the API, AI service, and web app separately as before. Keep provider keys only in `.env`; never put them in browser code.

Default local URLs: web `http://localhost:3002`, API `http://localhost:8080`, AI `http://localhost:8081`, PostgreSQL `localhost:5433`, Redis `localhost:6380`, RabbitMQ `http://localhost:15672`. Object storage is optional: start it with `docker compose --profile storage up -d minio`.

Enterprise SSO is disabled by default. Provider registration, callback URLs and account-linking policy are documented in [docs/OIDC_CONFIGURATION.md](docs/OIDC_CONFIGURATION.md).

Run the local load baseline with `docker run --rm -v "${PWD}/tests/load:/scripts:ro" -e API_BASE_URL=http://host.docker.internal:8080 grafana/k6:latest run /scripts/smoke.js`.

## Safety

Local credentials are development-only. Production deployments must use a KMS/secrets manager, TLS, private networks, egress policy, signed artifacts, and non-default credentials.

