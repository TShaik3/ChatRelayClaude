# Migration Plan: ChatRelay → Full-Stack (Gradle + Spring Boot + PostgreSQL + Gson + Svelte)

One upfront risk to flag: **Spring Boot defaults to Jackson everywhere** (REST bodies, and STOMP over WebSocket). Using Gson means explicitly swapping the `HttpMessageConverter` and hand-wiring WebSocket payload encoding — it's supported, just not the path of least resistance. Called out in Phase 3.

---

## Phase 0 — Project scaffolding
- Convert to a **Gradle** multi-project build: `:backend` (Spring Boot) and a sibling `frontend/` (Svelte, own `package.json`, not a Gradle module).
- `backend/build.gradle.kts` dependencies: `spring-boot-starter-web`, `spring-boot-starter-websocket`, `spring-boot-starter-security`, `spring-boot-starter-jdbc`, `org.postgresql:postgresql`, `com.google.code.gson:gson`, `spring-boot-starter-test`, `org.testcontainers:postgresql`.
- Keep `run-tests.sh`'s intent but replace it with Gradle's `test` task; existing JUnit 5 tests in `test/` port over almost as-is once repackaged under `backend/src/test/java`.
- Add Flyway (`flyway-core` + `flyway-database-postgresql`) for schema migrations — cheap to add now, saves pain later.

## Phase 1 — Schema & data layer
- Design tables mirroring `Chat.java`, `User.java`/`ITAdmin.java`, `Message.java`:
  - `users(id, username, password_hash, first_name, last_name, is_disabled, is_admin)`
  - `chats(id, owner_id, room_name, is_private)`
  - `chat_members(chat_id, user_id)` — replaces the `/`-and-`,`-delimited `chatters` string
  - `messages(id, created_at, content, author_id, chat_id)`
- Write these as Flyway `V1__init.sql`.
- Replace `DBManager.java`'s file-load/rewrite methods with Spring `JdbcTemplate` (or `NamedParameterJdbcTemplate`) repositories — one per aggregate: `UserRepository`, `ChatRepository`, `MessageRepository`. Drop the in-memory `ConcurrentHashMap` + full-file-rewrite pattern entirely; Postgres is now the source of truth.
- One-off migration script (`MigrateFlatFilesToPostgres`, run once) to parse `Users.txt`/`Chats.txt`/`Messages.txt` and insert into Postgres — reuse the existing `Packet.unsanitize` parsing logic from `DBManager.loadUsers/loadChats/loadMessages` since the delimiter format doesn't change until this step runs. **Hash plaintext passwords with BCrypt during this import**, since they're stored in cleartext today.

## Phase 2 — Domain model cleanup
- Strip `Serializable`, `toString()`/`toStringClient()` wire-format methods from `AbstractUser`/`User`/`ITAdmin`/`Chat`/`Message` — those existed only to serialize over the raw socket protocol.
- Introduce API-facing DTOs (`UserDto`, `ChatDto`, `MessageDto`) that Gson serializes directly; keep domain objects as persistence-layer models mapped by the JDBC repositories.

## Phase 3 — Backend API & realtime
- REST controllers replacing the `ActionType` switch in `Server.java`:
  - `AuthController` — `POST /login`, `POST /logout`
  - `UserController` — `GET /users`, `POST /users` (admin), `PUT /users/{id}` (admin)
  - `ChatController` — `POST /chats`, `PUT /chats/{id}/rename`, `POST /chats/{id}/members`, `DELETE /chats/{id}/members/{userId}`
  - `MessageController` — `GET /chats/{id}/messages`, `POST /chats/{id}/messages`
- **Spring Security**: session or JWT-based auth (session is simpler for a first cut), `BCryptPasswordEncoder`, an admin-only `@PreAuthorize` guard replacing `Server.requireAdmin`.
- **Gson wiring**: register a `GsonHttpMessageConverter` bean and set it ahead of Jackson in `WebMvcConfigurer.extendMessageConverters`, so REST bodies use Gson instead of the Spring Boot default.
- **Realtime**: Spring `@EnableWebSocketMessageBroker` (STOMP) with per-chat topics (`/topic/chats/{chatId}`) and a per-user queue (`/user/queue/updates`) — this replaces `Server.sendPacketToUsers`/`ClientHandler.sendPacket`. Since STOMP's default (de)serialization is Jackson-based, either accept Jackson for the WS layer only, or supply a custom `MessageConverter` that shells out to Gson — decide based on how strongly "Gson everywhere" matters vs. shipping speed.

## Phase 4 — Svelte frontend
- Scaffold with Vite (`npm create vite@latest frontend -- --template svelte`).
- Screens mapped from `GUI.java`: login form, sidebar (chat list + admin's "All Users" panel), chat window, create-chat/create-user/edit-user dialogs.
- A `stores.js` mirroring `Client.java`'s in-memory state: writable stores for `users`, `chats`, `messages`, `currentUser`.
- `api.js` — `fetch`-based REST client for the controllers above.
- `ws.js` — native `WebSocket` or `@stomp/stompjs` client subscribing to the STOMP topics, folding incoming events into the stores (this is the direct replacement for `Client.handleIncomingPacket`).

## Phase 5 — Cutover
- Since this looks like a small internal tool (not a live production system with concurrent users to migrate gracefully), a **hard cutover** is simplest: run the Postgres import script once, stand up the new backend+frontend, retire `ServerMain`/`ClientMain`/the Swing GUI.
- Dockerize: `docker-compose.yml` with `postgres`, `backend` (Spring Boot jar), and `frontend` (static build served via nginx or Spring Boot's static resources for a simpler single-deployable option).

## Phase 6 — Testing
- Port existing JUnit tests (currently exercising `DBManager`/`Server` directly) to test the new JDBC repositories and REST controllers (`@WebMvcTest`, `@SpringBootTest` with Testcontainers-backed Postgres instead of the throwaway `dbFilePath` trick in `Server`'s test constructor).
- Add frontend tests (Vitest + `@testing-library/svelte`) for the new UI, since none exist today.

---

**Suggested order of execution**: Phase 0 → 1 → 2 → 3 → 4, with Phase 5/6 threaded throughout rather than saved for the end.
