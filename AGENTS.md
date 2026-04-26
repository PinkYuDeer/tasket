# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project

**Tasket** (`modid: tasket`, group `com.pinkyudeer.tasket`) is a Minecraft **1.7.10 / Forge 10.13.4.1614** mod targeting the GTNH 2.7.x modpack. It implements a collaborative in-game task list backed by SQLite, with a ModularUI2 GUI and optional integrations with BetterQuesting and GTNHLib's Team API. The README lives in `README.md` (English) and `README-zh_CN.md`. Comments and design docs (`docs/`) are largely written in Chinese.

## Build / run / test

The build uses `com.gtnewhorizons.gtnhconvention` (RetroFuturaGradle). Java is set to `enableModernJavaSyntax = jabel` and `usesMixins = false` — do not flip the mixin flag back on without supplying real mixin classes.

On Windows use `gradlew.bat`; on POSIX use `./gradlew`.

```bash
gradlew.bat build --no-daemon          # full build (includes tests + spotless)
gradlew.bat test --no-daemon           # JUnit tests only
gradlew.bat spotlessCheck --no-daemon  # formatting check
gradlew.bat spotlessApply              # auto-format
gradlew.bat runClient                  # launch dev client (run/client)
gradlew.bat runServer                  # launch dedicated dev server (run/server)
```

Run a single test class / method:

```bash
gradlew.bat test --tests com.pinkyudeer.tasket.task.service.TagServiceTest
gradlew.bat test --tests "com.pinkyudeer.tasket.task.service.TeamServiceTest.someMethod"
```

Tests live in `src/test/java`; `test/SqliteTestSupport` boots a pure in-memory SQLite — tests must NOT depend on a Minecraft world directory. CI runs `GTNewHorizons/GTNH-Actions-Workflows` build-and-test on PRs and pushes to `main`/`master`.

The mod version is injected via `generateGradleTokenClass = com.pinkyudeer.tasket.Tags` (token `VERSION`); reference it as `Tags.VERSION` rather than hardcoding a version string.

## Architecture

### Lifecycle and proxies

`Tasket.java` is a thin `@Mod` shell — every Forge lifecycle event (`construct` → `preInit` → `init` → `postInit` → `serverStarting` → `serverStopping`) is delegated to `core/CommonProxy` (server) or `core/ClientProxy` (client, via `@SidedProxy`). Common code MUST NOT import `net.minecraft.client.*` — client-only behavior (keybinds, GUI registration, `ClientTasketPacketHandler`, blur/shaders) lives behind `ClientProxy` and the `client/` package. Breaking this rule will dedicated-server-crash on classloading.

The world database is bound to world load/save events (`EventHandler`): on `WorldEvent.Load` for the overworld, `SQLiteManager.initSqlite()` either creates a fresh in-memory DB and runs `TaskSqlHelper.initTaskDataBase()` (scans `com.pinkyudeer.tasket.task.entity` for `@Table`), or restores `tasket/main.db` and runs `TaskSqlHelper.migrateSchema()`. On save the in-memory DB is backed up to file; on unload the connection is closed. Player login triggers `PlayerDao.updateOrInsert`, optional external-team sync, and a `main_sync` reset packet.

### Persistence layer (`db/`)

Self-rolled annotation-driven SQLite ORM — there is no JPA/Hibernate.

- Annotations: `@Table`, `@Column` (name/default/PK/unique/index), `@Reference` (FK), `@FieldCheck` (generates SQLite `CHECK` constraints — UUID length, enum membership, range, string length).
- `SQLUtils` maps Java types to SQLite types: enums and `UUID` → `TEXT`, `LocalDateTime` → `TIMESTAMP`, `Duration` → `REAL`.
- `builder/` holds the fluent SQL builders (`CreateTableBuilder` topo-sorts by FK dependencies; `Select/Insert/Update/Delete/AlterTable/DropTableBuilder`).
- `EntityHandler` reflectively maps `ResultSet` rows to entities via `UtilHelper.convertValue`.
- `SQLiteManager` is the only safe entry point for queries: prefer `SQLiteManager.query(sql, rsHandler, args)` and `SQLiteManager.transaction(...)` over the legacy `executeSafeSQL` / raw `ResultSet` paths, since the callback-based API guarantees `PreparedStatement`/`ResultSet` cleanup. New multi-table writes must run inside `transaction(...)`.
- Schema migrations are tracked in a `schema_version` table and must remain idempotent — re-running `migrateSchema()` cannot duplicate columns or alterations.

`docs/TaskSqlHelper使用指南.md` documents the canonical query/transaction patterns. Older API references (`TaskSqlHelper.entity(...)`, `ConvenienceMethods`) no longer exist.

### Domain model (`task/`)

