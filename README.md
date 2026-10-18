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
- **Optional login** with Spring Security: users and admins in Postgres, a sign-in page, HTTP Basic for API clients
- **Usage limits and cost tracking**: daily request and token limits per user, and tokens and cost on every answer
  and in an admin report
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

## Login, usage limits and cost tracking

Everything here is off by default, so the quick start stays two commands.

**Login.** Turn it on before you put the app where others can reach it:

```bash
export AUTH_ENABLED=true
export ADMIN_PASSWORD='choose-a-long-password'   # 12 to 64 characters; only used to create the first admin
./gradlew bootRun
```

The chat page now asks you to sign in. Admins add people with the API (see below). API clients sign in
with HTTP Basic, for example `curl -u alice:password ...`. Users and hashed passwords live in Postgres;
the tables are created by Flyway on first start, also for databases that already hold documents from v0.1.

**Usage limits.** `USAGE_DAILY_REQUESTS=50` allows each person 50 AI requests (chat, stream, ask) per UTC day;
`USAGE_DAILY_TOKENS=200000` caps their tokens. Over the limit, the API answers `429` with a `Retry-After`
header that counts down to midnight UTC. With login on, limits apply per user; with login off, per client
IP address (behind a reverse proxy, set `SERVER_FORWARD_HEADERS_STRATEGY=native` so the real address is used).

**Cost tracking.** Every answer reports the tokens it used and what they cost, the chat page shows it under
each answer, and admins get a daily report per user. Prices for current Claude and OpenAI models are built in
(Ollama is free); provider prices change, so set `AI_PRICE_INPUT_PER_MTOK` and `AI_PRICE_OUTPUT_PER_MTOK`
(US dollars per million tokens) if yours differ or you use another model. Embedding tokens are not counted.

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
| `AUTH_ENABLED` | `false` | Require sign-in for the chat page and the API |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / none | The first admin, created at startup if missing; the password is required (12 to 64 characters) when login is on |
| `USAGE_DAILY_REQUESTS` | `0` | AI requests per user (or IP) per UTC day; `0` means no limit |
| `USAGE_DAILY_TOKENS` | `0` | Input plus output tokens per user (or IP) per UTC day; `0` means no limit |
| `AI_PRICE_INPUT_PER_MTOK` / `AI_PRICE_OUTPUT_PER_MTOK` | built in for known models | US dollars per million tokens, for cost tracking |
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

# Your usage today and the limits that apply
curl -s localhost:8080/api/usage/me
```

Answers include what they used: `"usage":{"inputTokens":312,"outputTokens":58,"costUsd":0.0012}`.
A stream ends with a `data:{"usage":{...}}` event.

With login on, add `-u user:password` to every call. These need login on:

```bash
# Who am I (works without login too, and says whether login is on)
curl -s -u alice:password localhost:8080/api/me

# Change your own password
curl -s -u alice:password localhost:8080/api/me/password -H 'Content-Type: application/json' \
  -d '{"currentPassword":"old password here","newPassword":"new password here"}'

# Admins: list, add and remove users (role USER or ADMIN)
curl -s -u admin:password localhost:8080/api/admin/users
curl -s -u admin:password localhost:8080/api/admin/users -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"a long password","role":"USER"}'
curl -s -u admin:password -X DELETE localhost:8080/api/admin/users/alice

# Admins: tokens and cost per user per day, with totals (days: 1 to 366, default 30)
curl -s -u admin:password 'localhost:8080/api/admin/usage?days=7'
```

Streamed tokens are wrapped in JSON so spaces and line breaks survive in every SSE client.
If the provider fails before the first token you get a normal error response; if it fails midway,
the last event is the problem itself (`data:{"title":...,"detail":...,"status":503}`).

Errors come back as `application/problem+json` with a `title` and a `detail` you can show to users.

| Status | When |
| --- | --- |
| 400 | Blank or too-long message or question, malformed JSON, empty, missing or non-multipart upload |
| 401 | Login is on and you are not signed in, or the username or password is wrong |
| 403 | Signed in, but an admin-only call, or the page's CSRF token is missing |
| 409 | A user with that name already exists |
| 413 | File larger than `MAX_UPLOAD_SIZE` |
| 415 | File type other than PDF, Markdown or text |
| 422 | Document has no extractable text (for example a scanned PDF) |
| 429 | `Daily limit reached`; `Retry-After` says how many seconds until midnight UTC |
| 500 | Unexpected server error; details are in the server log only |
| 502 | The AI provider refused the request: title `AI provider rejected the API key` for a bad key, `AI request failed` for anything else (unknown model, no credits); details are in the server log only |
| 503 | Temporary: `AI provider busy` (rate limited, overloaded, down or timed out; try again) or `Database unavailable` |

## Project layout

```
src/main/java/dev/starterkit/ai/
  chat/     chat API, streaming, provider-independent ChatService
  rag/      document upload, chunking, vector search, cited answers
  config/   StarterKitProperties: every customer setting, validated at startup
  security/ login, users, admin user API
  usage/    daily limits, token and cost tracking, usage report
  web/      error handling
src/main/resources/
  application.yml       all settings and their environment variables
  db/migration/         Flyway migrations for the app's own tables
  static/index.html     the chat page (login.html when login is on)
```

## Running the tests

```bash
./gradlew test
node --test 'src/test/js/*.test.mjs'   # the chat page's Markdown renderer; needs Node 22+
```

The tests need no API keys or Docker. Database tests run on a real Postgres that Gradle downloads
once as a regular dependency and starts in-process.

## Roadmap

- Tool-calling agents and an MCP server
- Guardrails (PII masking) and an evaluation test suite

Ideas and bug reports are welcome in Issues.

## License

MIT. This is an independent project and is not affiliated with or endorsed by
the Spring project, Broadcom, Anthropic or OpenAI.
