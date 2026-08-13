# Legacy

Files and directories that are no longer part of the live project but were kept rather than
deleted, since they're historical records of the pre-migration architecture or the migration
process itself. Nothing in `backend/src` or `frontend/src` reads or references anything under
this folder. See [MigrationPlan.md](../MigrationPlan.md) for the full narrative these all belong
to.

| Path | Originally at | Purpose |
|---|---|---|
| [TEST_PLAN.md](TEST_PLAN.md) | repo root | The original test plan, written for the pre-migration socket/Swing architecture (`Packet`/`Server`/`ClientHandler`/`Client`/`GUI`/flat-file `DBManager`). Superseded by the automated suites under `backend/src/test/java` and `frontend/src/**/*.test.js`; kept as a historical reference rather than rewritten, per [MigrationPlan.md](../MigrationPlan.md) Phase 5. |
| [CutoverReferenceMap.md](CutoverReferenceMap.md) | repo root | The one-time reference audit performed before deleting the legacy socket/Swing code in Phase 5 — mapped every remaining call site (including non-obvious ones, like `packet.Packet.sanitize` leaking into the domain model) to confirm what was actually safe to remove. Purely a record of that audit; nothing depends on it now. |
| [dbFiles/development/](dbFiles/development/) (`Chats.txt`, `Messages.txt`, `Users.txt`) | `dbFiles/development/` at repo root | The legacy flat-file persistence store `DBManager` read/wrote before Phase 1 migrated it to PostgreSQL. Kept as a historical data snapshot rather than deleted; nothing in the current app reads it (Postgres has been the sole source of truth since Phase 1). |
| [out/](out/) | repo root | Compiled `.class` output from the original `javac`/`java` (no build tool) build of the pre-Gradle Java project — `client.*`, `server.*`, `packet.*`, `model.*`. That source was deleted outright in Phase 5's cutover, so these binaries are now the only on-disk trace of it (the source itself is still recoverable from git history before that phase). Not used by the current Gradle build in any way. |
| [test-out/](test-out/) | repo root | Compiled `.class` output from the pre-Gradle project's JUnit tests (`model/*Test`, `packet/PacketTest`, `server/*Test`, `support/*`), same status as `out/` above — build artifacts of deleted source, kept rather than deleted. |

One thing this table intentionally omits: a stray `dbFiles/.DS_Store` (a macOS Finder artifact,
not a project file) was removed rather than moved here — it never had project-relevant content to
preserve, unlike everything else in this folder.
