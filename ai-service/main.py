from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import httpx
import os

app = FastAPI(title="LégyMérnök AI Service")

OLLAMA_URL = os.getenv("OLLAMA_URL", "http://ollama:11434")
EMBED_MODEL = os.getenv("EMBED_MODEL", "nomic-embed-text")
CHAT_MODEL = os.getenv("CHAT_MODEL", "gemma3:8b-q4_K_M")


class EmbedRequest(BaseModel):
    text: str


class EmbedResponse(BaseModel):
    embedding: list[float]
    model: str


class GenerateRequest(BaseModel):
    prompt: str
    context: list[str] = []
    model: str | None = None
    system_prompt: str | None = None


class GenerateResponse(BaseModel):
    response: str
    model: str


@app.get("/health")
async def health():
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            r = await client.get(f"{OLLAMA_URL}/api/tags")
            r.raise_for_status()
        return {"status": "ok", "ollama": "reachable"}
    except Exception as e:
        return {"status": "degraded", "ollama": str(e)}


@app.post("/embed", response_model=EmbedResponse)
async def embed(req: EmbedRequest):
    async with httpx.AsyncClient(timeout=60) as client:
        try:
            r = await client.post(
                f"{OLLAMA_URL}/api/embeddings",
                json={"model": EMBED_MODEL, "prompt": req.text},
            )
            r.raise_for_status()
        except httpx.HTTPStatusError as e:
            raise HTTPException(status_code=502, detail=f"Ollama error: {e.response.text}")
        except httpx.RequestError as e:
            raise HTTPException(status_code=503, detail=f"Ollama unreachable: {str(e)}")

    data = r.json()
    return EmbedResponse(embedding=data["embedding"], model=EMBED_MODEL)


@app.post("/generate", response_model=GenerateResponse)
async def generate(req: GenerateRequest):
    model = req.model or CHAT_MODEL
    context_block = "\n\n".join(req.context)
    prompt = f"Kontextus:\n{context_block}\n\nKérdés: {req.prompt}" if context_block else req.prompt

    async with httpx.AsyncClient(timeout=600) as client:
        try:
            payload: dict = {"model": model, "prompt": prompt, "stream": False}
            if req.system_prompt:
                payload["system"] = req.system_prompt
            r = await client.post(f"{OLLAMA_URL}/api/generate", json=payload)
            r.raise_for_status()
        except httpx.HTTPStatusError as e:
            raise HTTPException(status_code=502, detail=f"Ollama error: {e.response.text}")
        except httpx.RequestError as e:
            raise HTTPException(status_code=503, detail=f"Ollama unreachable: {str(e)}")

    return GenerateResponse(response=r.json()["response"], model=model)
