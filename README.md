# ChatRelay

A chat application originally built as a pure-Java raw-socket server with a Swing desktop
client, migrated to a full-stack REST + WebSocket application (Spring Boot, PostgreSQL, Svelte).

---

## Project Timeline

### Original Java project

The project started as a plain `javac`/`java` (no build tool) client/server chat app:

- **`packet/`** — `Packet`/`ActionType`/`Status`: a `Serializable` envelope sent over raw
  `Socket`/`ObjectOutputStream`/`ObjectInputStream` connections, plus `sanitize`/`unsanitize`
  helpers for encoding `/`-delimited fields.
- **`server/`** — `Server`/`ClientHandler`/`ServerMain`: a multithreaded accept loop (one thread
  per connected client) implementing all protocol business logic (login, create/rename chat,
  send message, admin user management) in a big `switch` over `ActionType`.
- **`client/`** — `Client`/`ClientListener`/`ClientMain`/`GUI`: a Swing desktop UI driving the
  socket client and re-rendering on incoming broadcasts.
- **`server/DBManager.java`** — persistence via three flat `.txt` files (`Users.txt`,
  `Chats.txt`, `Messages.txt`) under `dbFiles/development/`, loaded into memory at startup and
  rewritten whole-file on every write.
- Test coverage: JUnit 5 unit tests for the model/packet layer, a headless socket harness for
  protocol/integration tests, and manual multi-instance testing for the Swing GUI — documented in
  full in [TEST_PLAN.md](TEST_PLAN.md) (now a historical document, see below).

### Migration to full-stack

Converted in six phases to Gradle + Spring Boot + PostgreSQL + Svelte, executed as a
**strangler-fig migration** — the new backend/frontend were built and verified alongside the
still-running legacy socket server/Swing client, which were only deleted once their replacements
were proven out:

| Phase | What happened |
|---|---|
| 0 — Scaffolding | Converted to a Gradle multi-project build (`:backend` + sibling `frontend/`); added Spring Boot, Flyway. |
| 1 — Schema & data layer | Designed Postgres tables (`users`, `chats`, `chat_members`, `messages`); replaced `DBManager`'s flat-file I/O with JDBC repositories; one-off migration of the legacy `.txt` files into Postgres, hashing plaintext passwords with BCrypt along the way. |
| 2 — Domain model cleanup | Added Jackson-serializable `UserDto`/`ChatDto`/`MessageDto`; removed genuinely dead code. `toString()`/`toStringClient()` deliberately deferred to Phase 5 since ~15 existing tests still asserted on them. |
| 3 — Backend API & realtime | Added REST controllers (`AuthController`, `UserController`, `ChatController`, `MessageController`) and STOMP-over-WebSocket broadcasts, session-based Spring Security — all running alongside the untouched socket server on port 8080 next to the socket server's port 5000. Verified with 21 new integration tests plus a real end-to-end WebSocket broadcast test. |
| 4 — Svelte frontend | Built the Svelte 5 UI (stores, REST client, STOMP client, screens mapped 1:1 from `GUI.java`) against the Phase 3 backend. Verified with a real Playwright browser run against the full stack. |
| 5 — Cutover | Retired the legacy stack for good: deleted `server.Server`/`ClientHandler`/`ServerMain`, all of `client.*`, and `packet.*` once a full reference audit ([CutoverReferenceMap.md](CutoverReferenceMap.md)) confirmed nothing else depended on them. Added a single-deployable multi-stage `Dockerfile` + `docker-compose.yml`. Result: 77 tests passing, zero references to the legacy code remaining. |
| 6 — Testing | Migrated backend tests from a locally-installed Postgres to Testcontainers (via colima); wrote the frontend test suite (Vitest + `@testing-library/svelte`) from scratch — 32 tests across 5 files. |

Full narrative detail, including every bug found and fixed along the way, is in
[MigrationPlan.md](MigrationPlan.md).

---

## Documentation

- **[MigrationPlan.md](MigrationPlan.md)** — the phase-by-phase plan and record of what was
  actually built and verified at each step (summarized in the timeline above).
- **[TEST_PLAN.md](TEST_PLAN.md)** — the *original* test plan, written for the legacy
  socket/Swing architecture. Superseded by the automated suites under `backend/src/test/java`
  (`api/*Test.java`, `server/DBManagerTest.java`, `model/*Test.java`, `dto/DtoMappingTest.java`)
  and `frontend/src/**/*.test.js`; kept as a historical reference rather than rewritten.
- **[CutoverReferenceMap.md](CutoverReferenceMap.md)** — the reference audit performed before
  deleting the legacy socket/Swing code, mapping every remaining call site (including the
  non-obvious ones, like `packet.Packet.sanitize` leaking into the domain model) to confirm what
  was actually safe to remove.

---

## Running Locally

### Prerequisites

- JDK 21
- Node.js (with npm)
- PostgreSQL 16 running locally, with a `chatrelay_dev` database and a `chatrelay`/`chatrelay`
  role able to access it (matches the defaults in
  [backend/src/main/resources/application.properties](backend/src/main/resources/application.properties))

### 1. Start the backend (Spring Boot, port 8080)

```bash
./gradlew :backend:bootRun
```

Flyway applies migrations automatically on startup, and an empty database is auto-seeded with an
`admin`/`admin` login. Connection settings can be overridden via `CHATRELAY_DB_URL`,
`CHATRELAY_DB_USER`, `CHATRELAY_DB_PASSWORD` env vars.

This command runs in the foreground and doesn't "finish" while the server is up — that's
expected, not a hang.

### 2. Start the frontend (Vite/Svelte dev server)

In a separate terminal:

```bash
cd frontend
npm install   # first time only
npm run dev
```

Open the URL Vite prints (typically `http://localhost:5173`). The dev server proxies `/api` and
`/ws` to `localhost:8080` (see [frontend/vite.config.js](frontend/vite.config.js)), so no CORS
setup is needed.

### Alternative: single-container Docker build

To run the production-style build (frontend baked into the Spring Boot jar, served same-origin)
against its own disposable Postgres instead of the two-terminal dev setup above:

```bash
docker compose up --build
```

This serves the whole app at `http://localhost:8080`.

### Running tests

```bash
./gradlew :backend:test      # backend (Testcontainers-backed Postgres, no local DB needed)
cd frontend && npm test      # frontend (Vitest)
```
