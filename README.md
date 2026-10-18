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

Needs Java 21 (check with `java -version`) and Docker.

```bash
git clone https://github.com/yashgandhi876/spring-ai-starter-kit.git
cd spring-ai-starter-kit
docker compose up -d        # Postgres with pgvector, and Ollama
./gradlew bootRun
```

Open http://localhost:8080. The first start downloads the Ollama models
(`llama3.2` and `nomic-embed-text`, about 2.3 GB), so the first answer can take a few minutes.

Prefer to run everything in Docker? `docker compose --profile app up -d --build`.

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
| `AI_TEMPERATURE` | `0.3` | Higher is more creative, lower is more predictable |
| `AI_MAX_TOKENS` | `1024` | Longest answer the model may write |
| `ANTHROPIC_MODEL` / `OPENAI_CHAT_MODEL` / `OLLAMA_CHAT_MODEL` | see `.env.example` | Chat model per provider |
| `OLLAMA_EMBEDDING_MODEL` / `OPENAI_EMBEDDING_MODEL` | `nomic-embed-text` / `text-embedding-3-small` | Embedding model per provider |
| `AI_SYSTEM_PROMPT` | a helpful-assistant prompt | Instructions sent with every chat |
| `RAG_TOP_K` | `4` | How many document chunks are sent with each question (1 to 20) |
| `RAG_SIMILARITY_THRESHOLD` | `0.0` | Drop chunks less similar than this (0 to 1) |
| `RAG_CHUNK_SIZE` | `800` | Chunk size in tokens when splitting documents (100 to 4000) |
| `MAX_UPLOAD_SIZE` | `20MB` | Largest file that can be uploaded |
| `APP_NAME` | `Spring AI Starter Kit` | Name shown on the chat page |
| `APP_WELCOME_MESSAGE` | a short greeting | First message on the chat page |
| `APP_ACCENT_COLOR` | `#2f6f4f` | Button and highlight colour, as `#rrggbb` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | local Compose database | Postgres connection |

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

Errors come back as `application/problem+json` with a `title` and a `detail` you can show to users.

| Status | When |
| --- | --- |
| 400 | Blank or too-long message or question, malformed JSON, empty, missing or non-multipart upload |
| 413 | File larger than `MAX_UPLOAD_SIZE` |
| 415 | File type other than PDF, Markdown or text |
| 422 | Document has no extractable text (for example a scanned PDF) |
| 500 | Unexpected server error; details are in the server log only |
| 502 | The AI provider failed (bad key, unknown model, provider down); details are in the server log only |
| 503 | The database can't be reached |

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
