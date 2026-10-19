import os
from fastapi.testclient import TestClient
from app.main import AgentPolicy, ChunkRequest, GenerateRequest, ToolCallRequest, app, authorize_tool_call, chunk_text, groq_payload


client = TestClient(app)
AUTH = {"Authorization": "Bearer test-service-token"}


def setup_module() -> None:
    os.environ["FLOWMIND_AI_SERVICE_TOKEN"] = "test-service-token"


def test_health() -> None:
    assert client.get("/health").json() == {"status": "ok"}


def test_missing_groq_credentials_is_explicit(monkeypatch) -> None:
    monkeypatch.delenv("GROQ_API_KEY", raising=False)
    response = client.post("/v1/generate", headers=AUTH, json={
        "provider": "groq", "model": "openai/gpt-oss-120b",
        "messages": [{"role": "user", "content": "Hello"}]
    })
    assert response.status_code == 424
    assert "Groq connection is awaiting credentials" == response.json()["detail"]


def test_non_groq_provider_is_rejected() -> None:
    response = client.post("/v1/generate", headers=AUTH, json={
        "provider": "openai", "model": "gpt-5-mini",
        "messages": [{"role": "user", "content": "Hello"}]
    })
    assert response.status_code == 422


def test_ai_routes_require_service_authentication() -> None:
    response = client.post("/v1/generate", json={
        "provider": "groq", "model": "openai/gpt-oss-120b",
        "messages": [{"role": "user", "content": "Hello"}]
    })
    assert response.status_code == 401


def test_stream_route_forwards_provider_events(monkeypatch) -> None:
    class FakeAdapter:
        async def stream(self, request):
            yield 'data: {"choices":[{"delta":{"content":"hello"}}]}\n\n'
            yield "data: [DONE]\n\n"

    monkeypatch.setattr("app.main.adapter_for", lambda request: FakeAdapter())
    response = client.post("/v1/generate/stream", headers=AUTH, json={
        "provider": "groq", "model": "openai/gpt-oss-120b",
        "messages": [{"role": "user", "content": "Hello"}], "stream": True
    })
    assert response.status_code == 200
    assert 'data: {"choices"' in response.text
    assert "data: [DONE]" in response.text


def test_structured_response_format_is_forwarded_to_groq() -> None:
    response_format = {
        "type": "json_schema",
        "json_schema": {"name": "result", "strict": True, "schema": {"type": "object"}},
    }
    request = GenerateRequest(
        provider="groq",
        model="openai/gpt-oss-120b",
        messages=[{"role": "user", "content": "Classify this"}],
        response_format=response_format,
    )
    assert groq_payload(request, stream=False)["response_format"] == response_format


def test_agent_tool_permissions_and_approval() -> None:
    policy = AgentPolicy(allowed_tools={"email.send"}, max_iterations=3, token_budget=100)
    denied = authorize_tool_call(ToolCallRequest(policy=policy, tool="email.send", iteration=1, tokens_used=10, side_effecting=True))
    assert not denied.allowed
    allowed = authorize_tool_call(ToolCallRequest(policy=policy, tool="email.send", iteration=1, tokens_used=10, side_effecting=True, approval_granted=True))
    assert allowed.allowed
    unknown = authorize_tool_call(ToolCallRequest(policy=policy, tool="database.drop", iteration=1, tokens_used=0))
    assert not unknown.allowed


def test_chunking_is_bounded_and_overlapping() -> None:
    chunks = chunk_text(ChunkRequest(text=("alpha beta gamma delta " * 100), max_characters=220, overlap_characters=20))
    assert len(chunks) > 1
    assert all(len(chunk.content) <= 220 for chunk in chunks)
    assert chunks[1].start < chunks[0].end

