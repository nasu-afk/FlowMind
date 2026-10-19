from __future__ import annotations

import hmac
import os
from enum import StrEnum
from typing import Any, AsyncIterator

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field


class Provider(StrEnum):
    GROQ = "groq"


class ChatMessage(BaseModel):
    role: str = Field(pattern="^(system|user|assistant|tool)$")
    content: str = Field(min_length=1, max_length=200_000)


class GenerateRequest(BaseModel):
    provider: Provider
    model: str = Field(min_length=1, max_length=200)
    messages: list[ChatMessage] = Field(min_length=1, max_length=200)
    max_tokens: int = Field(default=1024, ge=1, le=32_768)
    temperature: float = Field(default=0.2, ge=0, le=2)
    stream: bool = False
    response_format: dict[str, Any] | None = None


class ProviderStatus(BaseModel):
    provider: Provider
    configured: bool
    detail: str


class AgentPolicy(BaseModel):
    allowed_tools: set[str] = Field(default_factory=set, max_length=100)
    max_iterations: int = Field(default=8, ge=1, le=100)
    token_budget: int = Field(default=16_000, ge=1, le=10_000_000)
    require_approval_for_side_effects: bool = True


class ToolCallRequest(BaseModel):
    policy: AgentPolicy
    tool: str = Field(min_length=1, max_length=200)
    iteration: int = Field(ge=1)
    tokens_used: int = Field(ge=0)
    side_effecting: bool = False
    approval_granted: bool = False


class ToolCallDecision(BaseModel):
    allowed: bool
    reason: str


class ChunkRequest(BaseModel):
    text: str = Field(min_length=1, max_length=5_000_000)
    max_characters: int = Field(default=1600, ge=200, le=20_000)
    overlap_characters: int = Field(default=200, ge=0, le=5000)


class TextChunk(BaseModel):
    ordinal: int
    content: str
    start: int
    end: int


def authorize_tool_call(request: ToolCallRequest) -> ToolCallDecision:
    if request.tool not in request.policy.allowed_tools:
        return ToolCallDecision(allowed=False, reason="Tool is not granted to this agent")
    if request.iteration > request.policy.max_iterations:
        return ToolCallDecision(allowed=False, reason="Maximum agent iterations exceeded")
    if request.tokens_used >= request.policy.token_budget:
        return ToolCallDecision(allowed=False, reason="Agent token budget exhausted")
    if request.side_effecting and request.policy.require_approval_for_side_effects and not request.approval_granted:
        return ToolCallDecision(allowed=False, reason="Human approval is required for this side effect")
    return ToolCallDecision(allowed=True, reason="Tool call satisfies the explicit agent policy")


def chunk_text(request: ChunkRequest) -> list[TextChunk]:
    if request.overlap_characters >= request.max_characters:
        raise HTTPException(status_code=422, detail="Chunk overlap must be smaller than chunk size")
    normalized = request.text.replace("\r\n", "\n").replace("\r", "\n")
    chunks: list[TextChunk] = []
    start = 0
    while start < len(normalized):
        hard_end = min(len(normalized), start + request.max_characters)
        end = hard_end
        if hard_end < len(normalized):
            boundary = normalized.rfind("\n", start + request.max_characters // 2, hard_end)
            if boundary < 0:
                boundary = normalized.rfind(" ", start + request.max_characters // 2, hard_end)
            if boundary > start:
                end = boundary
        content = normalized[start:end].strip()
        if content:
            chunks.append(TextChunk(ordinal=len(chunks), content=content, start=start, end=end))
        if end >= len(normalized):
            break
        start = max(start + 1, end - request.overlap_characters)
    return chunks


class GroqAdapter:
    def __init__(self, api_key: str) -> None:
        self.api_key = api_key

    async def generate(self, request: GenerateRequest) -> dict[str, Any]:
        headers = {"Content-Type": "application/json"}
        headers["Authorization"] = f"Bearer {self.api_key}"
        payload = groq_payload(request, stream=False)
        async with httpx.AsyncClient(timeout=httpx.Timeout(90.0, connect=10.0), follow_redirects=False) as client:
            response = await client.post("https://api.groq.com/openai/v1/chat/completions", headers=headers, json=payload)
        if response.status_code >= 400:
            raise HTTPException(status_code=502, detail={"provider_status": response.status_code, "message": "Groq rejected the request"})
        return response.json()

    async def stream(self, request: GenerateRequest) -> AsyncIterator[str]:
        headers = {"Content-Type": "application/json", "Authorization": f"Bearer {self.api_key}"}
        payload = groq_payload(request, stream=True)
        async with httpx.AsyncClient(timeout=httpx.Timeout(90.0, connect=10.0), follow_redirects=False) as client:
            async with client.stream("POST", "https://api.groq.com/openai/v1/chat/completions", headers=headers, json=payload) as response:
                if response.status_code >= 400:
                    await response.aread()
                    raise HTTPException(status_code=502, detail={"provider_status": response.status_code, "message": "Groq rejected the streaming request"})
                async for line in response.aiter_lines():
                    if line.startswith("data:"):
                        yield f"{line}\n\n"


def groq_payload(request: GenerateRequest, stream: bool) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "model": request.model,
        "messages": [message.model_dump() for message in request.messages],
        "max_tokens": request.max_tokens,
        "temperature": request.temperature,
        "stream": stream,
    }
    if request.response_format is not None:
        payload["response_format"] = request.response_format
    return payload


def adapter_for(request: GenerateRequest) -> GroqAdapter:
    key = os.getenv("GROQ_API_KEY")
    if not key:
        raise HTTPException(status_code=424, detail="Groq connection is awaiting credentials")
    return GroqAdapter(key)


app = FastAPI(title="FlowMind AI Service", version="0.1.0")


def require_service_token(authorization: str | None = Header(default=None)) -> None:
    expected = os.getenv("FLOWMIND_AI_SERVICE_TOKEN", "")
    supplied = authorization.removeprefix("Bearer ") if authorization else ""
    if not expected or not supplied or not hmac.compare_digest(supplied, expected):
        raise HTTPException(status_code=401, detail="Valid service authentication is required")


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/v1/providers", response_model=list[ProviderStatus], dependencies=[Depends(require_service_token)])
async def providers() -> list[ProviderStatus]:
    return [
        ProviderStatus(provider=Provider.GROQ, configured=bool(os.getenv("GROQ_API_KEY")), detail="Ready" if os.getenv("GROQ_API_KEY") else "Awaiting credentials"),
    ]


@app.post("/v1/generate", dependencies=[Depends(require_service_token)])
async def generate(request: GenerateRequest) -> dict[str, Any]:
    if request.stream:
        raise HTTPException(status_code=400, detail="Use /v1/generate/stream for streaming")
    return await adapter_for(request).generate(request)


@app.post("/v1/generate/stream", dependencies=[Depends(require_service_token)])
async def generate_stream(request: GenerateRequest) -> StreamingResponse:
    return StreamingResponse(adapter_for(request).stream(request), media_type="text/event-stream")


@app.post("/v1/agents/authorize-tool", response_model=ToolCallDecision, dependencies=[Depends(require_service_token)])
async def authorize_agent_tool(request: ToolCallRequest) -> ToolCallDecision:
    return authorize_tool_call(request)


@app.post("/v1/knowledge/chunks", response_model=list[TextChunk], dependencies=[Depends(require_service_token)])
async def create_chunks(request: ChunkRequest) -> list[TextChunk]:
    return chunk_text(request)

