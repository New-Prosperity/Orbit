# Orbit
Standalone Minestom game server for the Nebula network. Runs on every server — hubs and game servers. `java -jar orbit.jar` starts the server with all shared infrastructure. Features are implemented as hot-swappable `OrbitModule`s registered into Ether's `ModuleRegistry`.

## Build
- Plugins: kotlin-jvm 2.3.10, maven-publish, application, shadow 9.0.0-beta12
- Deps: Gravity (implementation), Minestom 2026.02.09-1.21.11, adventure-text-minimessage 4.20.0
- Main class: `me.nebula.orbit.Orbit`
- JVM toolchain: 25 (required by Minestom)
- Gradle wrapper: 9.3.1
- Shadow bundles Gravity/Ether/Hazelcast/Minestom. `mergeServiceFiles()` for Hazelcast Enterprise.

## Entry — `Orbit.kt`
- `object Orbit` with `@JvmStatic fun main()`.
- All env vars via Ether's `environment {}` DSL: `SERVER_NAME` (required), `GAME_MODE` (optional, empty = hub), `SERVER_PORT` (optional, default 25565), `HAZELCAST_LICENSE`.
- `serverName` (lateinit), `gameMode` (nullable), `isGameServer` (derived getter).
- Locale cache: `localeOf(uuid)`, `cacheLocale(uuid, locale)`, `evictLocale(uuid)`, `deserialize(key, locale, resolvers)`.
- Hazelcast: lite member with 17 stores (PlayerStore, SanctionStore, SessionStore, ServerOccupancyStore, EconomyStore, EconomyTransactionStore, PropertyStore, RankStore, PlayerRankStore, PreferenceStore, QueueStore, PoolConfigStore, RankingStore, StatsStore, RankingReportStore, ServerStore, ProvisionStore).
- Init order: `environment {}` → `appDelegate` (Hazelcast + translations only) → `app.start()` → `MinecraftServer.init()` → create instance → core player lifecycle on global handler → OnlinePlayerCache scheduler (5s) → `app.modules.enableAll()` → `server.start()` → publish `ServerRegistrationMessage` → shutdown hook (publish `ServerDeregistrationMessage` → `app.modules.disableAll()` + `app.stop`).
- Self-registration: reads `P_SERVER_UUID` env var (Pterodactyl container). On startup publishes `ServerRegistrationMessage(serverUuid)` to trigger immediate Pulsar sync. On shutdown publishes `ServerDeregistrationMessage(serverUuid)` before stopping.
- Core player lifecycle (directly on global event handler, not in a module):
  - `AsyncPlayerConfigurationEvent`: loads locale from PlayerStore, caches it, sets spawning instance + respawn point.
  - `PlayerDisconnectEvent`: evicts locale cache.

## Module System — `module/`

### `OrbitModule` — `module/OrbitModule.kt`
Extends Ether's `Module(name, canReload = true)`. Adds Minestom-specific lifecycle:
- `eventNode: EventNode<Event>` — scoped via `EventNode.all(name)`. Entire subtree attaches/detaches in one operation.
- `commands(): List<Command>` — override to provide commands (registered/unregistered on enable/disable).
- `onEnable()` — attaches eventNode to global handler, registers commands. Subclasses override to add listeners on `eventNode`.
- `onDisable()` — unregisters commands, detaches eventNode.
- `onReload()` — full teardown + rebuild (onDisable → onEnable).
- Inherits from Module: `logger`, `isEnabled`, `name`, `canReload`, state guards.

Managed via `app.modules` (Ether's `ModuleRegistry`):
- `app.modules.register(module)` — register at runtime.
- `app.modules.unregister(name)` — must be disabled first.
- `app.modules.enable(name)` / `disable(name)` / `reload(name)` — individual hot-swap.
- `app.modules.enableAll()` / `disableAll()` — bulk lifecycle.
- `app.modules.get<T>()` / `require<T>()` — reified type lookup.

## Command DSL — `command/CommandDsl.kt`
- `minestomCommand(name, aliases) { permission; playerOnly; execute {}; suggest {} }` — mirrors Horizon's `velocityCommand`.
- `OrbitCommandContext(player, args, locale)` — context for execute block.
- Permission checks via `RankManager.hasPermission()` (Gravity rank system, cached).
- Dispatches execute/suggest to virtual threads.
- `OnlinePlayerCache` — refreshes from `SessionStore.all()` via Minestom scheduler every 5s.
- `suggestPlayers(prefix)` — filters OnlinePlayerCache names.

## Player Resolver — `command/PlayerResolver.kt`
- `resolvePlayer(name): Pair<UUID, String>?` — uses Minestom's `findOnlinePlayer()` with exact name match guard, then `PlayerStore` via `PlayerNamePredicate`.

## File Tree
```
src/main/kotlin/me/nebula/orbit/
├── Orbit.kt                        (entry point, Hazelcast, Minestom, core lifecycle)
├── module/
│   └── OrbitModule.kt              (abstract hot-swappable module, extends Ether Module)
└── command/
    ├── CommandDsl.kt               (minestomCommand DSL, OnlinePlayerCache)
    └── PlayerResolver.kt           (resolvePlayer helper)
```
