# ChatRelay — Test Plan

## 1. Objectives & Scope

Validate that the client/server chat application behaves correctly across:

- the wire protocol (`Packet`/`ActionType`/`Status`),
- the domain model (`AbstractUser`/`User`/`ITAdmin`/`Chat`/`Message`),
- the persistence layer (`DBManager` and its three `.txt` files),
- the multithreaded server (`Server`/`ClientHandler`),
- the client (`Client`/`ClientListener`) and its Swing `GUI`,

under normal use, concurrent/multi-client use, restarts, and malformed or unauthorized input.

Out of scope: network security hardening (TLS, auth tokens), horizontal scaling beyond one `Server` process, i18n.

## 2. Test Levels

| Level | Target | Method |
|---|---|---|
| Unit | `model/*`, `packet/Packet` | JUnit, no sockets |
| Component/Persistence | `server/DBManager` | JUnit against a temp `dbFiles` directory |
| Integration/Protocol | `Server` + `ClientHandler` over real sockets | Headless socket harness (see §8), one `Packet` in/out per assertion |
| Concurrency | `Server`, `DBManager` | Many threads/sockets hammering the same server |
| System/Manual | `Client` + `GUI` | Manual exploratory pass with two+ running `ClientMain` instances |
| Persistence/Restart | `DBManager` files | Kill server mid-session, restart, verify state and id continuity |

## 3. Test Environment

- JDK matching the one used to compile (`javac -d out $(find src -name '*.java')`).
- Each test run uses an **isolated** `dbFiles/development/` directory (copy or point `DBManager` at a temp path) — never the developer's working data.
- Server started via `java -cp out server.ServerMain <port> 127.0.0.1`. First boot on an empty DB seeds `admin`/`admin` (IT admin) — confirm this seed exists before login-dependent tests.
- Protocol-level tests talk to the server directly via `ObjectOutputStream`/`ObjectInputStream` and `packet.Packet`, bypassing `Client`/`GUI`, exactly like the smoke test used during development. This is faster and more precise than driving the Swing UI for every case.

## 4. Unit Tests — Model & Packet

### 4.1 `Packet`
| ID | Case | Expected |
|---|---|---|
| PKT-1 | Construct with args, read back via all getters | `getActionType`, `getStatus`, `getSenderId`, `getActionArguments`, `getTimeCreated` return what was passed in |
| PKT-2 | Two `Packet`s constructed in sequence | `getId()` values are distinct (monotonic counter) |
| PKT-3 | `sanitize("a/b/c")` then `unsanitize(...)` | round-trips to `"a/b/c"` |
| PKT-4 | `sanitize(null)` / `unsanitize(null)` | returns `null`, no exception |
| PKT-5 | `sanitize` on a string with no `/` | returns the input unchanged |
| PKT-6 | Serialize a `Packet` (`ObjectOutputStream`) and deserialize it | resulting object is `equals`-by-field to the original (id, type, status, args, senderId) |

### 4.2 `AbstractUser` / `User` / `ITAdmin`
| ID | Case | Expected |
|---|---|---|
| USR-1 | Construct via the "new user" ctor (no id) twice | ids are distinct and increase |
| USR-2 | Construct via the "load from storage" ctor with an explicit id | `getId()` returns exactly that id, no counter side-effect beyond `restoreCount` |
| USR-3 | Construct via the "frontend" ctor (`frontEndUser=true`) | `getPassword()` is `null`; other fields set correctly |
| USR-4 | `addChat(chat)` then `getChats()` | contains the chat exactly once |
| USR-5 | `addChat(chat)` called twice with the same chat | still only one entry (no duplicates) |
| USR-6 | `removeChat(chat)` on a user without that chat | no exception, list unchanged |
| USR-7 | `updateIsDisabled(true)` then `isDisabled()` | returns `true` |
| USR-8 | `toString()` on a user whose name contains `/` | output round-trips through `Packet.unsanitize` split back to the original name |
| USR-9 | `toStringClient()` | does **not** contain the password anywhere in the output |
| USR-10 | `new User(...)` vs `new ITAdmin(...)` with `isAdmin=true/false` respectively | `isAdmin()` matches constructor arg regardless of concrete class |
| USR-11 | `AbstractUser.restoreCount(5)` then construct a new user | new id is `6`, not colliding with `5` |
| USR-12 | `restoreCount` called with a value lower than the current counter | counter is unaffected (never decreases) |

