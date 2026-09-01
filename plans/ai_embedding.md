# AI Search & Embedding — Implementation Plan

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2026-06-20 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

## Goal

Semantic search over the platform's content (missions, star systems, group descriptions) using
a local LLM and a vector database — no external API key, no network calls out.

---

## Architecture overview

```
User
    │
    ▼
Spring Boot (/api/search)
    │
    ├─► PostgreSQL + pgvector   ← embedding index
    │       (similarity search)
    │
    └─► ai-service (FastAPI)    ← Python microservice
            │
            ▼
        Ollama container
        (embedding + generation)
```

### Components

| Component | Technology | Port | Role |
|---|---|---|---|
| `ollama` | `ollama/ollama` Docker | 11434 | Embedding + LLM inference |
| `ai-service` | Python 3.12 + FastAPI | 8081 | Embedding API, search |
| `postgres` | PostgreSQL 16 + pgvector | 5432 | Vector storage + querying |
| `backend` | Spring Boot | 8080 | Orchestration, auth |

---

## 1. Ollama Docker container

The model files live on the Windows filesystem (`C:\Users\<user>\.ollama`).

```yaml
# docker-compose.yml addition
ollama:
  image: ollama/ollama
  volumes:
    - /mnt/c/Users/<username>/.ollama:/root/.ollama
  ports:
    - "11434:11434"
  networks:
    - legymernok-net
```

### Model recommendation

| Task | Model | Why |
|---|---|---|
| Embedding | `nomic-embed-text` | Small, fast, good quality |
| Search + answer | `gemma3:8b-q4_K_M` | Fast, 8B params is enough |
| Detailed answer | `llama2:13b-q4_K_M` | Better quality, slower |

`gemma3` and `llama2` are already downloaded. `nomic-embed-text` still needs pulling:
```bash
# inside the ollama container:
ollama pull nomic-embed-text
```

---

## 2. pgvector setup

New Flyway migration (`V_ai__add_embeddings.sql`):

```sql
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE mission
  ADD COLUMN IF NOT EXISTS content_embedding vector(768);

ALTER TABLE star_system
  ADD COLUMN IF NOT EXISTS content_embedding vector(768);

ALTER TABLE mission_group
  ADD COLUMN IF NOT EXISTS content_embedding vector(768);

CREATE INDEX ON mission USING ivfflat (content_embedding vector_cosine_ops)
  WITH (lists = 100);
```

`nomic-embed-text` produces a 768-dimensional vector. OpenAI's ada-002 would need 1536 — if you
switch models, the dimension changes too.

---

## 3. ai-service (Python FastAPI)

### Directory structure

```
ai-service/
├── Dockerfile
├── requirements.txt
└── main.py
```

### `requirements.txt`

```
fastapi==0.115.0
uvicorn==0.30.0
httpx==0.27.0
pydantic==2.8.0
asyncpg==0.29.0        # for pgvector queries
psycopg[binary]==3.2.0
pgvector==0.3.2
```

### `main.py` — the main API

```python
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import httpx
import os

app = FastAPI()

OLLAMA_URL = os.getenv("OLLAMA_URL", "http://ollama:11434")
EMBED_MODEL = os.getenv("EMBED_MODEL", "nomic-embed-text")
CHAT_MODEL = os.getenv("CHAT_MODEL", "gemma3:8b-q4_K_M")

class EmbedRequest(BaseModel):
    text: str

class EmbedResponse(BaseModel):
    embedding: list[float]

class SearchRequest(BaseModel):
    query: str
    top_k: int = 5

class GenerateRequest(BaseModel):
    prompt: str
    context: list[str] = []

@app.post("/embed", response_model=EmbedResponse)
async def embed(req: EmbedRequest):
    async with httpx.AsyncClient(timeout=30) as client:
        r = await client.post(
            f"{OLLAMA_URL}/api/embeddings",
            json={"model": EMBED_MODEL, "prompt": req.text},
        )
        r.raise_for_status()
        return {"embedding": r.json()["embedding"]}

@app.post("/generate")
async def generate(req: GenerateRequest):
    context_block = "\n\n".join(req.context)
    full_prompt = f"Context:\n{context_block}\n\nQuestion: {req.prompt}" if context_block else req.prompt
    async with httpx.AsyncClient(timeout=120) as client:
        r = await client.post(
            f"{OLLAMA_URL}/api/generate",
            json={"model": CHAT_MODEL, "prompt": full_prompt, "stream": False},
        )
        r.raise_for_status()
        return {"response": r.json()["response"]}

@app.get("/health")
async def health():
    return {"status": "ok"}
```

