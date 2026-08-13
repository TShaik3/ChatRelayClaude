# Phase 5 Cutover — Reference Map

Investigation performed before deleting the legacy socket server (`server.Server`/`ClientHandler`/`ServerMain`) and Swing client (`client.*`), to confirm exactly what could be safely removed and what had to survive for the REST/WebSocket layer (Phase 3) and Svelte frontend (Phase 4).

## 1. Files under `server/`, `client/`, `packet/`

**`backend/src/main/java/client/`**
- `Client.java` — Socket client: opens the `Socket`/`ObjectOutputStream`/`ObjectInputStream`, sends `Packet`s for every user action (login, send message, create/rename chat, create/update user, etc.), and folds incoming `Packet`s into local `users`/`chats` state before refreshing the GUI.
- `ClientListener.java` — Background thread that blocks on `ObjectInputStream.readObject()` in a loop and hands each deserialized `Packet` to `Client.handleIncomingPacket`.
- `ClientMain.java` — CLI entry point (`main`) that reads `ip`/`port` args and starts a `Client`.
- `GUI.java` — Swing desktop UI (login screen, chat list, message panel, admin dialogs) driven entirely by calling `Client` methods and being notified via `Client.update(ActionType)`.

**`backend/src/main/java/server/`**
- `ClientHandler.java` — Runnable owning one accepted socket; handles `LOGIN`/`LOGOUT` directly (calls `DBManager.checkLoginCredentials`, `fetchAllUsers/Chats/Messages`) and otherwise delegates to `Server.receivePacket`.
- `Server.java` — Multithreaded accept loop plus all protocol business logic (`receivePacket`'s switch over `ActionType`), builds outgoing `Packet`s from `model.*` `toString()`/`toStringClient()`, runs `Migrations.migrate` and seeds a default admin at construction.
- `ServerMain.java` — CLI entry point; builds a `DataSources.devDataSource()` and starts a `Server`.
- `DBManager.java` — Business logic for users/chats/messages backed by Postgres (via `server.repository.*`); used by **both** the socket layer and the REST layer (see item 4).
- `repository/ChatRepository.java`, `repository/MessageRepository.java`, `repository/UserRepository.java` — JdbcTemplate-backed persistence, consumed only by `DBManager`.
- `support/DataSources.java` — Builds the pooled Hikari `DataSource` for the standalone socket server.
- `support/Migrations.java` — Runs Flyway migrations against a given `DataSource` explicitly (used outside Spring's auto-config context).
- `support/MigrateFlatFilesToPostgres.java` — One-off importer of the legacy `Users.txt`/`Chats.txt`/`Messages.txt` flat files into Postgres.

**`backend/src/main/java/packet/`**
- `ActionType.java` — Enum of all wire actions (client→server requests, server→client broadcasts, generic replies).
- `Packet.java` — `Serializable` envelope sent over the raw socket (`id`, `ActionType`, `Status`, args, sender, timestamp) plus static `sanitize`/`unsanitize` helpers for `/`-delimited fields.
- `Status.java` — Enum: `SUCCESS`, `ERROR`, `NONE`.

## 2. References to `packet.*` outside server/client packages — the key finding

`packet.Packet` was **not** contained to the legacy layer. It leaked into the domain model, which the new REST layer depends on:

- `model/AbstractUser.java` — used in `toString()` and `toStringClient()` via `Packet.sanitize(...)`.
- `model/Chat.java` — used in `toString()` via `Packet.sanitize(roomName)`.
- `model/Message.java` — used in `toString()` via `Packet.sanitize(content)`.
- `server/repository/MessageRepository.java` — used in `findVisibleToAsStrings` via `Packet.sanitize(rs.getString("content"))`.
- `server/support/MigrateFlatFilesToPostgres.java` — used `Packet.unsanitize` when parsing the legacy flat files.

`ActionType` and `Status` had **no** references outside `server/`, `client/`, and `packet/` itself.

Nothing in `api/`, `dto/`, or `model/`'s own logic referenced `ActionType`/`Status`. Conclusion: **`packet.ActionType`/`packet.Status` were safely deletable outright, but `packet.Packet` could not be deleted until `toString()`/`toStringClient()` were first removed from the model classes and `findVisibleToAsStrings` removed from `MessageRepository`** — only then does `Packet.sanitize`/`unsanitize` become fully unreferenced.

## 3. `.toString()` / `.toStringClient()` call sites for `AbstractUser`/`Chat`/`Message`

**Legacy socket layer (deleted alongside server/client):**
- `server/Server.java` — `message.toString()`, `chat.toString()` (×2), `newUser.toStringClient()`, `updated.toStringClient()`
- `server/DBManager.java` — `user.toStringClient()` in `fetchAllUsers()`, `chat.toString()` in `fetchAllChats()`
- `server/DBManagerTest.java` — `message.toString()`, `privateChat.toString()` (×3)
- `model/UserModelTest.java` — `user.toString()`, `user.toStringClient()`
- `model/MessageModelTest.java` — `message.toString()` (×2)
- `model/ChatModelTest.java` — `chat.toString()` (×5)

**New REST/WebSocket layer:** zero call sites. `dto/UserDto.java` only had a comment referencing `toStringClient()`; the DTOs are built from getters (`user.getId()`, `getUserName()`, etc.), not from the wire-format strings.

**Conclusion:** `toString()`/`toStringClient()` on the three model classes were used exclusively by the legacy layer and its own tests — safe to remove once `server.Server`, `DBManager`'s wire-format `fetchAll*` methods, and those test files are gone.

## 4. `DBManager` public methods — callers by layer

| Method | `server/` (socket) | `api/` (REST) | Tests |
|---|---|---|---|
| `fetchAllUsers()` | `Server`, `ClientHandler` | — | `DBManagerTest`, `PersistenceRestartTest` |
| `fetchAllChats(user)` | `Server`, `ClientHandler` | — | `DBManagerTest`, `PersistenceRestartTest` |
| `fetchAllMessages(user)` | `Server`, `ClientHandler` | — | `DBManagerTest` |
| `checkLoginCredentials` | `ClientHandler` | — (REST login goes through Spring Security's `AuthenticationManager`/`ChatRelayUserDetailsService`, not this) | `DBManagerTest` |
| `updateUserIsDisabled` | — (not called, already dead before this phase) | — | `DBManagerTest` only |
| `listAllUsers()` | — | `UserController` | — |
| `listChatsVisibleTo(user)` | — | `ChatController` | — |
| `fetchMessagesForChat(user, chatId)` | — | `MessageController` | — |
| `getUserById`/`getChatById` | direct + internal | reached internally through surviving methods | `DBManagerTest` |
| `getUserByUsername` | internal | `ChatRelayUserDetailsService` | multiple |
| `writeNewUser` | `Server` | `UserController` | many |
| `writeNewChat` | `Server` | `ChatController` | many |
| `writeNewMessage` | `Server` | `MessageController` | `DBManagerTest` |
| `updateUserDetails` | `Server` | `UserController` | `DBManagerTest`, `UserControllerTest` |
| `addUserToChat` / `removeUserFromChat` / `renameChat` | `Server` | `ChatController` | `DBManagerTest` |

**Dead-code once the socket layer is gone:** `fetchAllUsers()`, `fetchAllChats(AbstractUser)`, `fetchAllMessages(AbstractUser)` (all superseded by the `list*`/`fetchMessagesForChat` domain-object equivalents), `checkLoginCredentials` (REST auth never calls it), and `updateUserIsDisabled` (already had zero production callers even before this phase — only a test was propping it up).

## 5. `MessageRepository.findVisibleToAsStrings`

Only one caller anywhere: `DBManager.fetchAllMessages(AbstractUser)`. Dead once that method is removed — which also removes `MessageRepository`'s only dependency on `packet.Packet`.

## 6. `DataSources` / `Migrations` callers

- `DataSources.devDataSource()` — called by `ServerMain` and `MigrateFlatFilesToPostgres`. No caller in `api/` (Spring Boot wires its own `DataSource` bean for the REST app).
- `Migrations.migrate(dataSource)` — called by `Server`'s constructor and `MigrateFlatFilesToPostgres`. No caller in `api/`.

Both dead once `ServerMain`/`Server` and `MigrateFlatFilesToPostgres` are retired.

## 7. `MigrateFlatFilesToPostgres` invocation

Single call path confirmed: the `migrateFlatFiles` Gradle task in `backend/build.gradle.kts` (`./gradlew migrateFlatFiles -PflatFileDir=...`). No other code path, script, or test references this class.

## 8. Tests importing `support.TestServerHarness` / `support.TestConnection`

Exactly the socket-layer test files under `backend/src/test/java/server/`: `ServerProtocolTest`, `PersistenceRestartTest`, `ErrorHandlingTest`, `ConcurrencyTest`. (`DBManagerTest` imports only `TestDatabase`, not the socket harness — it talks to `DBManager` directly.)

The REST-layer tests (`api/*Test`, `api/websocket/WebSocketBroadcastTest`) import `support.ApiSession`/`support.TestRestTemplates` instead, so `TestServerHarness`/`TestConnection` could be deleted without touching them. `support.TestDatabase` is shared by both groups and had to survive.

## 9. `TEST_PLAN.md` and `dbFiles/`

**`dbFiles/`** — the legacy flat-file store (`Users.txt`, `Chats.txt`, `Messages.txt`, plus a stray `.DS_Store`). Referenced only by `MigrateFlatFilesToPostgres`'s default path and by `TEST_PLAN.md`'s prose; nothing in `backend/src` reads it (the current `DBManager` is fully Postgres-backed).

**`TEST_PLAN.md`** (231 lines, 14 sections) is entirely about the legacy socket architecture (`Packet`/`Server`/`ClientHandler`/`Client`/`GUI`/flat-file `DBManager`) and was already stale relative to the Postgres-backed `DBManager` even before this phase. It has no coverage of `api/`, `dto/`, or the WebSocket layer. Needs a full rewrite/replacement or explicit archiving as part of cutover, not a small edit.
