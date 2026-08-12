# Migration Plan: ChatRelay → Full-Stack (Gradle + Spring Boot + PostgreSQL + Jackson + Svelte)

Jackson is Spring Boot's default JSON library for both REST bodies and STOMP over WebSocket, so this removes the converter-wiring friction the Gson option would have introduced — `spring-boot-starter-web`/`spring-boot-starter-websocket` bring it in automatically, no extra dependency or configuration needed.

---

## Phase 0 — Project scaffolding
- Convert to a **Gradle** multi-project build: `:backend` (Spring Boot) and a sibling `frontend/` (Svelte, own `package.json`, not a Gradle module).
- `backend/build.gradle.kts` dependencies: `spring-boot-starter-web`, `spring-boot-starter-websocket`, `spring-boot-starter-security`, `spring-boot-starter-jdbc`, `org.postgresql:postgresql`, `spring-boot-starter-test`, `org.testcontainers:postgresql` (Jackson ships transitively with `spring-boot-starter-web`/`-websocket`).
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
- Correction from the original plan: only `Packet` ever implemented `Serializable` — `AbstractUser`/`User`/`ITAdmin`/`Chat`/`Message` never did, so there was nothing to strip there.
- `toString()`/`toStringClient()` are still the live wire format for the current socket protocol (`Server.java`, `DBManager.java`) and are directly asserted on by ~15 existing tests (including a security-relevant one confirming `toStringClient()` never leaks the password hash). Stripping them now would mean rewriting those tests twice — once now, once again in Phase 3 when the socket layer they exist for is deleted. **Deferred to Phase 3**, bundled with deleting `Server`/`ClientHandler`'s use of them.
- Added `UserDto`, `ChatDto`, `MessageDto` (Jackson-serializable records) with `from(...)` mappers off the domain models, plus mapping/round-trip tests — ready for Phase 3's controllers to consume directly. Domain objects remain the persistence-layer models mapped by the JDBC repositories.
- Removed `AbstractUser.getAllChatIds()` — genuinely dead code, zero call sites anywhere in the codebase.

## Phase 3 — Backend API & realtime ✅ done
Built as a **strangler-fig addition**, not a replacement: `Server`/`ClientHandler`/`ServerMain` (socket, port 5000) and `Client`/`GUI`/`ClientMain` (Swing) are untouched and still fully working, running alongside the new Spring MVC/WebSocket layer (HTTP, port 8080) against the same Postgres database. Both were left in place deliberately — deleting them now would leave no working UI at all until Phase 4's Svelte frontend exists to replace them; actual retirement happens at Phase 5's cutover, per that phase's own plan.

- New `api` package (`backend/src/main/java/api/`), scanned explicitly via `@SpringBootApplication(scanBasePackages = {"app", "api"})` since it's a sibling of `app`, not a sub-package.
- REST controllers, all delegating to the same `DBManager` the socket server uses (wired as a Spring bean over the auto-configured `DataSource`):
  - `AuthController` — `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` (the last one wasn't in the original plan — added because a session-cookie SPA needs *some* way to recover "who am I" after a page refresh).
  - `UserController` — `GET /api/users` (any authenticated user, matching the old GET_ALL_USERS behavior), `POST /api/users` / `PUT /api/users/{id}` (`@PreAuthorize("hasRole('ADMIN')")`, replacing `Server.requireAdmin`).
  - `ChatController` — `GET /api/chats`, `POST /api/chats`, `PUT /api/chats/{id}/rename`, `POST /api/chats/{id}/members`, `DELETE /api/chats/{id}/members/{userId}`. Ownership/admin authorization for the last three stays inside `DBManager.assertCanManageChat` (data-dependent business rule) rather than being duplicated as `@PreAuthorize`.
  - `MessageController` — `GET`/`POST /api/chats/{id}/messages`, scoped to one chat rather than dumping every visible message up front the way the socket login flow did; added `DBManager.fetchMessagesForChat` + `MessageRepository.findByChatId` to support it.
  - A `@RestControllerAdvice` (`ApiExceptionHandler`) maps `DBManager`'s existing exceptions to HTTP statuses (`IllegalArgumentException`→400, `SecurityException`/`AccessDeniedException`→403, `AuthenticationException`→401) — the same exceptions `Server.receivePacket`'s catch clause already turned into ERROR packets.
- **Spring Security**: session-based (`SecurityConfig`, `ChatRelayUserDetails`/`ChatRelayUserDetailsService` adapting `AbstractUser`), `BCryptPasswordEncoder` (reuses the hashing from Phase 1). Disabled accounts are rejected automatically via `UserDetails.isEnabled()` — Spring's own `DaoAuthenticationProvider` throws `DisabledException` before ever calling `checkLoginCredentials`. CSRF is disabled, acceptable for a same-origin SPA (Phase 4's Vite proxy, Phase 5's single-deployable) but would need revisiting behind a public multi-origin deployment.
- **Jackson**: no extra wiring needed, as planned.
- **Realtime**: `/topic/chats/{chatId}` for chat-scoped broadcasts (new message, rename, member added/removed) and `/user/queue/updates` for events aimed at someone who isn't subscribed to a topic yet (just added to a brand-new chat). The STOMP handshake authenticates via the existing HTTP session cookie: `PrincipalHandshakeInterceptor` reads `SecurityContextHolder` during the handshake's initial HTTP request and stashes the user id, `UserPrincipalHandshakeHandler` turns that into the STOMP session's `Principal` that `convertAndSendToUser` routes against.
- Verified for real, not just unit-tested in isolation: 21 new integration tests (`AuthControllerTest`, `UserControllerTest`, `ChatControllerTest`, `MessageControllerTest`, each against an isolated Postgres schema via the existing `TestDatabase` helper) plus one true end-to-end `WebSocketBroadcastTest` — a session-authenticated STOMP client subscribes to a chat topic, a REST call sends a message, and the test asserts the broadcast actually arrives over the wire. Also manually smoke-tested with `curl` against the real migrated `chatrelay_dev` data (login, wrong password, disabled account, admin-guard, unauthenticated access all behaved correctly) before writing the automated tests.
- One JDK gotcha hit and fixed along the way: `TestRestTemplate`'s default `HttpURLConnection`-based client throws `HttpRetryException` on a POST that gets back a 401 (it can't rewind an already-streamed request body to retry). Fixed by pointing test `RestTemplate`s at the modern `java.net.http.HttpClient`-backed `JdkClientHttpRequestFactory` instead (`support.TestRestTemplates`).
- `toString()`/`toStringClient()` remain on `AbstractUser`/`Chat`/`Message`, still used by the still-running socket layer — removal stays deferred to Phase 5, alongside deleting `Server`/`ClientHandler`/`Client`/`GUI` themselves.

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