### 4.3 `Chat`
| ID | Case | Expected |
|---|---|---|
| CHT-1 | Construct with owner + initial chatters list | owner is in `getChatters()`; each chatter's `getChats()` includes this chat |
| CHT-2 | `addChatter` with a user already present | no duplicate in `getChatters()` |
| CHT-3 | `removeChatter` | user removed from `getChatters()` **and** chat removed from that user's `getChats()` |
| CHT-4 | `addMessage` then `getMessages()` | message present, insertion order preserved |
| CHT-5 | `changePrivacy(true)` then `isPrivate()` | `true` |
| CHT-6 | `setRoomName("new/name")` then `toString()` then re-parse | name round-trips correctly despite the `/` |
| CHT-7 | `toString()` format | matches `id/ownerId/roomName/isPrivate/chatterId1,chatterId2,...` exactly |
| CHT-8 | Chat with zero additional chatters (owner only) | `toString()` chatter list is just the owner's id, no trailing comma |

### 4.4 `Message`
| ID | Case | Expected |
|---|---|---|
| MSG-1 | Construct via "new message" ctor | `getCreatedAt()` is a plausible current epoch-seconds value |
| MSG-2 | Construct via "load from storage" ctor with explicit id/timestamp | both preserved exactly |
| MSG-3 | `toString()` with content containing `/` | round-trips via `Packet.unsanitize` |
| MSG-4 | `toString()` format | matches `id/createdAt/content/authorId/chatId` |

## 5. Component Tests — `DBManager`

Use a fresh temp directory per test (e.g. `Files.createTempDirectory()`), passed as `filepath` to `DBManager`'s constructor, so tests never touch real data and can run in parallel.