### `Dockerfile`

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY main.py .
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8081"]
```

### docker-compose addition

```yaml
ai-service:
  build: ./ai-service
  ports:
    - "8081:8081"
  environment:
    - OLLAMA_URL=http://ollama:11434
    - EMBED_MODEL=nomic-embed-text
    - CHAT_MODEL=gemma3:8b-q4_K_M
    - DATABASE_URL=postgresql://postgres:postgres@postgres:5432/legymernok
  depends_on:
    - ollama
    - postgres
  networks:
    - legymernok-net
```

---

## 4. Spring Boot integration

### Search flow

```
GET /api/search?q=electric+circuit
    │
    ├─ 1. AI service: POST /embed { text: q } → vector
    │
    ├─ 2. PostgreSQL: SELECT id, name, 'MISSION' as type,
    │       1 - (content_embedding <=> $1) AS similarity
    │    FROM mission
    │    WHERE content_embedding IS NOT NULL
    │    ORDER BY similarity DESC LIMIT 5
    │
    └─ 3. Returns: [{id, name, type, similarity, description}]
```

### New service class (Spring)

```java
@Service
public class SemanticSearchService {

    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbc;
    private final String aiServiceUrl;

    public List<SearchResult> search(String query, int topK) {
        float[] embedding = getEmbedding(query);
        return searchByVector(embedding, topK);
    }

    private float[] getEmbedding(String text) {
        var response = restTemplate.postForObject(
            aiServiceUrl + "/embed",
            Map.of("text", text),
            EmbedResponse.class
        );
        return response.embedding();
    }

    private List<SearchResult> searchByVector(float[] vector, int topK) {
        String pgVector = Arrays.toString(vector).replace("[", "[").replace("]", "]");
        return jdbc.query(
            """
            SELECT id, name, 'MISSION' as type, description_markdown as description,
                   1 - (content_embedding <=> ?::vector) AS similarity
            FROM mission
            WHERE content_embedding IS NOT NULL
            ORDER BY similarity DESC LIMIT ?
            """,
            (rs, i) -> new SearchResult(rs.getString("id"), rs.getString("name"),
                                        rs.getString("type"), rs.getString("description"),
                                        rs.getDouble("similarity")),
            pgVector, topK
        );
    }
}
```

### Indexing (batch job)

A new `@Scheduled` task that indexes every mission that has no content embedding yet:

```java
@Scheduled(fixedDelay = 3_600_000)  // hourly
public void indexMissingEmbeddings() {
    List<Mission> unindexed = missionRepo.findByContentEmbeddingIsNull();
    for (Mission m : unindexed) {
        String text = m.getName() + " " + m.getDescriptionMarkdown();
        float[] embedding = aiService.getEmbedding(text);
        m.setContentEmbedding(embedding);
        missionRepo.save(m);
    }
}
```

---

## 5. Implementation phases

### Phase 1 — Infrastructure (1-2 days)
- [ ] `ai-service/` directory + Dockerfile + `main.py` (embed + health)
- [ ] Ollama container in docker-compose
- [ ] pgvector extension + migration
- [ ] `nomic-embed-text` model pull

### Phase 2 — Indexing (1 day)
- [ ] `content_embedding` column + index on the relevant tables
- [ ] Spring Boot batch indexer (missions, star systems)
- [ ] Manual trigger endpoint for admins (`POST /api/admin/reindex`)

### Phase 3 — Search (1 day)
- [ ] `SemanticSearchService` in Spring
- [ ] `GET /api/search?q=...` endpoint
- [ ] Frontend: search bar + results list

### Phase 4 — RAG (optional, 1-2 days)
- [ ] `ai-service` `/generate` endpoint with context
- [ ] Spring: top-K results → context → Ollama → text answer
- [ ] Frontend: "Ask the AI" panel

---

## Decision points

**Embedding dimension:** `nomic-embed-text` → 768 dim. If you switch to OpenAI later, a
re-index is needed (1536 dim).

**pgvector vs. a separate vector DB:** pgvector is sufficient up to ~100K documents.
Qdrant/Weaviate are only worth it if separate scaling is needed.

**Streaming responses:** Ollama's `/api/generate` supports `stream=true` — if the frontend
wants real-time text, this can be done via SSE through Spring.

**Model swap:** `CHAT_MODEL` and `EMBED_MODEL` come from env vars, so they can be swapped in
docker-compose without a rebuild.