- `task/entity/` — `Task`, `Player`, `Team`, `Tag` (top-level entities).
- `task/entity/record/` — junction/audit rows: `BaseRecord` plus `TaskInteraction`, `PlayerInteraction`, `TeamMember`, `TeamRequest`, `TagLink`, `Notification`, `StatusChangeRecord`.
- `task/dao/` — one DAO per entity; record DAOs under `task/dao/record/`.
- `task/service/` — **the only correct write entry points**: `TaskService`, `TeamService`, `TagService`. GUI and packet handlers must funnel writes through these (they enforce `PermissionContext` and wrap multi-table writes in transactions). DAO calls from the GUI or directly from network handlers are a regression.
- `task/team/` — `TeamProvider` abstraction with `LocalTeamProvider` and the `TeamProviders` registry; this is how external team sources plug in.
- `task/TaskSqlHelper` — only owns DB init + migration, not query helpers.

ID-type quirk worth knowing: `Task.id` and `Task.parentTaskId` are `String`, while most other ID fields (and `TaskInteraction.parentTaskId`) are `UUID`. Conversions happen at service boundaries — be deliberate when adding new code that crosses this boundary.

### External team integrations (`integration/`)

Tasket's local model allows a player in multiple teams; external sources are treated as read-only member providers.

- `integration/betterquesting/BetterQuestingTeamProvider` — links by BQ party (int id, stored in `Team.externalPartyId`).
- `integration/gtnhlib/GtnhLibTeamProvider` + `GtnhLibTeamDataBridge` — links by `teamName` (stored in `Team.externalTeamKey`); `GtnhLibTeamDataBridge.register()` runs in `postInit` and registers the `tasket` data key.

Both providers reach the external mod **only via reflection**. Core services (`task/service/*`) MUST NOT directly import BQ or GTNHLib classes — when those mods are absent, the provider goes unavailable and the team is marked `STALE` rather than crashing. Run a no-BQ/no-GTNHLib dedicated server before claiming integration changes are safe.

### Network layer (`network/` + `client/network/`)

Architecturally modeled on BetterQuesting:

- A single `tasket` `SimpleNetworkWrapper` channel registers exactly one Forge message: `TasketPacket`, which carries an `NBTTagCompound` plus a `ResourceLocation` `ID`.
- `PacketTypeRegistry` dispatches by `ID` to handlers in `network/handler/` (server-side) or `client/network/ClientTasketPacketHandler` (client-side). Server and client handler tables are kept separate to prevent side leakage.
- Packet families: `main_sync`, `task_action`, `task_sync`, `team_action`, `team_sync`, `invite_sync`, `error`.
- `PacketAssembly` compresses NBT and slices at 20480 bytes, reassembling per-player by UUID — large sync payloads no longer use Kryo `Object` packets.
- `ServerTaskScheduler` (registered in `serverStarting`) hops server-side handler work onto the server tick thread, so DB writes never run on the network thread. New server handlers should follow this pattern.
- `task_sync` already filters by visibility (OP, creator, public, team membership). Maintain that filter when extending sync.
- `network/NetWorkData.java` and `network/NetWorkHelper.java` are **legacy compatibility shims** scheduled for removal once GUI/command paths fully migrate; do not build new features on top of them.

### Client store + GUI (`client/`, `gui/`)

- `client/TaskClientStore` is a read-only mirror of server-pushed data; `client/TaskClientActions` builds outgoing `task_action` packets. The GUI must read from the store and write via actions — never call services directly from client code.
- `gui/screen/TaskScreen` wraps a ModularUI2 screen with a fade-out close. Panels under `gui/panel/` (`MainPanel`, `TaskDetailPanel`, `TaskFormPanel`) consume `TaskClientStore`.
- `gui/drawable/`, `render/ShaderHelper`, `render/BlurHandler`, `render/GLShaderDrawHelper` provide rounded panels, shadows, and blurred backgrounds; GLSL shaders live in `src/main/resources/assets/tasket/shaders/`.
- UI strings should land in `assets/tasket/lang/{en_US,zh_CN}.lang` rather than being hardcoded in panels.

### Game-content registration (`loader/`, `inGame/`, `config/`, `helper/`)

- `loader/{BlockLoader,ItemLoader,CreativeTabsLoader,RecipeLoader}` run in `preInit`/`init`; `CommandLoader` runs in `serverStarting`.
- `inGame/block/`, `inGame/item/` hold the registered blocks/items.
- `config/ConfigHelper` + `config/ConfigEntry` wrap Forge configuration; `core/ConfigSetting` exposes typed values.
- `helper/` holds cross-cutting utilities: `CommandHelper`, `ModFileHelper` (mod paths — note `ModFileHelper.init()` is called in `preInit`), `PlayerHelper`, `UtilHelper` (the type-conversion routine the ORM relies on).

## Conventions

- Logging: use `Tasket.LOG` with `{}` placeholders, e.g. `LOG.info("Registered {} items", count)`. Don't string-concatenate into log messages.
- Editor config: 4-space indent for Java; 2-space for `*.{json,xml,yml,md,...}` (see `.editorconfig`). `*.lang` keeps trailing whitespace — Spotless respects this.
- Comments and design docs are often in Chinese; preserve that style when editing existing files rather than translating in passing.
- `docs/plan.md` is the active release punch list; `docs/future.md` tracks deferred ideas; `docs/技术细节.md` is the long-form architecture writeup and is the authoritative source if this file conflicts with reality.