| ID | Case | Expected |
|---|---|---|
| DB-1 | Construct `DBManager` against an empty/non-existent directory | starts with zero users/chats/messages; directory is created; no exception |
| DB-2 | `writeNewUser(...)` then `getUserById`/`getUserByUsername` | both return the same user |
| DB-3 | `writeNewUser` with a username that already exists | throws `IllegalArgumentException`, no duplicate written to `Users.txt` |
| DB-4 | `writeNewUser(...)` then reload: `new DBManager(samePath, ...)` | reloaded manager's `fetchAllUsers()` contains an equivalent entry (same id/fields) |
| DB-5 | After reload, `writeNewUser` again | new id does not collide with any id from before the restart (see USR-11) |
| DB-6 | `checkLoginCredentials(user, wrongPassword)` | returns `null` |
| DB-7 | `checkLoginCredentials` for a user with `isDisabled=true` | `DBManager` returns the user (disabled-check is the server's job — verify at SRV-3 instead); confirm this boundary explicitly so the two layers aren't both silently relying on the other |
| DB-8 | `updateUserIsDisabled(id, true)` then reload from disk | persisted value is `true` |
| DB-9 | `writeNewChat(ownerId, name, [otherId], false)` | returned chat contains **both** owner and other id in `getChattersIds()` even though only `otherId` was passed |
| DB-10 | `writeNewChat` with an unknown `ownerId` | throws `IllegalArgumentException` |
| DB-11 | `writeNewMessage(content, authorId, chatId)` then `fetchAllMessages(author)` | includes that message |
| DB-12 | `fetchAllChats(user)` / `fetchAllMessages(user)` for a user in **no** chats | returns empty list, not an error |
| DB-13 | `fetchAllChats(userA)` where `userA` is not in a private chat between `userB`/`userC` | that chat is absent from `userA`'s results |
| DB-14 | `addUserToChat(newUserId, chatId, ownerId)` | succeeds; chat now contains `newUserId` |
| DB-15 | `addUserToChat(newUserId, chatId, someRandomNonOwnerNonAdminId)` | throws `SecurityException` |
| DB-16 | `addUserToChat` by a requester who **is** an IT admin but not the owner | succeeds (admin override) |
| DB-17 | `removeUserFromChat` by the chat owner | succeeds, user removed |
| DB-18 | `removeUserFromChat` by a non-owner, non-admin chatter | throws `SecurityException` |
| DB-19 | `renameChat` by owner vs. by unrelated user | owner succeeds; unrelated user gets `SecurityException` |
| DB-20 | Concurrent `writeNewUser` calls from N threads on one `DBManager` | all N users are created with distinct ids; `Users.txt` on disk has exactly N lines afterward (no lost update / interleaved-write corruption) |
| DB-21 | Load a hand-corrupted `Users.txt` line (wrong field count) | fails loudly (documented exception) rather than silently loading a half-populated user — decide and assert the actual current behavior |

## 6. Integration/Protocol Tests — `Server` + `ClientHandler`

Drive these over a real socket connection to a running `Server`, sending real `Packet` objects and asserting on the `Packet`(s) received back — this is the level the earlier smoke test (`SmokeTest.java`) exercised manually; formalize it into the following cases.

### 6.1 Login / session
| ID | Case | Expected |
|---|---|---|
| SRV-1 | `LOGIN` with valid credentials | reply `Status.SUCCESS`/`ActionType.LOGIN` with `[userId, firstName, lastName, isAdmin, isDisabled]`, followed by `GET_ALL_USERS`, `GET_ALL_CHATS`, `GET_ALL_MESSAGES` in that order |
| SRV-2 | `LOGIN` with wrong password | `Status.ERROR`/`ActionType.ERROR`, no session established (subsequent action from same socket using a made-up userId is rejected/ignored) |
| SRV-3 | `LOGIN` for a disabled user | `Status.ERROR`/`ActionType.ERROR` with a message distinguishing "disabled" from "invalid credentials" |
| SRV-4 | `LOGIN` with an unknown username | `Status.ERROR`/`ActionType.ERROR` |
| SRV-5 | Two sockets `LOGIN` as the *same* user concurrently | both succeed independently (or define/assert the intended single-session policy if one is wanted — currently both are allowed; confirm that's acceptable) |
| SRV-6 | `LOGOUT` | server removes the client from its registry (`containsClient(userId)` false after); a *second* `receivePacket` sent immediately after on the same connection is not delivered to any handler (socket closes) |
| SRV-7 | Client sends a non-`LOGIN` action **before** logging in | server does not crash; either ignored or errored gracefully (no `NullPointerException` in server log) |
| SRV-8 | Abrupt socket close (no `LOGOUT`) while logged in | server detects disconnect, removes client from registry, other clients' subsequent broadcasts don't throw trying to reach the dead socket |

### 6.2 Users
| ID | Case | Expected |
|---|---|---|
| SRV-9 | Admin sends `CREATE_USER` | `NEW_USER_BROADCAST` sent to **every** currently connected client (admin and non-admin alike) |
| SRV-10 | Non-admin sends `CREATE_USER` | `Status.ERROR`, no user created, no broadcast sent to anyone |
| SRV-11 | Admin sends `CREATE_USER` with a duplicate username | `Status.ERROR`, no broadcast |
| SRV-12 | Admin sends `UPDATE_USER` to disable a user | `UPDATED_USER_BROADCAST` to all connected clients with `[userId, "true"]`; that user's next `LOGIN` attempt hits SRV-3 |
| SRV-13 | Non-admin sends `UPDATE_USER` | `Status.ERROR` |
| SRV-14 | `GET_ALL_USERS` sent mid-session (not just at login) | fresh `Status.SUCCESS`/`GET_ALL_USERS` reply to just that client |

### 6.3 Chats
| ID | Case | Expected |
|---|---|---|
| SRV-15 | `CREATE_CHAT` with one other user id, private=false | `NEW_CHAT_BROADCAST` delivered to **both** the creator and the invited user, not to a third uninvolved connected client |
| SRV-16 | `CREATE_CHAT` referencing an unknown user id in the id list | chat is created with only the resolvable members (owner at minimum); no crash |
| SRV-17 | `ADD_USER_TO_CHAT` by the chat owner | `ADD_USER_TO_CHAT_BROADCAST` to all (now including new member); newly added member separately receives a `GET_ALL_MESSAGES` packet scoped to their chats |
| SRV-18 | `ADD_USER_TO_CHAT` by a non-owner, non-admin chatter | `Status.ERROR`, no broadcast, membership unchanged |
| SRV-19 | `REMOVE_USER_FROM_CHAT` by the owner | `REMOVE_USER_FROM_CHAT_BROADCAST` reaches both the removed user and the remaining chatters |
| SRV-20 | `RENAME_CHAT` by the owner | `RENAME_CHAT_BROADCAST` to all chatters with `[chatId, newName]` |
| SRV-21 | `RENAME_CHAT` by a non-owner | `Status.ERROR` |
| SRV-22 | `GET_ALL_CHATS` for a user in zero chats | `Status.SUCCESS` with empty args list |

### 6.4 Messages
| ID | Case | Expected |
|---|---|---|
| SRV-23 | `SEND_MESSAGE` to a chat the sender belongs to | `NEW_MESSAGE_BROADCAST` delivered to every current chatter, sender included |
| SRV-24 | `SEND_MESSAGE` to a chat the sender does **not** belong to | rejected (define whether this should be a hard `Status.ERROR` today — if `DBManager.writeNewMessage` doesn't check membership, this is a gap; treat as a candidate defect, not just a test) |
| SRV-25 | `SEND_MESSAGE` with content containing `/` and multiple slashes | broadcast content, once unsanitized client-side, exactly matches what was sent |
| SRV-26 | `SEND_MESSAGE` to a non-existent `chatId` | `Status.ERROR`, no crash |
| SRV-27 | Long message content (e.g. 10,000 chars) | delivered intact, no truncation |
| SRV-28 | Empty-string message content | either rejected or delivered as empty — assert whichever is intended |

## 7. Concurrency & Multithreading

| ID | Case | Expected |
|---|---|---|
| CNC-1 | 20 clients connect simultaneously and each sends `LOGIN` within the same second | every login either succeeds or fails independently and correctly; `Server.connect()`'s accept loop keeps accepting (no dropped connections) |
| CNC-2 | 10 clients in the same chat each `SEND_MESSAGE` 20 times concurrently | all 200 messages persisted (check `Messages.txt` line count and `fetchAllMessages` size); every client receives all 200 broadcasts; no interleaved/corrupted lines in `Messages.txt` |
| CNC-3 | Admin issues `CREATE_USER` for 50 distinct usernames concurrently from 50 threads sharing one connection... | *(note: a single `ClientHandler`/socket is inherently sequential per the protocol — this case should instead use 50 separate admin-authenticated connections)*: 50 users created, 50 distinct ids, no duplicate-id or duplicate-username races |
| CNC-4 | One client sends `CREATE_CHAT` while another concurrently sends `ADD_USER_TO_CHAT` for a different chat | both operations complete independently without one blocking/corrupting the other's write to `Chats.txt` |
| CNC-5 | Kill (`SIGKILL`) the server process mid-write of a large batch of messages | on restart, `Messages.txt` is at worst missing the in-flight batch — never truncated mid-line/corrupted such that `DBManager` fails to load the rest of the file |

## 8. Persistence & Restart

| ID | Case | Expected |
|---|---|---|
| PER-1 | Fresh empty `dbFiles/development/` on server start | default `admin`/`admin` IT admin is seeded exactly once |
| PER-2 | Restart server with existing `Users.txt`/`Chats.txt`/`Messages.txt` | `admin` seed is **not** re-created (no duplicate `admin` entries); all previously created users/chats/messages are available via `GET_ALL_*` after a fresh login |
| PER-3 | Restart server, then create a new user/chat/message | new ids never collide with pre-restart ids (see USR-11, DB-5) |
| PER-4 | Manually edit `Chats.txt` to reference a `chatterId` that no longer exists in `Users.txt`, then restart | server doesn't crash on load; that dangling id is dropped or handled per whatever `DBManager.loadChats` actually does — pin down and assert the real behavior |
| PER-5 | File-format sanity: after a session covering all action types, diff `Users.txt`/`Chats.txt`/`Messages.txt` field counts per line against the documented `id/.../...` formats | every line matches its documented shape exactly |

## 9. Client & GUI (manual/system test)

Run two `ClientMain` instances against one `ServerMain`, driven manually:

| ID | Case | Expected |
|---|---|---|
| GUI-1 | Launch client, log in as `admin`/`admin` | login screen is replaced by the main chat screen; sidebar shows "Create User" button (admin-only) |
| GUI-2 | Log in as a non-admin user | main screen appears without the "Create User" button |
| GUI-3 | Log in with wrong password | error dialog appears (`showMessageDialog`); still on login screen; can retry |
| GUI-4 | Admin creates a user via the dialog | dialog closes; that username can immediately log in from a second client instance |
| GUI-5 | Create a new chat with another online user | chat appears in the correct sidebar list (Private vs. Group, based on the checkbox) on **both** clients without either needing to restart |
| GUI-6 | Send a message | appears immediately in both clients' message panel, correctly attributed to the sender, in order |
| GUI-7 | Second client sends a reply while first client has a *different* chat selected | first client's selected view does not change; switching to the chat shows the new message (verifies `update()` only re-renders the currently selected chat) |
| GUI-8 | Admin disables a user (via whatever future UI hook, or by simulating `UPDATE_USER` from a script) while that user is logged in | that user's next action gets errored server-side; their next fresh login is rejected |
| GUI-9 | `saveChatToTxt` on a chat with several messages | produces a readable local `.txt` export with one line per message, correct author names and content |
| GUI-10 | Log out, then log back in | previous chats/messages reappear identically (client-side state was rebuilt fresh from `GET_ALL_*`, not stale) |
| GUI-11 | Close the client window without logging out (`EXIT_ON_CLOSE`) | server-side eventually reflects the disconnect (see SRV-8); doesn't hang other clients |
| GUI-12 | Resize the main window / very long message content | text wraps (`setLineWrap`), no layout corruption, scrollbar reaches the latest message automatically |

## 10. Error Handling & Malformed Input (protocol-level, adversarial)

| ID | Case | Expected |
|---|---|---|
| ERR-1 | Send a `Packet` whose `actionArgs` is shorter than the handler expects (e.g. `LOGIN` with only 1 arg) | server catches `IndexOutOfBoundsException` internally (per `Server.receivePacket`'s catch clause) and responds with `Status.ERROR` rather than killing the connection or crashing the accept loop |
| ERR-2 | Send `actionArgs` with a non-boolean string where a boolean is expected (e.g. `isPrivate="maybe"`) | `Boolean.parseBoolean` silently yields `false` — confirm this is acceptable or flag as a validation gap |
| ERR-3 | Send `actionArgs` with a non-numeric string where a chat/user id is expected | downstream `Integer.parseInt`-free paths (ids are just strings) shouldn't throw; a lookup miss should produce `Status.ERROR`, not a stack trace to the client |
| ERR-4 | Open a raw socket and write garbage bytes instead of a serialized `Packet` | that one `ClientHandler` thread fails to deserialize and exits cleanly; **other** clients' connections/threads are unaffected |
| ERR-5 | Send an action type with no case in `Server.receivePacket`'s switch (currently: any broadcast-only type sent *to* the server, or `SUCCESS`) | falls into the `default` branch, `Status.ERROR` returned, no crash |

## 11. Non-Functional

| ID | Case | Expected |
|---|---|---|
| NFR-1 | 100 concurrent client connections, idle after login | server accept loop and per-connection threads remain responsive; no `OutOfMemoryError`/thread-starvation under default JVM settings |
| NFR-2 | Chat with 5,000 historical messages | `GET_ALL_MESSAGES` at login still completes in a reasonable time (define an SLA, e.g. < 2s on dev hardware) and the client GUI renders it without freezing the EDT |
| NFR-3 | `Users.txt`/`Chats.txt`/`Messages.txt` growth | confirm rewrite-whole-file persistence strategy's cost at realistic scale (e.g. 10k users) is acceptable, or flag as a scaling limit to document |

## 12. Test Data / Fixtures

- **Seed accounts**: `admin`/`admin` (IT admin, auto-seeded), plus test-created `alice`/`bob`/`carol` with known passwords for repeatable scripted runs.
- **Isolation**: every automated test run must point `DBManager` (or `ServerMain`'s hardcoded `./dbFiles/development`) at a throwaway directory — never run automated tests against the same `dbFiles/` a developer is manually using, or seed/user-id assumptions will drift.
- **Escaping fixtures**: usernames/chat names/message content containing `/`, commas, and empty strings, specifically to exercise `Packet.sanitize`/`unsanitize` and the comma-joined chatter-id format.

## 13. Tooling Recommendation

The project currently has no build tool or test framework (plain `javac`/`java`). Two additions would make this plan executable as automation rather than a manual checklist:

1. **JUnit 5** for §4–5 (unit + `DBManager`), run against temp directories.
2. A small **headless socket test harness** (extending the ad hoc smoke test used during development) for §6–8/§10 — a thin helper that opens a socket, sends a `Packet`, and asserts on the `Packet`(s) read back, without any `Client`/`GUI` involvement.

§9 (GUI) stays manual/exploratory unless a UI automation tool (e.g. AssertJ-Swing) is introduced later — not recommended unless the GUI grows significantly, given its current size.

## 14. Entry / Exit Criteria

- **Entry**: code compiles clean (`javac` with no errors; `this-escape` warnings in `Chat` are accepted/known); a throwaway `dbFiles` directory is available.
- **Exit**: all §4–8 automated cases pass; §9 manual pass completed and any defects logged; §10 adversarial cases confirmed non-fatal; any gaps identified in §6.4 (message-membership check) and §10.2 (boolean/id validation) are either fixed or explicitly accepted as known limitations before release.
