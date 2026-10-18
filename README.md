# Spring AI Starter Kit

A production-ready Spring Boot 4 + Spring AI 2.0 starting point for AI apps in Java.
Chat with streaming, ask questions about your own documents (RAG) with cited sources,
and switch between Claude, OpenAI and a free local model by changing one setting.

[![CI](https://github.com/yashgandhi876/spring-ai-starter-kit/actions/workflows/ci.yml/badge.svg)](https://github.com/yashgandhi876/spring-ai-starter-kit/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4-green)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow)

## What you get

- **Chat API with streaming** (`/api/chat`, `/api/chat/stream`) using Server-Sent Events
- **RAG over your documents**: upload PDF, Markdown or text files, get answers with the sources they came from;
  list, replace and delete documents
- **Provider switching without code changes**: Ollama (local, free), Anthropic Claude or OpenAI
- **Built-in chat page** at `http://localhost:8080`, with your own app name, welcome text and colour
- **Everything configurable**: models, temperature, prompts, RAG tuning and branding through environment variables
- **Production basics**: input validation, RFC 9457 error responses that never leak provider errors or keys,
  health probes, graceful shutdown, a non-root Docker image and GitHub Actions CI

## Quick start (local, no API key)

Needs Java 21 (check with `java -version`) and Docker with Compose 2.24 or newer
(check with `docker compose version`; update Docker Desktop if it is older).

```bash
git clone https://github.com/yashgandhi876/spring-ai-starter-kit.git
cd spring-ai-starter-kit
docker compose up -d        # Postgres with pgvector, and Ollama
./gradlew bootRun
```

Open http://localhost:8080. The first start downloads the Ollama models
(`llama3.2` and `nomic-embed-text`, about 2.3 GB), so the first answer can take a few minutes.

Prefer to run everything in Docker? `docker compose --profile app up -d --build`.

**Port already in use?** If you already run Postgres (port 5432) or Ollama (port 11434) on your machine,
pick other host ports. Compose and the app both read them:

```bash
export DB_PORT=5433
docker compose up -d
./gradlew bootRun
```

## Use Claude or OpenAI

```bash
export AI_PROVIDER=anthropic
export ANTHROPIC_API_KEY=sk-ant-...
./gradlew bootRun
```

or for OpenAI:

```bash
export AI_PROVIDER=openai
export OPENAI_API_KEY=sk-...
./gradlew bootRun
```

If the provider's key is not set, the app stops at startup and names the variable to export.
If chat answers `502`, the provider refused the request (an invalid key, an unknown model, no credits);
`503 AI provider busy` means try again shortly. The provider's own message is in the server log, never in the response.

Anthropic has no embeddings API, so RAG keeps using Ollama embeddings unless you set
`EMBEDDING_PROVIDER=openai` and `EMBEDDING_DIMENSIONS=1536`.

## Configuration

Every setting is an environment variable with a working default. Copy `.env.example` to `.env`
for Docker Compose, or export the variables before `./gradlew bootRun`.

| Setting | Default | What it does |
| --- | --- | --- |
| `AI_PROVIDER` | `ollama` | Chat provider: `ollama`, `anthropic` or `openai` |
| `EMBEDDING_PROVIDER` | `ollama` | Embedding provider for RAG: `ollama` or `openai` |
| `EMBEDDING_DIMENSIONS` | `768` | Must match the embedding model (`1536` for OpenAI `text-embedding-3-small`) |
| `AI_TEMPERATURE` | `0.3` | Higher is more creative, lower is more predictable. Ollama and OpenAI only: current Claude models don't accept it |
| `AI_MAX_TOKENS` | `1024` | Longest answer the model may write (Ollama and OpenAI) |
| `ANTHROPIC_MAX_TOKENS` | `8192` | Same for Claude; includes Claude's thinking, so it is higher. A cap, not a cost |
| `ANTHROPIC_MODEL` / `OPENAI_CHAT_MODEL` / `OLLAMA_CHAT_MODEL` | see `.env.example` | Chat model per provider |
| `OLLAMA_EMBEDDING_MODEL` / `OPENAI_EMBEDDING_MODEL` | `nomic-embed-text` / `text-embedding-3-small` | Embedding model per provider |
| `AI_SYSTEM_PROMPT` | a helpful-assistant prompt | Instructions sent with every chat |
| `RAG_TOP_K` | `4` | How many document chunks are sent with each question (1 to 20) |
| `RAG_SIMILARITY_THRESHOLD` | `0.0` | Drop chunks less similar than this (0 to 1) |
| `RAG_MAX_SCORE_GAP` | `0.15` | Keep only chunks scoring within this distance of the best match, so documents that aren't about the question are not cited; `1` keeps all |
| `RAG_CHUNK_SIZE` | `800` | Chunk size in tokens when splitting documents (100 to 4000) |
| `MAX_UPLOAD_SIZE` | `20MB` | Largest file that can be uploaded |
| `APP_NAME` | `Spring AI Starter Kit` | Name shown on the chat page |
| `APP_WELCOME_MESSAGE` | a short greeting | First message on the chat page |
| `APP_ACCENT_COLOR` | `#2f6f4f` | Button and highlight colour, as `#rrggbb` |
| `DB_PORT` / `OLLAMA_PORT` | `5432` / `11434` | Host ports for the Compose Postgres and Ollama containers; the app follows them |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local Compose database | Postgres connection (`DB_URL` overrides `DB_PORT`) |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server (overrides `OLLAMA_PORT`) |

Invalid values (for example `RAG_TOP_K=0` or a colour that isn't `#rrggbb`) stop the app at
startup with a message naming the setting, instead of failing later.

The RAG prompt can be changed in `application.yml` under `starter-kit.prompts.rag`;
it must contain `{context}` and `{question}`.

**Changing embedding models later:** vectors from different models can't be mixed. Drop the
table (`DROP TABLE vector_store;`) and upload your documents again after changing
`EMBEDDING_PROVIDER`, the embedding model or `EMBEDDING_DIMENSIONS`.

## API

```bash
# Chat
curl -s localhost:8080/api/chat -H 'Content-Type: application/json' -d '{"message":"Hello"}'

# Chat, streamed as Server-Sent Events; each event is JSON: data:{"text":" world"}
curl -N localhost:8080/api/chat/stream -H 'Content-Type: application/json' -d '{"message":"Tell me a joke"}'

# Upload a document (pdf, md, txt). Uploading the same file name again replaces it.
curl -s -F file=@handbook.pdf localhost:8080/api/documents

# List documents and their chunk counts
curl -s localhost:8080/api/documents

# Delete a document
curl -s -X DELETE localhost:8080/api/documents/handbook.pdf

# Ask about your documents
curl -s localhost:8080/api/ask -H 'Content-Type: application/json' -d '{"question":"What is the leave policy?"}'
```

Streamed tokens are wrapped in JSON so spaces and line breaks survive in every SSE client.
If the provider fails before the first token you get a normal error response; if it fails midway,
the last event is the problem itself (`data:{"title":...,"detail":...,"status":503}`).

Errors come back as `application/problem+json` with a `title` and a `detail` you can show to users.

| Status | When |
| --- | --- |
| 400 | Blank or too-long message or question, malformed JSON, empty, missing or non-multipart upload |
| 413 | File larger than `MAX_UPLOAD_SIZE` |
| 415 | File type other than PDF, Markdown or text |
| 422 | Document has no extractable text (for example a scanned PDF) |
| 500 | Unexpected server error; details are in the server log only |
| 502 | The AI provider refused the request: title `AI provider rejected the API key` for a bad key, `AI request failed` for anything else (unknown model, no credits); details are in the server log only |
| 503 | Temporary: `AI provider busy` (rate limited, overloaded, down or timed out; try again) or `Database unavailable` |

## Project layout

```
src/main/java/dev/starterkit/ai/
  chat/     chat API, streaming, provider-independent ChatService
  rag/      document upload, chunking, vector search, cited answers
  config/   StarterKitProperties: every customer setting, validated at startup
  web/      error handling
src/main/resources/
  application.yml       all settings and their environment variables
  static/index.html     the chat page
```

## Running the tests

```bash
./gradlew test
node --test 'src/test/js/*.test.mjs'   # the chat page's Markdown renderer; needs Node 22+
```

The tests need no API keys, network or Docker.

## Roadmap

- Spring Security login and per-user usage limits
- Tool-calling agents and an MCP server
- Guardrails (PII masking) and an evaluation test suite
- Token and cost tracking per user

Ideas and bug reports are welcome in Issues.

## License

MIT. This is an independent project and is not affiliated with or endorsed by
the Spring project, Broadcom, Anthropic or OpenAI.
