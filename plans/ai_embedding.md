# AI Keresés & Embedding — Megvalósítási Terv

## Célkitűzés

Szemantikus keresés a platform tartalmában (missziók, star system-ek, csoport leírások) helyi LLM-mel és vektoros adatbázissal — külső API-kulcs és hálózat nélkül.

---

## Architektúra áttekintés

```
Felhasználó
    │
    ▼
Spring Boot (/api/search)
    │
    ├─► PostgreSQL + pgvector   ← embedding index
    │       (hasonlóság keresés)
    │
    └─► ai-service (FastAPI)    ← Python microservice
            │
            ▼
        Ollama container
        (embedding + generálás)
```

### Komponensek

| Komponens | Technológia | Port | Feladat |
|---|---|---|---|
| `ollama` | `ollama/ollama` Docker | 11434 | Embedding + LLM inferencia |
| `ai-service` | Python 3.12 + FastAPI | 8081 | Embedding API, keresés |
| `postgres` | PostgreSQL 16 + pgvector | 5432 | Vektor tárolás + lekérdezés |
| `backend` | Spring Boot | 8080 | Orchestráció, auth |

---

## 1. Ollama Docker container

A modell fájlok a Windows fájlrendszeren vannak (`C:\Users\<user>\.ollama`).

```yaml
# docker-compose.yml kiegészítés
ollama:
  image: ollama/ollama
  volumes:
    - /mnt/c/Users/<username>/.ollama:/root/.ollama
  ports:
    - "11434:11434"
  networks:
    - legymernok-net
```

### Modell ajánlás

| Feladat | Modell | Miért |
|---|---|---|
| Embedding | `nomic-embed-text` | Kis méret, gyors, jó minőség |
| Keresés + válasz | `gemma3:8b-q4_K_M` | Gyors, 8B param elegendő |
| Részletes válasz | `llama2:13b-q4_K_M` | Jobb minőség, lassabb |

A `gemma3` és `llama2` már le vannak töltve. Az `nomic-embed-text`-et le kell tölteni:
```bash
# az ollama container-ben:
ollama pull nomic-embed-text
```

---

## 2. pgvector beállítás

Új Flyway migration (`V_ai__add_embeddings.sql`):

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

`nomic-embed-text` 768 dimenziós vektort generál. OpenAI ada-002 esetén 1536 kellene — ha modellt váltasz, a dimenzió is változik.

---

## 3. ai-service (Python FastAPI)

### Mappastruktúra

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
asyncpg==0.29.0        # pgvector lekérdezéshez
psycopg[binary]==3.2.0
pgvector==0.3.2
```

### `main.py` — fő API

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
    full_prompt = f"Kontextus:\n{context_block}\n\nKérdés: {req.prompt}" if context_block else req.prompt
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

### docker-compose kiegészítés

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

## 4. Spring Boot integráció

### Keresési flow

```
GET /api/search?q=elektromos+áramkör
    │
    ├─ 1. AI service: POST /embed { text: q } → vector
    │
    ├─ 2. PostgreSQL: SELECT id, name, 'MISSION' as type,
    │       1 - (content_embedding <=> $1) AS similarity
    │    FROM mission
    │    WHERE content_embedding IS NOT NULL
    │    ORDER BY similarity DESC LIMIT 5
    │
    └─ 3. Visszaad: [{id, name, type, similarity, description}]
```

### Új service osztály (Spring)

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

### Indexelés (batch job)

Új `@Scheduled` task, ami az összes content-et nélküli missziót indexeli:

```java
@Scheduled(fixedDelay = 3_600_000)  // óránként
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

## 5. Megvalósítási fázisok

### Fázis 1 — Infrastruktúra (1-2 nap)
- [ ] `ai-service/` mappa + Dockerfile + `main.py` (embed + health)
- [ ] Ollama container a docker-compose-ba
- [ ] pgvector extension + migration
- [ ] `nomic-embed-text` model pull

### Fázis 2 — Indexelés (1 nap)
- [ ] `content_embedding` oszlop + index a releváns táblákban
- [ ] Spring Boot batch indexelő (missziók, star system-ek)
- [ ] Manuális trigger endpoint adminoknak (`POST /api/admin/reindex`)

### Fázis 3 — Keresés (1 nap)
- [ ] `SemanticSearchService` Spring-ben
- [ ] `GET /api/search?q=...` endpoint
- [ ] Frontend: keresősáv + találati lista

### Fázis 4 — RAG (opcionális, 1-2 nap)
- [ ] `ai-service` `/generate` endpoint kontextussal
- [ ] Spring: top-K találat → context → Ollama → szöveges válasz
- [ ] Frontend: "Kérdezz az AI-tól" panel

---

## Döntési pontok

**Embedding dimenzió:** `nomic-embed-text` → 768 dim. Ha later OpenAI-ra váltasz, re-index kell (1536 dim).

**pgvector vs. külön vektorDB:** pgvector elégséges ~100K dokumentumig. Qdrant/Weaviate csak akkor érdemes ha külön scaling kell.

**Streaming válasz:** Az Ollama `/api/generate` támogatja a stream=true-t — ha a frontend real-time szöveget akar, SSE-vel a Spring-en keresztül meg lehet oldani.

**Modell swap:** Az `CHAT_MODEL` és `EMBED_MODEL` env var-ból jön, így a docker-compose-ban cserélhető futás nélkül.
