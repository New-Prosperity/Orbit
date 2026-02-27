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
- Env vars: `HAZELCAST_LICENSE` (required), `VELOCITY_SECRET` (required), `SERVER_PORT` (default 25565), `P_SERVER_UUID` (optional), `SERVER_HOST` (optional).
- **Provision self-discovery**: When `P_SERVER_UUID` is set, Orbit looks up `ProvisionStore.load(serverUuid)` from the distributed Hazelcast store (populated by Pulsar's `ServerSynchronizer.sync()`), deriving `serverName` from the provision's server name and `gameMode` from `provision.metadata["game_mode"]`. No direct Proton API call needed — Orbit relies on the cluster-synced store. When `P_SERVER_UUID` is empty or no provision found, `serverName` defaults to `"orbit-local"` and `gameMode` to `null` (hub mode).
- Hazelcast: lite member with 18 stores (includes ReconnectionStore).
- Init order: `environment {}` → `appDelegate` → `app.start()` → provision self-discovery → `MinecraftServer.init()` → `resolveMode()` → ensure `data/models/` directory → `ModelEngine.install()` → `CustomContentRegistry.init()` (loads items, blocks, armors) → load `.bbmodel` model files → `CustomContentRegistry.mergePack()` (generates armor shaders, merges all) → register commands (model, cc, cinematic, screen, armor) → `mode.install(handler)` → common listeners → `server.start()` → registration → shutdown hook.
- **Resource pack**: Pack is merged at startup but NOT sent from Orbit — pack distribution is delegated to the proxy.
- Common listeners: `AsyncPlayerConfigurationEvent` (locale cache, set spawning instance/respawn from mode), `PlayerDisconnectEvent` (evict locale), `OnlinePlayerCache` refresh (5s).
- `resolveMode()` selects `ServerMode` by `gameMode` (sourced from Proton provision `metadata["game_mode"]`, NOT an env var): `null` → `HubMode(app.resources)`, `"hoplite"` → `HopliteMode(app.resources)`, else → `error()`.

## Server Mode System — `mode/`

### `ServerMode` interface (`mode/ServerMode.kt`)
```kotlin
interface ServerMode {
    val defaultInstance: InstanceContainer
    val spawnPoint: Pos
    val cosmeticConfig: CosmeticConfig  // default = all enabled
    fun install(handler: GlobalEventHandler)
    fun shutdown()
}
```
Each mode owns its instance, event listeners, scheduled tasks, and shutdown. `Orbit.kt` delegates to it. `cosmeticConfig` has a default implementation returning `CosmeticConfig()` (all categories enabled, empty blacklist) so existing/future modes work without changes.

### Config-Driven Mode System — `mode/config/`

#### Shared Config Data Classes (`mode/config/ModeConfig.kt`)
- `SpawnConfig(x, y, z, yaw, pitch)` — spawn coordinates. `toPos()` converts to Minestom `Pos`.
- `ScoreboardConfig(title, refreshSeconds, lines)` — scoreboard layout.
- `TabListConfig(refreshSeconds, header, footer)` — tab list layout.
- `LobbyConfig(gameMode, protectBlocks, disableDamage, disableHunger, lockInventory, voidTeleportY)` — lobby protection settings.
- `HotbarItemConfig(slot, material, name, glowing, action)` — hotbar item definition.
- `CosmeticConfig(enabledCategories, blacklist)` — per-mode cosmetic filtering. `enabledCategories` lists allowed `CosmeticCategory` names (default: all five). `blacklist` lists specific cosmetic IDs to block even if category is enabled. Both default to all-enabled / empty for backwards compatibility.

#### Placeholder System (`mode/config/PlaceholderResolver.kt`)
- `{placeholder}` syntax in config strings (curly braces, distinct from MiniMessage `<tag>`).
- `placeholderResolver { global("name") { value }; perPlayer("name") { player -> value } }` DSL.
- `resolve(template, player?)` — replaces `{name}` tokens with provider values.
- `hasPlaceholders(template)` — determines static vs dynamic lines at build time.
- Lines without placeholders become `LiveLine.Static`; lines with placeholders become `LiveLine.Dynamic`. All placeholder lines resolve with the player context.

### `HubMode` (`mode/hub/HubMode.kt`)
- **Config-driven**: All settings loaded from `modes/hub.json` via `ResourceManager.loadOrCopyDefault<HubModeConfig>("hub.json", "modes/hub.json")`. First run copies bundled default from classpath to `data/modes/hub.json`. Operators edit the JSON file without code changes.
- **Config data classes**: `HubModeConfig` (`mode/hub/HubModeConfig.kt`) wraps `SpawnConfig`, `ScoreboardConfig`, `TabListConfig`, `LobbyConfig`, `List<HotbarItemConfig>`, `SelectorConfig(title, rows, border)`, `CosmeticConfig`.
- **Default config**: `src/main/resources/hub.json` bundled in JAR.
- **Placeholders**: Global `{online}` (SessionStore.cachedSize), `{server}` (Orbit.serverName). Per-player `{rank}` (PlayerRankStore/RankStore lookup).
- **Action system**: Hotbar items reference click actions by string name (e.g., `"open_selector"`). Immutable actions map built locally in `install()`. Unknown actions and invalid material keys logged as warnings.
- **Hub instance**: Validates and loads Anvil world from `config.worldPath` (requires `region/` with `.mca` files), preloads chunks with `config.preloadRadius`, verifies block data post-load. Falls back to flat grass generator if directory missing.
- **Lobby**: Built from `config.lobby` — `GameMode.valueOf(config.lobby.gameMode)`, all protection flags from config.
- **Scoreboard**: Built from `config.scoreboard` — title and lines support `{placeholder}` syntax, static vs dynamic determined by placeholder presence.
- **Tab list**: Built from `config.tabList` — same placeholder resolution for header/footer.
- **Hotbar**: Built from `config.hotbar` — `Material.fromKey()`, maps action strings via registered actions.
- **Server selector**: Built from `config.selector` — title, rows, border material.
- No `MechanicLoader` — hub mode has no mechanics enabled.
- `shutdown()` uninstalls scoreboard, tab list, lobby, and hotbar.

### Game Engine — `mode/game/`
Abstract lifecycle framework for minigames. Concrete modes subclass `GameMode` and implement only game-specific logic; the base class owns phases, player tracking, countdowns, timers, and cleanup.

#### Phase Lifecycle
```
WAITING ──(minPlayers)──> STARTING ──(countdown)──> PLAYING ──(win / timer)──> ENDING ──(end timer)──> WAITING
```
- **WAITING**: Lobby protection, scoreboard/tablist/hotbar. Tracks players. → STARTING when `tracker.aliveCount >= minPlayers`.
- **STARTING**: Countdown (`timing.countdownSeconds`). Cancels → WAITING if players drop below `minPlayers`.
- **PLAYING**: `onGameSetup(players)` for game-specific prep. Grace period + game timer if configured. `eliminate(player)` → spectator, check win condition.
- **ENDING**: `MatchResultDisplay.broadcast()`. End countdown, then reset → WAITING.

#### `GamePhase.kt`
`enum class GamePhase { WAITING, STARTING, PLAYING, ENDING }`

#### `GameSettings.kt`
- `TimingConfig(countdownSeconds, gameDurationSeconds, endingDurationSeconds, gracePeriodSeconds, minPlayers, maxPlayers, allowReconnect, disconnectEliminationSeconds, reconnectWindowSeconds)` — 0 = unlimited/disabled for duration fields. `allowReconnect` (default `true`): when `false`, disconnecting during PLAYING immediately eliminates instead of allowing reconnection. `disconnectEliminationSeconds` (default `0`): per-player auto-elimination timer after disconnect; 0 = no auto-elimination. `reconnectWindowSeconds` (default `0`): game-wide window after PLAYING starts; after expiry, all disconnected players are eliminated and new disconnects are instant eliminations; 0 = unlimited.
- `GameSettings(worldPath, preloadRadius, spawn, scoreboard, tabList, lobby, hotbar, timing, cosmetics)` — wraps all config types from `mode/config/`. `cosmetics` defaults to `CosmeticConfig()` (all enabled).

#### `PlayerTracker.kt`
- `sealed interface PlayerState { Alive, Spectating, Disconnected(since) }`
- `PlayerTracker` — `ConcurrentHashMap<UUID, PlayerState>`. Properties: `alive`, `spectating`, `disconnected` (Set<UUID>), `aliveCount`, `size`. Methods: `join`, `eliminate`, `disconnect`, `reconnect`, `remove`, `stateOf`, `isAlive`, `isSpectating`, `isDisconnected`, `contains`, `clear`.

#### `GameMode.kt`
Abstract `ServerMode` implementation. Lazy fields: `spawnPoint`, `defaultInstance` (AnvilWorldLoader), `resolver`, `stateMachine` (GameStateMachine<GamePhase>).

**Composed utilities** (managed by base):

| Utility | Phase | Notes |
|---|---|---|
| `Lobby` | WAITING | From `settings.lobby`. Created on WAITING enter, uninstalled on STARTING enter |
| `Hotbar` | WAITING | Via `buildLobbyHotbar()` (open, null default). Uninstalled on PLAYING enter |
| `LiveScoreboard` | All | Built once in `install()` from `settings.scoreboard` + resolver |
| `LiveTabList` | All | Built once in `install()` from `settings.tabList` + resolver |
| `Countdown` | STARTING | `timing.countdownSeconds`. Cancels → WAITING if players drop |
| `MinigameTimer` | PLAYING | `timing.gameDurationSeconds`. 0 = not created. Expiry → ENDING |
| `GracePeriodManager` | PLAYING start | `timing.gracePeriodSeconds`. 0 = skipped |
| `MatchResultDisplay` | ENDING | Broadcasts result to all tracked players |
| `Countdown` | ENDING | `timing.endingDurationSeconds`. Complete → reset to WAITING |

**Abstract** (must implement): `settings: GameSettings`, `buildPlaceholderResolver()`, `onGameSetup(players)`, `checkWinCondition(): MatchResult?`.

**Open** (default no-op): `onWaitingStart()`, `onPlayerJoinWaiting()`, `onPlayerLeaveWaiting()`, `onCountdownTick()`, `onPlayingStart()`, `onPlayerEliminated()`, `onPlayerDisconnected()`, `onPlayerReconnected()`, `onEndingStart()`, `onEndingComplete()`, `onGameReset()`, `buildLobbyHotbar(): Hotbar?`, `buildTimeExpiredResult(): MatchResult`.

**Public API**: `eliminate(player)`, `forceEnd(result)`, `phase: GamePhase`, `tracker: PlayerTracker`, `gameStartTime: Long`.

### `HopliteMode` (`mode/game/hoplite/HopliteMode.kt`)
FFA last-player-standing battle royale. First concrete `GameMode` implementation.

- **Config-driven**: Loaded from `modes/hoplite.json` via `ResourceManager.loadOrCopyDefault<HopliteModeConfig>("hoplite.json", "modes/hoplite.json")`.
- **Config data classes**: `HopliteModeConfig` (`mode/game/hoplite/HopliteModeConfig.kt`) wraps `SpawnConfig`, `List<SpawnConfig>` (spawn points), `ScoreboardConfig`, `TabListConfig`, `LobbyConfig`, `List<HotbarItemConfig>`, `TimingConfig`, `BorderConfig`, `KitConfig`, `CosmeticConfig`.
  - `BorderConfig(initialDiameter, finalDiameter, centerX, centerZ, shrinkStartSeconds, shrinkDurationSeconds)` — world border shrink phase.
  - `KitConfig(helmet?, chestplate?, leggings?, boots?, items: List<KitItemConfig>)` — starter gear. Materials as key strings (`minecraft:iron_sword`).
  - `KitItemConfig(slot, material, amount)` — inventory slot item.
- **Default config**: `src/main/resources/hoplite.json` — flat world, 16 spread spawn points, iron gear kit, 200→20 border shrink over 5min after 1min delay, 10min game, 15s grace, 2-16 players, 10s countdown.
- **Placeholders**: Global `{online}`, `{server}`, `{alive}`, `{phase}`. Per-player `{kills}`.
- **Gameplay**:
  - WAITING: lobby protection, scoreboard, tab list (all managed by base `GameMode`).
  - STARTING: 10s countdown, cancels if players drop below minimum.
  - PLAYING: players shuffled and teleported to spawn points, starter kit applied via `Kit` DSL, `ManagedWorldBorder` initialized. Grace period prevents damage for first 15s (managed by base `GracePeriodManager`). After `shrinkStartSeconds`, border shrinks to `finalDiameter` over `shrinkDurationSeconds`.
  - Kill tracking: `EntityDamageEvent` listener via `EventNode.all()` on global handler (matches `DeathMessageHandler` / `OrbitModule` patterns). Tags target with `Tag<UUID>` of last attacker (same pattern as `DeathMessageHandler`). On lethal damage: cancels event, heals target, credits killer via `StatTracker`, calls `eliminate()`.
  - Elimination: broadcasts per-player translated message (`orbit.game.hoplite.elimination` via `Player.translate()`), switches eliminated player to spectator.
  - Win condition: `tracker.aliveCount <= 1` → winner is last alive. On timer expiry (`MinigameTimer`): player with most kills wins via `StatTracker.top()`.
  - ENDING: `MatchResult` (via `matchResult {}` DSL) with winner, kill stats, game duration. `MatchResultDisplay` broadcasts titles + chat summary.
- **Reconnection**: disabled (`allowReconnect = false`). Disconnect during PLAYING = immediate elimination.
- **Mechanics**: delegates to Orbit's mechanic system (`combat`, `armor`, `fall-damage`, `food`, `projectile`, `shield`, `sprint`, `void-damage` etc. — all enabled when `GAME_MODE` is set).
- **Translation keys**: `orbit.game.hoplite.elimination` — registered in `OrbitTranslations.game()`.
- **Utilities reused**: `GameMode` (base), `Kit` DSL, `ManagedWorldBorder` DSL, `StatTracker`, `PlaceholderResolver` DSL, `MatchResult` DSL, `delay()` scheduler, `TranslationRegistry` via `Player.translate()`, `Tag<UUID>` (DeathMessageHandler pattern), `EventNode.all()` (OrbitModule pattern).
- **Activation**: `resolveMode()` in `Orbit.kt` maps `"hoplite"` → `HopliteMode(app.resources)`. Set via Proton provision `metadata["game_mode"] = "hoplite"`.

## Module System — `module/OrbitModule.kt`
Extends Ether's `Module(name, canReload = true)`. Scoped `EventNode<Event>` attaches/detaches in one operation. Override `commands()` to provide commands. Managed via `app.modules`.

### Lifecycle Hooks
- **`onPlayerDisconnect(callback: (Player) -> Unit)`** — registers a lambda called on `PlayerDisconnectEvent`. Only adds the listener if any callbacks are registered.
- **`ConcurrentHashMap<K, *>.cleanOnInstanceRemove(extractHash: (K) -> Int)`** — registers a map for periodic stale-instance eviction. A 30-second sweep task compares map keys against live instances and removes entries whose instance no longer exists.
- **`MutableSet<K>.cleanOnInstanceRemove(extractHash: (K) -> Int)`** — same for `ConcurrentHashMap.newKeySet()` sets.
- Sweep task only created if maps/sets registered. Disconnect listener only registered if callbacks exist. Both scoped to module lifecycle.

## Mechanic System — `mechanic/` (263 modules)
Modular vanilla Minecraft mechanics. Each is an `OrbitModule` subclass using scoped `EventNode` and `Tag<T>` for state.

### Configuration
- `MECHANICS` env var: csv of module names. If unset + `GAME_MODE` set: all 263 enabled. If unset + no `GAME_MODE`: none.
- All mechanics: `combat`, `fall-damage`, `block`, `food`, `item-drop`, `death`, `experience`, `armor`, `projectile`, `void-damage`, `fire`, `drowning`, `sprint`, `weather`, `day-night`, `bed`, `explosion`, `natural-spawn`, `container`, `crafting`, `potion`, `crop`, `fishing`, `anvil`, `breeding`, `beacon`, `enchanting`, `respawn-anchor`, `trading`, `door`, `shield`, `enderpearl`, `snowball`, `elytra`, `trident`, `crossbow`, `lever`, `noteblock`, `composter`, `sign`, `furnace-smelting`, `armor-stand`, `painting`, `lectern`, `jukebox`, `cauldron`, `item-frame`, `campfire`, `grindstone`, `stonecutter`, `loom`, `cartography-table`, `smithing-table`, `ender-chest`, `bell`, `candle`, `shulker-box`, `scaffolding`, `boat`, `ladder`, `dispenser`, `hopper`, `conduit`, `copper-oxidation`, `turtle-egg`, `beehive`, `lightning-rod`, `gravity`, `bonemeal`, `piston`, `redstone-wire`, `minecart`, `armor-trim`, `firework`, `snow-layer`, `ice`, `cactus`, `vine-growth`, `bamboo`, `sugarcane`, `mushroom`, `coral`, `dragon-breath`, `wither`, `water-flow`, `lava-flow`, `bubble-column`, `item-durability`, `double-chest`, `cobweb`, `hoe-tilling`, `axe-stripping`, `shovel-pathing`, `protection-enchant`, `fire-aspect`, `thorns`, `mending`, `silk-touch`, `sweeping`, `sharpness`, `knockback-enchant`, `power`, `flame`, `infinity`, `fortune`, `efficiency`, `unbreaking`, `looting`, `loyalty`, `channeling`, `quick-charge`, `wind-charge`, `mace`, `trial-spawner`, `sculk-catalyst`, `copper-bulb`, `crafter`, `vault`, `ominous-bottle`, `item-frame-rotation`, `suspicious-stew`, `sculk-vein`, `breeze`, `spore-blossom`, `sculk-sensor-calibrated`, `brushable`, `frog`, `allay`, `warden`, `smelting-drop`, `nether-wart`, `sniffer`, `camel`, `hanging-block`, `mud`, `mangrove`, `cherry`, `pitcher-plant`, `torchflower`, `wax`, `azalea`, `bamboo-mosaic`, `recovery-compass`, `echo-shard`, `slime-block-bounce`, `noteblock-instrument`, `dragon-egg`, `respawn-module`, `head-drop`, `compass-tracking`, `spyglass`, `goat-horn`, `bundle`, `amethyst-growth`, `light-block`, `structure-void`, `barrier-block`, `command-block`, `skull`, `netherite-damage`, `sweet-berry-bush`, `powder-snow-freezing`, `drowned-conversion`, `piglin-barter`, `enderman-pickup`, `creeper-explosion`, `skeleton-shoot`, `zombie-attack`, `wither-skeleton`, `blaze-behavior`, `ghast-behavior`, `slime-split`, `phantom`, `vex`, `spider-climb`, `guardian-beam`, `shulker-bullet`, `witch-potion`, `evoker-fangs`, `ravager`, `pillager-crossbow`, `snow-golem`, `fox-sleep`, `parrot-dance`, `dolphin-grace`, `bee-pollination`, `silverfish-burrow`, `cat-creeper`, `turtle-scute`, `villager-profession`, `wandering-trader`, `raid-system`, `elder-guardian`, `zombie-siege`, `piglin-aggro`, `hoglin-behavior`, `strider-behavior`, `wolf-taming`, `cat-taming`, `horse-taming`, `llama-behavior`, `panda-behavior`, `polar-bear`, `axolotl-behavior`

### Core Mechanics (19)
| Module | Summary |
|---|---|
| `combat` | Melee damage, attack cooldown, crits (1.5x), knockback, 500ms iframes |
| `fall-damage` | Fall height tracking, landing damage `(startY - landY - 3)` |
| `food` | Hunger, eating via DataComponents, exhaustion, regen, starvation. Exposes `Player.addExhaustion()` |
| `item-drop` | Drop/pickup items, 2s pickup delay, 5min despawn |
| `block` | Break drops via BlockDropData, tool requirement, placement validation |
| `death` | Death inventory scatter, respawn reset (heal, food=20, saturation=5) |
| `experience` | XP orbs, vanilla level formula, `Player.giveExperience()` |
| `armor` | Vanilla damage reduction via Attribute.ARMOR/ARMOR_TOUGHNESS |
| `projectile` | Bow charging, arrow spawning with velocity, damage scaling |
| `void-damage` | Kill below Y=-64 |
| `fire` | Fire/lava contact damage via `entityMeta.setOnFire(true)` |
| `drowning` | Air supply (300), drowning damage at 0 |
| `sprint` | Sprint exhaustion, auto-stop at food<=6 |
| `weather` | Per-instance weather cycling (clear→rain→thunder) |
| `day-night` | `instance.timeRate = 1` for vanilla day/night |
| `bed` | Respawn point, sleep skip at >=50% sleeping |
| `explosion` | TNT ignition, spherical blast, entity damage+knockback |
| `natural-spawn` | Mob spawning with AI goals, max 100, 5min despawn |
| `container` | Block-tied inventories (chest, barrel, hopper, furnace, etc.) |

### Advanced Mechanics (10)
| Module | Summary |
|---|---|
| `crafting` | Opens crafting inventory on crafting table interaction |
| `potion` | Reads `DataComponents.POTION_CONTENTS`, applies effects, replaces with glass bottle |
| `crop` | Tick-based crop growth (wheat, carrots, potatoes, beetroots, stems, nether_wart) |
| `fishing` | Fishing bobber casting, bite timer, weighted fish loot table |
| `anvil` | Opens anvil inventory on anvil block interaction |
| `breeding` | Animal breeding with per-type food maps, love mode, breed cooldowns |
| `beacon` | Pyramid tier scanning (1-4) via `BlockPositionIndex`, tier cache with invalidation, tier-based potion effects |
| `enchanting` | Opens enchantment inventory on enchanting table |
| `respawn-anchor` | Glowstone charging (up to 4), set respawn point |
| `trading` | Opens merchant inventory on villager/wandering_trader interaction |

### Interaction & Projectile Mechanics (12)
| Module | Summary |
|---|---|
| `door` | Door/trapdoor/fence gate open/close, double door sync, sound effects |
| `shield` | Shield blocking damage, 200ms warmup, attacker knockback |
| `enderpearl` | Ender pearl teleportation, 5 fall damage on land |
| `snowball` | Snowball/egg throwable projectiles, knockback, egg chicken spawn |
| `elytra` | Elytra gliding, firework rocket boost |
| `trident` | Trident throwing, 8 damage, knockback |
| `crossbow` | Crossbow loading (1.25s), firing with 9 damage arrows |
| `lever` | Lever toggle, button auto-reset (20/30 ticks), pressure plate |
| `noteblock` | Note block tuning (25 pitches), instrument sounds |
| `composter` | Composter interaction, compostable items (30-100% chance), bone meal output |
| `sign` | Sign placement tracking, text storage |
| `furnace-smelting` | Furnace/smoker/blast furnace smelting with 27 recipes, fuel system |

### Entity & Decoration Mechanics (5)
| Module | Summary |
|---|---|
| `armor-stand` | Armor stand placement, equipment slots, interaction/break |
| `painting` | Painting placement on wall faces, break to drop |
| `item-frame` | Item frame placement, 8-state rotation, item insert/remove, glow support |
| `boat` | 18 boat types (wood + chest variants), water placement, mount/dismount, break to drop |
| `gravity` | Gravity blocks (sand, gravel, anvil, dragon egg, concrete powder), falling entities, landing |

### Workstation UI Mechanics (8)
| Module | Summary |
|---|---|
| `grindstone` | Opens grindstone inventory on block interaction |
| `stonecutter` | Opens stonecutter inventory on block interaction |
| `loom` | Opens loom inventory on block interaction |
| `cartography-table` | Opens cartography table inventory on block interaction |
| `smithing-table` | Opens smithing table inventory on block interaction |
| `ender-chest` | Per-player ender chest with persistent inventory, open sound |
| `lectern` | Lectern book placement and reading |
| `jukebox` | Jukebox disc insert/eject |

### Block Interaction Mechanics (9)
| Module | Summary |
|---|---|
| `bell` | Bell ring sound on interaction |
| `candle` | Candle light/extinguish (flint & steel), stacking (1-4), all 17 colors |
| `cauldron` | Cauldron water level fill/drain, bucket and bottle interaction |
| `shulker-box` | 17 colored shulker boxes, persistent per-block inventory, open sound |
| `scaffolding` | Scaffolding placement with max distance (6), stability check |
| `ladder` | Ladder/vine climbing, slow descent when sneaking |
| `copper-oxidation` | Oxidation stages, waxing with honeycomb, scraping with axe |
| `turtle-egg` | Egg trampling, hatching over time, stacking (1-4) |
| `bonemeal` | Bone meal crop growth, sapling handling, grass flower spreading |

### Redstone & Environment Mechanics (5)
| Module | Summary |
|---|---|
| `dispenser` | Dispenser/dropper item ejection with directional velocity |
| `hopper` | Hopper persistent inventory, open on interaction |
| `conduit` | Conduit power effect, prismarine frame detection (16+ blocks), 32-block range |
| `beehive` | Honey level tracking, glass bottle → honey bottle, shears → honeycomb |
| `lightning-rod` | Lightning rod tracking, nearest rod finder, powered state management |
| `campfire` | Campfire cooking (8 recipes, 30s), contact damage (1/2 for soul), lit/unlit state |

### Plant Growth Mechanics (5)
| Module | Summary |
|---|---|
| `cactus` | Cactus contact damage, growth (max 3), placement validation (sand only, no adjacent) |
| `sugarcane` | Sugar cane tick-based growth (max 3) |
| `bamboo` | Bamboo growth (max 16) |
| `vine-growth` | Vine downward spreading over time |
| `mushroom` | Mushroom spreading in dark areas |

### Redstone & Technical Mechanics (4)
| Module | Summary |
|---|---|
| `piston` | Piston extend/retract, block pushing (max 12), sticky pull, immovable block list |
| `redstone-wire` | Redstone wire connection updates (side/up/none), neighbor propagation |
| `minecart` | 5 minecart types, rail placement, mount/dismount, break to drop |
| `firework` | Firework rocket launching (use item + block interact), timed despawn |

### World & Environment Mechanics (6)
| Module | Summary |
|---|---|
| `snow-layer` | Snow layer stacking (1-8), shovel clearing, snow block removal |
| `ice` | Ice breaking → water conversion |
| `coral` | Coral dying outside water (15 types), dead coral block replacement |
| `gravity` | Gravity blocks (sand, gravel, anvil, dragon egg, concrete powder), falling entities |
| `dragon-breath` | Dragon breath collection with glass bottles |
| `wither` | Wither skull pattern detection, T-shape soul sand validation, structure clearing |

### Armor, Items & Survival Mechanics (5)
| Module | Summary |
|---|---|
| `armor-trim` | Smithing table armor trim UI |
| `cake` | Cake eating per slice (7 bites), food+saturation restore |
| `totem` | Totem of undying death prevention, regen/absorption/fire resistance effects |
| `flower-pot` | Flower pot planting (31 plant types), empty pot on interact |
| `item-repair` | Anvil repair UI |

### Portal & Structure Mechanics (2)
| Module | Summary |
|---|---|
| `nether-portal` | Obsidian frame detection, flint & steel activation, portal block filling |
| `wither` | Wither skull T-shape pattern detection, soul sand validation |

### Connection & Enchantment Mechanics (3)
| Module | Summary |
|---|---|
| `fence` | Fence/wall connection updates (11 wood types, 22 wall types), neighbor propagation |
| `frost-walker` | Frost walker ice creation on water, 10s melt timer |
| `map` | Map creation via cartography table |

### Wave 7 — Portals, Constructs & Environment (15)
| Module | Summary |
|---|---|
| `end-portal` | End portal frame eye insertion, 12-frame detection, portal filling |
| `brewing-stand` | Brewing stand inventory, ingredient/fuel tracking, 400-tick brew timer |
| `banner` | Banner placement/break, 16 color drop mapping |
| `spawner` | Mob spawner block via `BlockPositionIndex`, per-type entity spawning, 4 max entities, 400-tick cycle |
| `chorus` | Chorus fruit teleport (16 attempts), chorus plant cascading break |
| `honey-block` | Honey block slowdown, slime block bounce |
| `magma-block` | Magma block contact damage (1f), sneaking bypass |
| `sponge` | Sponge water absorption (65 max, range 7), wet sponge conversion |
| `observer` | Observer block state change detection, 2-tick powered pulse |
| `repeater` | Repeater delay cycling (1-4), comparator mode toggle (compare/subtract) |
| `soul-speed` | Soul sand/soil velocity boost (1.3x) |
| `depth-strider` | Underwater movement boost (1.2x) |
| `kelp` | Kelp underwater growth, age progression (0-25) |
| `end-crystal` | End crystal placement on obsidian/bedrock, explosion on attack (6 radius, 12 damage) |
| `iron-golem` | Iron golem T-shape construction (iron blocks + pumpkin), snow golem detection |

### Wave 8 — Block Placement & Workstations (15)
| Module | Summary |
|---|---|
| `slab` | Slab double-placement (bottom+top = double), 50+ slab types |
| `stairs` | Stair shape detection (inner/outer corners), 45+ stair types |
| `trapdoor` | Trapdoor open/close toggle, 11 wood types, iron excluded |
| `glass-pane` | Glass pane/iron bars connection updates (NSEW), 18 pane types |
| `rail` | Rail shape auto-detection (curved/straight), 4 rail types |
| `torch` | Torch placement validation, support block breaking cascade |
| `end-rod` | End rod facing alignment with adjacent rods |
| `chain` | Chain axis alignment with adjacent chains |
| `barrel` | Barrel persistent inventory, open sound/state |
| `smoker` | Smoker inventory on interaction |
| `blast-furnace` | Blast furnace inventory on interaction |
| `fletching-table` | Fletching table crafting UI |
| `lantern` | Lantern placement validation (hanging/standing), support break cascade |
| `chorus-flower-growth` | Chorus flower vertical growth, age progression, plant stem conversion |
| `wither-rose` | Wither rose contact wither effect |

### Wave 9 — Modern Blocks & Archaeology (13)
| Module | Summary |
|---|---|
| `dropper` | Dropper block inventory |
| `lily-pad` | Lily pad water-only placement, drop on break |
| `sea-lantern` | Sea lantern prismarine crystal drops (2-3) |
| `glow-lichen` | Glow lichen shear harvesting |
| `moss` | Moss bone meal spreading, stone/dirt conversion in radius |
| `sculk-shrieker` | Sculk shrieker activation on non-sneaking movement, shriek sound, 10s cooldown |
| `decorated-pot` | Decorated pot single-item storage, break drops contents |
| `chiseled-bookshelf` | Chiseled bookshelf 6-slot book storage, slot property updates |
| `hanging-sign` | Hanging sign placement validation (requires above block) |
| `suspicious-sand` | Archaeology brushing (4 stages), loot table on complete |
| `pointed-dripstone-water` | Dripstone water dripping via `BlockPositionIndex` spatial lookup, cauldron filling |
| `respiration` | Underwater breath tracking (300 ticks), recovery on surface |
| `tinted-glass` | Tinted glass drops self on break |

### Wave 10 — Fluids, Durability & Tool Interactions (8)
| Module | Summary |
|---|---|
| `water-flow` | Event-driven water spreading cascade (5-tick delay per step, max depth 7), horizontal + downward flow, gap-filling on break |
| `lava-flow` | Event-driven lava spreading cascade (30-tick delay per step, max depth 3), lava-water interaction (cobblestone/obsidian) |
| `bubble-column` | Bubble column push (soul sand up) / pull (magma block down) on player movement |
| `item-durability` | Weapon/tool/armor durability via DataComponents.DAMAGE, break at max |
| `double-chest` | Adjacent chest detection, 6-row double chest inventory, ordered key deduplication |
| `cobweb` | Cobweb velocity reduction (0.05x) on player movement |
| `hoe-tilling` | Hoe right-click converts dirt/grass/path/coarse/rooted to farmland with sound |
| `axe-stripping` | Axe right-click strips logs/wood/stems/hyphae (10 wood types, 20 mappings) with sound |

### Wave 11 — Enchantment Mechanics & Tool Actions (7)
| Module | Summary |
|---|---|
| `shovel-pathing` | Shovel right-click converts grass/dirt/coarse/mycelium/podzol/rooted to dirt path with sound |
| `protection-enchant` | Protection enchantment damage reduction (4%/level, max 20), specialized fire/blast/projectile/feather falling |
| `fire-aspect` | Fire Aspect enchantment sets target on fire for level*4s on melee attack |
| `thorns` | Thorns enchantment reflects 1-4 damage with level*15% chance on melee hit |
| `mending` | Mending enchantment repairs damaged equipment when picking up nearby XP orbs (2 durability/XP) |
| `silk-touch` | Silk Touch drops block itself (ores, glass, glowstone, ice, bookshelves, grass, mycelium, sculk) |
| `sweeping` | Sweep attack hits nearby entities (1.0 block radius), sweep edge enchantment scaling, sweep particle via `spawnParticleAt` util |

### Wave 12 — Enchantments, 1.21 Features & Combat (15)
| Module | Summary |
|---|---|
| `sharpness` | Sharpness (+0.5*level+0.5), Smite (vs undead, +2.5*level), Bane of Arthropods (vs arthropods, +2.5*level) extra melee damage |
| `knockback-enchant` | Knockback enchantment adds extra velocity (0.5*level multiplier) on melee attack |
| `power` | Power enchantment increases arrow damage by 0.5*(level+1) via Tag on arrow entity |
| `flame` | Flame enchantment sets arrow targets on fire for 5s, tracked via Tag on arrow entity |
| `infinity` | Infinity enchantment restores consumed arrow after bow shot |
| `fortune` | Fortune enchantment multiplies ore drops (bonus 0..level), 18 ore-to-drop mappings |
| `efficiency` | Efficiency enchantment tracking via Tag for tool mining speed bonus |
| `unbreaking` | Unbreaking enchantment 1/(level+1) chance to actually take durability damage on attack/break/hit |
| `looting` | Looting enchantment spawns bonus mob drops (0..level) on kill, mob-type-aware drop materials |
| `loyalty` | Loyalty enchantment returns thrown trident to owner with velocity tracking, level-scaled return speed |
| `channeling` | Channeling enchantment strikes lightning (5 damage, fire) on trident melee hit during thunderstorm |
| `quick-charge` | Quick Charge enchantment reduces crossbow loading time by 0.25s per level |
| `wind-charge` | 1.21 wind charge throwable, sphere knockback (radius 2.5), no damage, projectile collision detection |
| `mace` | 1.21 mace weapon, fall-distance-scaled damage (base 6 + (fallDist-1.5)*4), bounce on smash hit |
| `trial-spawner` | 1.21 trial spawner block, proximity activation (8 blocks), 3-6 hostile mobs, reward drop on completion, 30min cooldown |

### Wave 13 — Deep Dark, 1.21 Blocks & Mob Behaviors (15)
| Module | Summary |
|---|---|
| `sculk-catalyst` | Sculk catalyst spreads sculk blocks in 3-block radius around mob deaths within 8 blocks, timer-based death scanning |
| `copper-bulb` | 1.21 copper bulb light toggle on interaction, all 8 oxidation/waxed variants, lit property toggling |
| `crafter` | 1.21 crafter block opens 3x3 crafting grid on interaction, persistent per-block inventory |
| `vault` | 1.21 vault block gives random reward on first interaction per player, ConcurrentHashMap claim tracking |
| `ominous-bottle` | Ominous bottle applies Bad Omen effect (6000 ticks) on use, consumes item, drink sound |
| `item-frame-rotation` | Enhanced item frame rotation cycling through 8 states on punch, drops item at max rotation |
| `suspicious-stew` | Suspicious stew applies random potion effect (5-15s) when eaten, replaces with bowl |
| `sculk-vein` | Sculk vein placement validation (requires adjacent solid block), multi-face property tracking |
| `breeze` | Breeze mob wind charge attack behavior, 3s cooldown, sphere knockback on projectile impact |
| `spore-blossom` | Spore blossom dripping particle effect via `BlockPositionIndex` spatial lookup, FALLING_SPORE_BLOSSOM particles |
| `sculk-sensor-calibrated` | Calibrated sculk sensor frequency setting (1-15) via interaction, vibration matching via `BlockPositionIndex` spatial lookup |
| `brushable` | Extended archaeology for suspicious gravel, 4-stage brush progress, random archaeological loot |
| `frog` | Frog eats small slimes/magma cubes, produces variant-based froglight (warm=pearlescent, temperate=ochre, cold=verdant) |
| `allay` | Allay item collection within 32 blocks via `EntitySpawnEvent` tracking, delivers to nearest note block, material matching via Tag |
| `warden` | Warden spawns after 3 sculk shrieker activations, sonic boom ranged attack (10 dmg, 5s cooldown), darkness effect |

### Wave 14 — Modern Plants, Mobs & Crafting (15)
| Module | Summary |
|---|---|
| `smelting-drop` | Auto-smelt mining drops when tool has auto_smelt Tag, 12 ore-to-ingot/material mappings |
| `nether-wart` | Nether wart growth on soul sand, tick-based age 0-3 progression, placement validation |
| `sniffer` | Sniffer mob digging behavior, produces torchflower_seeds or pitcher_pod, 2min cooldown via Tag |
| `camel` | Camel mount with dash ability on sprint, velocity burst in look direction, 5s cooldown, 2-rider support |
| `hanging-block` | Hanging block placement validation (propagule, spore blossom, etc.), cascading break on support removal |
| `mud` | Water bottle on dirt converts to mud, mud above pointed dripstone dries to clay (timer-based), ConcurrentHashMap tracking |
| `mangrove` | Mangrove propagule growth, tick-based age 0-4 progression for hanging propagules |
| `cherry` | Cherry blossom petal particles via `BlockPositionIndex` spatial lookup near cherry_leaves blocks, CHERRY_LEAVES particle with POOF fallback |
| `pitcher-plant` | Pitcher crop growth age 0-4, becomes 2-block tall pitcher plant at max age |
| `torchflower` | Torchflower crop growth age 0-2, becomes torchflower block at max age |
| `wax` | Honeycomb waxing on copper blocks, 36 copper variant mappings (blocks, doors, trapdoors, grates, bulbs, chiseled) |
| `azalea` | Azalea bush bone meal growth into azalea tree (4-block oak log trunk + azalea/flowering leaves sphere) |
| `bamboo-mosaic` | Bamboo block stripping with axe, bamboo_block to stripped_bamboo_block conversion |
| `recovery-compass` | Recovery compass tracks last death location via Tags, action bar distance display |
| `echo-shard` | Echo shard crafting shortcut: 8 echo shards + compass on crafting table produces recovery compass |

### Wave 15 — Special Blocks, Items & Display (15)
| Module | Summary |
|---|---|
| `slime-block-bounce` | Player lands on slime block → bounce (negate + 0.8x Y velocity), cancel fall damage |
| `noteblock-instrument` | Note block instrument determined by block below (16 instrument-to-block mappings) |
| `dragon-egg` | Dragon egg teleports to random nearby position (radius 15) when clicked |
| `respawn-module` | Full respawn handling: heal, food=20, saturation=5, clear effects, respawn position |
| `head-drop` | Player kill drops victim skull as PLAYER_HEAD item with victim name in lore |
| `compass-tracking` | Lodestone compass position binding on lodestone interaction, position storage via Tag |
| `spyglass` | Spyglass zoom with slowness effect on use, cancel on stop |
| `goat-horn` | Goat horn 8 variant sounds on use, 7s cooldown via `Cooldown<UUID>` util |
| `bundle` | Bundle item GUI storage (1-row inventory), 64-item max capacity, per-player tracking |
| `amethyst-growth` | Amethyst bud growth stages on budding amethyst (small→medium→large→cluster), random face |
| `light-block` | Light block adjustable level (0-15) cycling on interaction |
| `structure-void` | Structure void blocks: invisible, no collision for non-creative, instant break |
| `barrier-block` | Barrier blocks: invisible to non-creative, full collision, particle display via `BlockPositionIndex` spatial lookup |
| `command-block` | Command block stored command framework, GUI for ops, redstone execution |
| `skull` | Player/mob skull placement with owner tracking, texture display, owner-aware drops |

### Wave 16 — Mob Behaviors & Environmental Hazards (15)
| Module | Summary |
|---|---|
| `netherite-damage` | Netherite armor knockback resistance: 10% per piece (max 40%), velocity reduction on entity damage |
| `sweet-berry-bush` | Extended sweet berry bush: 0.2x velocity slowdown, 1 damage/sec timer while inside bush |
| `powder-snow-freezing` | Powder snow freeze timer: 1 damage/sec after 7s in powder snow, leather boots bypass |
| `drowned-conversion` | Zombie underwater 30s converts to drowned, timer-based scanning, AI group setup |
| `piglin-barter` | Piglin bartering: gold ingot pickup, 6s wait, weighted loot table (21 items) drop |
| `enderman-pickup` | Enderman random block pickup (15 types) and placement, held block via Tag |
| `creeper-explosion` | Creeper hiss + 1.5s fuse + spherical explosion (radius 3), charged creepers double radius |
| `skeleton-shoot` | Skeleton arrow shooting at nearest player (16 blocks), 2s cooldown, 2-5 random damage |
| `zombie-attack` | Zombie melee AI: path to nearest player (16 blocks), 3 damage on contact, AI goals |
| `wither-skeleton` | Wither skeleton: 5 melee damage + Wither I (10s), 2.5% wither skull drop on death |
| `blaze-behavior` | Blaze 3-fireball burst at nearest player (16 blocks), 3s cooldown, fire at impact |
| `ghast-behavior` | Ghast large fireball, deflectable by player attack, explosion + fire on impact |
| `slime-split` | Slime/magma cube splitting: large→2 medium→2 small on death, size via Tag |
| `phantom` | Phantom spawning after 72000 insomnia ticks, night-only, swooping attack pattern |
| `vex` | Vex mob: no gravity, 9 damage, target tracking, 30s lifetime auto-remove |

### Wave 17 — Mob Behaviors & Environmental Interactions (15)
| Module | Summary |
|---|---|
| `spider-climb` | Spider wall climbing: spiders climb vertical surfaces, 0.2 upward velocity when adjacent to solid wall blocks |
| `guardian-beam` | Guardian laser beam: target nearest player (16 blocks), 6 damage after 2s charge, Tag-based beam tracking |
| `shulker-bullet` | Shulker homing bullet: projectile tracks nearest player (16 blocks), Levitation I (10s) on hit, 3s cooldown |
| `witch-potion` | Witch splash potions: random Poison/Slowness/Weakness/Harming at nearest player (16 blocks), 3s cooldown |
| `evoker-fangs` | Evoker fang line: 16 sequential damage zones toward target, 6 damage per fang, 5s cooldown |
| `ravager` | Ravager charge: 12 melee damage, breaks leaf blocks on contact, 4s cooldown, 12-block range |
| `pillager-crossbow` | Pillager crossbow: arrow projectile at nearest player (16 blocks), 3-6 damage, 2s cooldown |
| `snow-golem` | Snow golem: leaves snow trail, throws snowballs at hostile mobs (10 blocks), 1s cooldown |
| `fox-sleep` | Fox day sleeping: sits during daytime (0-12000), wakes at night, FoxMeta sitting pose |
| `parrot-dance` | Parrot jukebox dance: parrots within 3 blocks of active jukebox dance, stop when jukebox stops |
| `dolphin-grace` | Dolphin's Grace: players within 5 blocks of dolphin in water receive Dolphin's Grace potion effect |
| `bee-pollination` | Bee pollination: bees collect nectar from flowers, return to hive, increment honey_level (Tag-based) |
| `silverfish-burrow` | Silverfish burrowing: idle silverfish convert stone/cobblestone/stone_bricks to infested variants, spawn on infested block break |
| `cat-creeper` | Cat scares creepers: creepers flee from cats/ocelots within 6 blocks, velocity away from nearest cat |
| `turtle-scute` | Baby turtle scute drop: baby turtles drop scute item on growth (24000 tick age transition) |

### Wave 18 — Village, Nether & Animal Taming (15)
| Module | Summary |
|---|---|
| `villager-profession` | Villager workstation assignment: 40-tick scan, block-to-profession mapping (13 workstations), profession via Tag |
| `wandering-trader` | Wandering trader spawning near players every 24000 ticks, despawn after 48000 ticks, 2 trader llama companions |
| `raid-system` | Raid waves on Bad Omen + bell proximity, pillager/vindicator spawn escalation (5 waves), per-instance ConcurrentHashMap tracking |
| `elder-guardian` | Elder guardian Mining Fatigue III (6000 ticks) within 50 blocks, 60s refresh scan |
| `zombie-siege` | Zombie siege at villages: night-only (18000-23000), bell + 20 beds required, max 20 zombies per siege |
| `piglin-aggro` | Piglin gold armor check: attack players not wearing gold within 10 blocks, AI goal configuration |
| `hoglin-behavior` | Hoglin melee attack (6 damage, 8 blocks, 2s cooldown), flee from warped fungus within 7 blocks |
| `strider-behavior` | Strider lava walking, passenger steering with warped fungus on stick, shiver speed reduction outside lava |
| `wolf-taming` | Wolf taming with bone (33% chance), sit on shift-click, follow owner, attack owner's attacker |
| `cat-taming` | Cat taming with raw cod/salmon (33% chance), sit on shift-click, follow owner |
| `horse-taming` | Horse taming via repeated mounting, increasing tame chance per attempt, saddle for steering control |
| `llama-behavior` | Llama spit at hostile mobs (10 blocks, 1 damage, 3s cooldown), caravan formation when leashed |
| `panda-behavior` | Panda gene system via Tag: lazy roll, playful somersault, aggressive retaliation attack (6 damage) |
| `polar-bear` | Polar bear neutral behavior: attacks when baby nearby is hit, 6 damage, 12-block aggro range |
| `axolotl-behavior` | Axolotl attacks drowned/guardians via `EntitySpawnEvent` tracking + `nearbyEntities`, plays dead at low HP (regenerates), grants Regeneration I to nearby players |

## Custom Content System — `utils/customcontent/` (23 files)
Custom item, block, and 3D armor system with JSON config or code DSL definitions, Blockbench `.bbmodel` model source, GLSL shader generation, and resource pack merging.

### Architecture
| File | Summary |
|---|---|
| `CustomContentRegistry.kt` | Central singleton: init, load JSON, allocate states, register items/blocks, merge pack. Top-level `customItem {}` and `customBlock {}` DSL functions |
| `item/CustomItem.kt` | Item data class (`id`, `baseMaterial`, `customModelDataId`, `displayName`, `lore`, `unbreakable`, `glowing`, `maxStackSize`, `modelPath`), `createStack(amount)` |
| `item/CustomItemRegistry.kt` | ConcurrentHashMap registry: `get(id)`, `require(id)`, `byCustomModelData(cmd)`, `all()` |
| `item/CustomItemLoader.kt` | JSON deserialization → `CustomItemDefinition`, `CustomItemDsl` builder class |
| `block/BlockHitbox.kt` | Sealed class: `Full` (592), `Slab` (53), `Stair` (50), `Thin` (17), `Transparent` (128), `Wall` (22), `Fence` (12), `Trapdoor` (11). `fromString()` companion |
| `block/BlockStateAllocator.kt` | Deterministic vanilla block state pools per hitbox type, `allocate(id, hitbox)`, `isAllocated(block)`, `fromVanillaBlock(block)`. Persists to `data/customcontent/allocations.dat` |
| `block/CustomBlock.kt` | Block data class (`id`, `hitbox`, `itemId`, `customModelDataId`, `hardness`, `drops`, `modelPath`, `placeSound`, `breakSound`, `allocatedState`). `CustomBlockDrops` sealed: `SelfDrop`, `LootTableDrop` |
| `block/CustomBlockRegistry.kt` | ConcurrentHashMap registry: `get(id)`, `require(id)`, `fromVanillaBlock(block)`, `fromItemId(itemId)`, `all()` |
| `block/CustomBlockLoader.kt` | JSON deserialization → `CustomBlockDefinition`, `CustomBlockDsl` and `CustomBlockDropsDsl` builder classes |
| `event/CustomBlockPlaceHandler.kt` | `PlayerBlockPlaceEvent` listener: detects held CustomModelData item → sets allocated vanilla block state, decrements item, plays place sound |
| `event/CustomBlockBreakHandler.kt` | `PlayerBlockBreakEvent` listener: detects custom block → spawns drops (self or loot table), plays break sound |
| `event/CustomBlockInteractHandler.kt` | `PlayerBlockInteractEvent` listener: cancels vanilla interactions on custom block states |
| `pack/PackMerger.kt` | Merges ModelEngine bones + custom item/block models + armor shader entries into single ZIP with SHA-1. `merge(modelsDir, rawResults, armorShaderEntries)` → `MergeResult(packBytes, sha1)`. All ModelEngine assets under `modelengine` namespace. Armor shader entries injected as-is (paths like `assets/minecraft/shaders/...`) |
| `pack/ItemModelOverrideWriter.kt` | Generates `models/item/{material}.json` with sorted CustomModelData override entries |
| `pack/BlockStateWriter.kt` | Generates `blockstates/{block}.json` with complete `variants` for `full` (note_block/mushroom) and `thin` (carpet) hitbox types. Non-allocated states fall back to vanilla model. Other hitbox types skip blockstate generation (placed block renders as vanilla material) |

### Block State Pools
| Hitbox | Source | Count | Custom Visual |
|---|---|---|---|
| `full` | note_block (16 instruments × 25 notes × powered=false) + brown/red mushroom_block + mushroom_stem (64 face combos each) | 592 | Yes — complete `variants` blockstate with vanilla fallback |
| `thin` | 17 carpet colors | 17 | Yes — single-variant blockstate replacement |
| `slab` | 53 slab materials, canonical `type=bottom,waterlogged=false` | 53 | No — vanilla material visual when placed |
| `stair` | 50 stair materials, canonical `facing=north,half=bottom,shape=straight,waterlogged=false` | 50 | No — vanilla material visual when placed |
| `transparent` | tripwire 7-boolean combos | 128 | No — vanilla material visual when placed |
| `wall` | 22 wall materials, canonical isolated state | 22 | No — vanilla material visual when placed |
| `fence` | 12 fence materials, canonical disconnected state | 12 | No — vanilla material visual when placed |
| `trapdoor` | 11 trapdoor materials (no iron), canonical closed state | 11 | No — vanilla material visual when placed |

Non-`full`/`thin` hitbox types provide the correct collision shape but render as their vanilla material when placed. This is because replacing blockstate files for complex blocks (slabs, stairs, fences, walls, trapdoors, tripwire) would break all vanilla instances of that material. The custom model is only visible on the held item.

### Mechanic Guards
- `NoteBlockModule` — skips tuning for `BlockStateAllocator.isAllocated(block)`
- `BlockModule` — skips vanilla drops for allocated states
- `SlabModule` — skips double-slab stacking for allocated states

### Data Directories
```
data/customcontent/
├── items/           (JSON item definitions)
├── blocks/          (JSON block definitions)
├── models/          (.bbmodel files for items/blocks)
├── armors/          (.bbmodel files for 3D armor sets)
├── allocations.dat  (persistent block state allocations)
├── model_ids.dat    (persistent CustomModelData IDs)
└── pack.zip         (merged resource pack output)
```

### Custom 3D Armor — `armor/` (8 files)
Shader-based 3D armor rendering. Players equip dyed leather armor; client-side GLSL raycasting replaces flat leather texture with 3D cubes defined in `.bbmodel` files.

| File | Summary |
|---|---|
| `ArmorPart.kt` | Sealed class: 9 armor body parts (`Helmet`, `Chestplate`, `RightArm`, `LeftArm`, `InnerArmor`, `RightLeg`, `LeftLeg`, `RightBoot`, `LeftBoot`) with bone prefix, STASIS constant, layer, `isLeft`. `fromBoneName()` auto-detection |
| `ArmorDefinition.kt` | Data classes: `ArmorCube` (center, halfSize, rotation, pivot, uvFaces), `ArmorCubeUv`, `ParsedArmorPiece`, `ParsedArmor`, `RegisteredArmor` (id, colorId, RGB, parsed data) |
| `ArmorParser.kt` | Parses `.bbmodel` via `BlockbenchParser`, walks bone hierarchy, auto-detects armor pieces by prefix. Coordinate transform: BB → TBN space (bone-relative positioning). Left-part 180° mirror via sign multiplier. Handles nested prefixed sub-groups, multi-level rotations |
| `ArmorGlslGenerator.kt` | Converts parsed cubes → GLSL `ADD_BOX_WITH_ROTATION_ROTATE` macros. `generateArmorGlsl()` produces dual-section file (`#ifdef VSH`/`#ifdef FSH`). `generateArmorcordsGlsl()` produces RGB → armorId mapping |
| `ArmorShaderPack.kt` | Assembles all shader pack entries: 16 static shader files from classpath + generated `armor.glsl` + `armorcords.glsl` + leather layer textures with marker pixels. Returns `Map<String, ByteArray>` |
| `CustomArmorRegistry.kt` | Registry with `ConcurrentHashMap`. `loadFromResources(resources, dir)` auto-detects `.bbmodel` files. `register()` assigns color IDs via `ModelIdRegistry`. Extension functions: `createItem(ArmorPart)`, `equipFullSet(Player)` |
| `ArmorTestCommand.kt` | `/armor list` (show registered armors), `/armor equip <id>` (full set), `/armor give <id> <slot>` (single piece) |

**Bone prefix convention** (case-insensitive):
| Prefix | Part | STASIS | Layer |
|---|---|---|---|
| `h_` | Helmet | 199 | 1 |
| `c_` | Chestplate | 299 | 1 |
| `ra_` | Right Arm | 399 | 1 |
| `la_` | Left Arm | 499 | 1 |
| `ia_` | Inner Armor | 999 | 2 |
| `rl_` | Right Leg | 599 | 2 |
| `ll_` | Left Leg | 699 | 2 |
| `rb_` | Right Boot | 799 | 1 |
| `lb_` | Left Boot | 899 | 1 |

**How it works**: Each armor gets a unique RGB via `ModelIdRegistry`. Server equips leather armor dyed to that RGB. Vertex shader (`entity.vsh`) reads pixel `(63,31)` of the leather texture to identify custom armor. Fragment shader (`entity.fsh`) raycasts 3D cubes via `ADD_BOX` macros instead of rendering flat texture.

**Static shaders**: 16 files in `src/main/resources/shaders/armor/` from MC 1.21.6 overlay (compatible with 1.21.11). Key files: `entity.vsh`/`entity.fsh` (core pipeline), `frag_funcs.glsl` (CEM raycasting library), `armorparts.glsl` (STASIS constants), `setup.glsl` (UV-based part detection).

### Modified Existing Files
- `modelengine/generator/ModelIdRegistry.kt` — added `assignId(key: String)` single-arg overload, `getId(key: String)` overload
- `modelengine/generator/ModelGenerator.kt` — added `generateRaw()` returning `RawGenerationResult(blueprint, boneModels, textureBytes)`, `buildBoneElements()` and `buildFlatModel()` helpers. Bone offsets are parent-relative (not absolute). `buildBoneElements(centerOffset)`: ModelEngine bones use `centerOffset=0f` so element [0,0,0] maps to entity position (correct rotation pivot for item_display transforms); custom content flat models use default `centerOffset=8f` (standard MC model centering). All pack paths and `ITEM_MODEL` values lowercased for Minecraft resource location compliance. Bone items use `DataComponents.ITEM_MODEL` (`"minecraft:me_<model>_<bone>"` flat naming — all under `minecraft` namespace with `me_` prefix) instead of `CUSTOM_MODEL_DATA`. All pack assets (textures, models, items) placed under `assets/minecraft/` for guaranteed client-side resolution
- `modelengine/bone/BoneTransform.kt` — `toRelativePosition()` uses 3D Y-axis rotation matrix (not 2D rotation) to correctly position bones when the model has non-zero yaw
- `Orbit.kt` — calls `CustomContentRegistry.init()` before mode install, auto-merges pack (pack distribution delegated to proxy)
- `customcontent/CustomContentCommand.kt` — removed `PackServer` and `/cc send` (pack sending delegated to proxy)

## Utility Framework — `utils/` (189 files)

### Model Engine (46)
| Util | Summary |
|---|---|
| `modelengine/ModelEngine.kt` | Singleton registry, factory DSL (`modeledEntity(entity) {}`, `modeledEntity(owner) {}`, `standAloneModel(pos) {}`), 1-tick loop via `install()`/`uninstall()`, blueprint CRUD, modeled entity lifecycle, `PlayerDisconnectEvent` session cleanup (viewer eviction, mount/VFX cleanup). `ModelOwner` interface decouples from Entity |
| `modelengine/math/ModelMath.kt` | `data class Quat(x,y,z,w)` with `operator get`/`toFloatArray()`, `eulerToQuat`/`quatToEuler` (gimbal-lock safe)/`quatSlerp`/`quatMultiply`/`quatRotateVec`/`quatInverse`/`quatNormalize`/`wrapDegrees`, `OrientedBoundingBox` (containsPoint/intersects/rayTrace), `clamp`/`lerp`/`lerpVec`/`colorArgb` |
| `modelengine/blueprint/ModelBlueprint.kt` | Immutable model data: bone tree, animations, hitbox dimensions. `traverseDepthFirst()` visitor. `AnimationBlueprint`, `BoneKeyframes`, `Keyframe`, `InterpolationType`, `LoopMode` |
| `modelengine/blueprint/BlueprintBone.kt` | Static bone definition: name, parent, children, offset, rotation, scale, modelItem, behaviors map, visibility |
| `modelengine/bone/BoneTransform.kt` | Immutable `BoneTransform(position, leftRotation, rightRotation, scale)`. `combine(parent)` chain, `toRelativePosition(yaw)` (bone offset rotated by model yaw — for display entity translation), `toWorldPosition(modelPos)` (absolute world coords — for behaviors), `toWorldRotation(yaw)`, `lerp()` |
| `modelengine/bone/ModelBone.kt` | Runtime mutable bone: local + animated transforms, `computeTransform()` recurses children, dirty-check. `behaviors` list, `addBehavior()`/`removeBehavior()`, inline `behavior<T>()`/`behaviorsOf<T>()` |
| `modelengine/render/BoneRenderer.kt` | Per-player `ITEM_DISPLAY` packet renderer. Entity IDs from `-3,000,000`. Spawn/update/destroy with dirty-checking (only changed metadata). Uses `toRelativePosition()` for META_TRANSLATION (relative to entity spawn). Teleports all bone entities to model position on movement via `EntityTeleportPacket`. `show`/`hide`/`update`/`destroy` |
| `modelengine/model/ActiveModel.kt` | Model instance from blueprint, owns bone map + renderer + `animationHandler: PriorityHandler`. Auto-creates behaviors from blueprint. Auto-plays first animation containing "idle" at init. `modelScale`, `computeTransforms()`, `tickAnimations(deltaSeconds)`, `playAnimation(name, lerpIn, lerpOut, speed)`, `stopAnimation(name)`, `stopAllAnimations()`, `isPlayingAnimation(name)`, `initBehaviors`/`tickBehaviors`/`destroyBehaviors`, `show`/`hide`/`destroy` |
| `modelengine/model/ModelOwner.kt` | `ModelOwner` interface (`position`, `isRemoved`, `ownerId`), `EntityModelOwner` adapter, `Entity.asModelOwner()` extension, `StandaloneModelOwner` (mutable position, no entity), `standAloneModel(pos) {}` DSL |
| `modelengine/model/ModeledEntity.kt` | Wraps `ModelOwner`, holds multiple ActiveModels, per-player viewer tracking, `entityOrNull` for Entity-specific downcast, `tick()` (animations → transforms → behaviors → renderer at 1/20s delta), `destroy()` calls behavior cleanup + unregisters from ModelEngine, `evictViewer()` propagates to behaviors. DSL builders: `ActiveModelBuilder.animation(name, lerpIn, lerpOut, speed)` |
| `modelengine/animation/AnimationHandler.kt` | Sealed interface: `play`/`stop`/`stopAll`/`isPlaying`/`tick` |
| `modelengine/animation/PriorityHandler.kt` | Priority-based per-bone blending. `boundModel` must be set before `play()`. Sorted priority iteration. Only resets animated bones (multi-layer safe). Weight lerp in/out |
| `modelengine/animation/StateMachineHandler.kt` | Layered state machines via `TreeMap<Int, AnimationStateMachine>`. Resets all bones once, then ticks all layers (additive override) |
| `modelengine/animation/AnimationStateMachine.kt` | States + conditional transitions. `animationStateMachine { state(); transition() }` DSL |
| `modelengine/animation/AnimationProperty.kt` | Per-bone animation state (pos/rot/scale), `blend()`, `fromKeyframes()` (positions divided by 16 for pixel→block conversion) |
| `modelengine/animation/KeyframeInterpolator.kt` | Binary-search keyframe evaluation with pluggable interpolation (LINEAR/CATMULLROM/BEZIER/STEP) |
| `modelengine/animation/InterpolationFunctions.kt` | `linearVec`, `stepVec`, `catmullromVec`, `bezierVec` |
| `modelengine/behavior/BoneBehavior.kt` | Sealed interface: `bone`, `onAdd`/`tick`/`onRemove`/`evictViewer` |
| `modelengine/behavior/HeadBehavior.kt` | Smooth look-at from `modeledEntity.headYaw/Pitch`, configurable maxPitch/maxYaw/smoothFactor |
| `modelengine/behavior/MountBehavior.kt` | Passenger seat via virtual Interaction entity. `mount(player)`/`dismount()`. Seat auto-teleports in tick. Seat IDs from `-3,500,000` |
| `modelengine/behavior/NameTagBehavior.kt` | Virtual TextDisplay at bone position. Reactive `text` property. Tag auto-teleports in tick. Tag IDs from `-3,600,000` |
| `modelengine/behavior/HeldItemBehavior.kt` | Override bone model item, restores on remove |
| `modelengine/behavior/GhostBehavior.kt` | Sets bone invisible, restores on remove |
| `modelengine/behavior/SegmentBehavior.kt` | Chain IK constraint: direction from parent, configurable angleLimit/rollLock |
| `modelengine/behavior/SubHitboxBehavior.kt` | OBB hitbox at bone position, scaled by transform, with damageMultiplier |
| `modelengine/behavior/LeashBehavior.kt` | Leash anchor delegating to `EntityLeashManager`, `attachTo(entity)`/`detach()` |
| `modelengine/behavior/PlayerLimbBehavior.kt` | Player skin on bone (HEAD/BODY/RIGHT_ARM/LEFT_ARM/RIGHT_LEG/LEFT_LEG). Limb IDs from `-3,700,000` |
| `modelengine/behavior/BoneBehaviorFactory.kt` | Creates behaviors from `BoneBehaviorType` enum + config map |
| `modelengine/interaction/ModelInteraction.kt` | Ray-cast against all model SubHitboxBehavior OBBs. `raycast(player)`/`raycastAll(player)` → `ModelHitResult` |
| `modelengine/interaction/ModelDamageEvent.kt` | Custom event: attacker, modeledEntity, bone, hitbox, hitDistance, damage, cancelled |
| `modelengine/mount/MountManager.kt` | Orchestrates driver/passenger sessions. Captures `ClientInputPacket` for WASD/jump. Auto-dismount on sneak |
| `modelengine/mount/MountController.kt` | Sealed interface: `tick(modeledEntity, driver, input)`. `MountInput(forward, sideways, jump, sneak)` |
| `modelengine/mount/WalkingController.kt` | Ground movement from player input. Configurable speed/jumpVelocity |
| `modelengine/mount/FlyingController.kt` | Flying movement from player input. Configurable speed/verticalSpeed |
| `modelengine/vfx/VFX.kt` | Standalone ItemDisplay effect. `vfx(item) { position(); scale(); lifetime() }` DSL. Per-player show/hide. VFX IDs from `-3,800,000` |
| `modelengine/vfx/VFXRegistry.kt` | Lifecycle management, 1-tick loop, auto-remove on lifetime expiry |
| `modelengine/lod/LODLevel.kt` | `lodConfig { level(16.0, tickRate=1); cullDistance(64.0) }` DSL. Per-level visible/hidden bone sets |
| `modelengine/lod/LODHandler.kt` | Closest-viewer distance evaluation. Applies bone visibility per LOD level. Per-player culling beyond max distance |
| `modelengine/advanced/RootMotion.kt` | Extracts root bone animation delta, applies to entity position. Configurable X/Y/Z axes |
| `modelengine/advanced/ModelSerializer.kt` | Save/load ModeledEntity state as ByteArray. Versioned binary format |
| `modelengine/generator/BlockbenchModel.kt` | Data classes: `BbElement`, `BbGroup`, `BbFace`, `BbTexture`, `BbAnimation`, `BbKeyframe` |
| `modelengine/generator/BlockbenchParser.kt` | Parses `.bbmodel` JSON → `BlockbenchModel`. Handles outliner hierarchy, textures, animations |
| `modelengine/generator/ModelGenerator.kt` | Orchestrator: parse `.bbmodel` → register `ModelBlueprint` + generate resource pack zip. Bone items use `ITEM_MODEL` component (`"minecraft:me_<model>_<bone>"` flat naming under `minecraft` namespace) for direct model resolution. All pack assets placed under `assets/minecraft/` (textures, models, items) with `me_` prefix to avoid collisions |
| `modelengine/generator/AtlasManager.kt` | Texture atlas stitching from base64-encoded bbmodel textures. Preserves original index order. Power-of-2 dimensions. `entryByOriginalIndex()` for UV lookup |
| `modelengine/generator/ModelIdRegistry.kt` | Persistent custom model data ID assignment. `computeIfAbsent`-based atomic assignment. Atomic file write via temp+rename |
| `modelengine/generator/PackWriter.kt` | Writes `pack.mcmeta`, model JSONs, textures into zip. Configurable pack format |

### Spatial (1)
| Util | Summary |
|---|---|
| `blockindex/BlockPositionIndex.kt` | Per-instance spatial index for specific block types. Eliminates O(n³) cubic scans. `BlockPositionIndex(targetBlockNames, eventNode).install()`, `positionsNear(instance, center, radius)` O(k), `allPositions(instance)`, `scanChunk(instance, chunk, minY, maxY)`, `evictInstance(hash)`. Coordinate packing: `(x << 40) \| ((y & 0xFFFFF) << 20) \| (z & 0xFFFFF)`. Auto-tracks via `PlayerBlockPlaceEvent`/`PlayerBlockBreakEvent` on scoped eventNode. |

### Display (12)
| Util | Summary |
|---|---|
| `gui/Gui.kt` | `gui(title, rows) {}` DSL, paginated GUI, event-based click handling |
| `scoreboard/Scoreboard.kt` | `scoreboard(title) { line(); animatedLine(); dynamicLine {} }` DSL, `PerPlayerScoreboard`, `AnimatedScoreboard`, `TeamScoreboard`, `ObjectiveTracker`, `liveScoreboard { title(); line(); refreshEvery() }` auto-managed lifecycle DSL, show/hide/update |
| `hologram/Hologram.kt` | TextDisplay holograms, `Instance.hologram {}` global + `Player.hologram {}` packet-based per-player, DSL builder, billboard/scale/background |
| `bossbar/BossBarManager.kt` | `bossBar(name, color, overlay) {}` DSL, show/hide/update |
| `tablist/TabList.kt` | `Player.tabList { header(); footer() }` DSL, `liveTabList { header(); footer(); refreshEvery() }` auto-managed lifecycle DSL |
| `actionbar/ActionBar.kt` | `Player.showActionBar(msg, durationMs)`, `clearActionBar()` |
| `title/Title.kt` | `Player.showTitle { title(); subtitle(); fadeIn(); stay(); fadeOut() }` DSL |
| `healthdisplay/HealthDisplay.kt` | `healthDisplay { format { } }` DSL, periodic display name health suffix updates |
| `entityglow/EntityGlow.kt` | Per-player entity glow via `EntityMetaDataPacket` (no real entities), 20-tick metadata refresh, `Player.setGlowingFor()`, global glow, timed glow |
| `notification/Notification.kt` | `notify(player) { title(); message(); channels(CHAT, ACTION_BAR, TITLE, SOUND) }` DSL, `NotificationManager` broadcast/instance/player, multi-channel (CHAT, ACTION_BAR, TITLE, BOSS_BAR, SOUND), `announceChat()`, `announceActionBar()`, `announceTitle()` convenience functions |
| `playertag/PlayerTag.kt` | `playerTag(player) { prefix(); suffix(); nameColor(); priority() }` DSL, `PlayerTagManager`, priority-based tag stacking, display name/tab/above-head rendering |
| `clickablechat/ClickableChat.kt` | `clickableMessage(player) { text(); clickText("[HERE]") { action(OPEN_URL, url) }; hover() }` DSL, `Player.sendClickable {}`, RUN_COMMAND/SUGGEST_COMMAND/OPEN_URL/COPY_TO_CLIPBOARD actions |
| `blockhighlight/BlockHighlight.kt` | `BlockHighlightManager` glowing invisible shulker at block position, `Player.highlightBlock(x, y, z, durationTicks)`, `Player.clearHighlights()`, auto-remove after duration |
| `nametag/NameTag.kt` | `Player.setNameTag { prefix(); suffix(); displayName() }` DSL, `NameTagManager` per-player custom name via prefix+name+suffix Component, `Player.clearNameTag()` |
| `playerlist/PlayerList.kt` | `playerList { header(); footer(); updateIntervalTicks }` DSL, `PlayerListManager` periodic per-player header/footer updates, MiniMessage support |

### Game Framework (21)
| Util | Summary |
|---|---|
| `gamestate/GameState.kt` | `gameStateMachine(initialState) { allow(); onEnter(); timedTransition() }` DSL |
| `arena/Arena.kt` | `arena(name) { instance(); region(); spawn() }` DSL, `ArenaRegistry` |
| `queue/Queue.kt` | `gameQueue(name) { minPlayers(); maxPlayers(); onStart() }` DSL, `QueueRegistry`, `simpleQueue(name)` FIFO variant, `Player.joinQueue/leaveQueue/queuePosition` extensions |
| `mappool/MapPool.kt` | `mapPool(name) { map(); strategy() }` DSL, voting, RANDOM/ROTATION/VOTE |
| `kit/Kit.kt` | `kit(name) { item(); helmet(); chestplate() }` DSL, `KitRegistry` |
| `loot/LootTable.kt` | `lootTable(name) { entry(material, weight); rolls() }` DSL |
| `vote/Vote.kt` | `poll(question) { option(); durationTicks(); onComplete() }` DSL |
| `lobby/Lobby.kt` | `lobby { instance; spawnPoint; hotbarItem() }` DSL, protection (blocks/damage/hunger/inventory), void teleport, `lockInventory` cancels InventoryPreClickEvent/ItemDropEvent/PlayerSwapItemEvent |
| `achievement/Achievement.kt` | `achievement(id) { name; maxProgress }` DSL, progress tracking, unlock notifications |
| `graceperiod/GracePeriod.kt` | `gracePeriod(name) { duration(); cancelOnMove(); cancelOnAttack(); onEnd {} }` DSL, `GracePeriodManager`, invulnerability management |
| `condition/Condition.kt` | `condition { hasPermission("x") and isAlive() and not(isFrozen()) }` DSL, composable `Condition<Player>` with `and`/`or`/`not`/`xor`, built-in conditions |
| `stattracker/StatTracker.kt` | `StatTracker.increment(player, "kills")`, `statTracker { stat(); derived("kdr") {} }` DSL, ConcurrentHashMap storage, top-N leaderboard, derived stats, `renderLeaderboard()`, `Player.sendLeaderboard()`, `LeaderboardRegistry` |
| `roundmanager/RoundManager.kt` | `roundManager(name) { rounds(); roundDuration(); onRoundStart {}; onGameEnd {} }` DSL, round/intermission state machine, per-round scores |
| `matchresult/MatchResult.kt` | `matchResult { winner(); losers(); stat("kills") {}; mvp() }` DSL, `MatchResultDisplay` rendered summary, `MatchResultManager` history tracking |
| `areaeffect/AreaEffect.kt` | `areaEffect(name) { region(); instance(); effect(); interval(); onEnter {}; onExit {} }` DSL, `AreaEffectManager`, timer-based region scanning, auto-remove on exit |
| `combatarena/CombatArena.kt` | `combatArena(name) { instance(); spawnPoints(); maxPlayers(); kit(); duration(); onKill {}; onEnd {} }` DSL, `CombatArenaManager`, kill/death/damage tracking, `ArenaResult` |
| `minigametimer/MinigameTimer.kt` | `minigameTimer(name) { duration(); display(BOSS_BAR/ACTION_BAR/TITLE); onTick {}; onHalf {}; onQuarter {}; onEnd {} }` DSL, start/pause/resume/stop, addTime/removeTime, milestone callbacks |
| `taskqueue/TaskQueue.kt` | `taskQueue(name) { maxConcurrent(1); onComplete {}; onError {} }` DSL, `TaskQueue.submit {}`, sequential/limited-concurrency async execution on virtual threads, pause/resume/stop, `TaskQueueRegistry` |
| `teambalance/TeamBalance.kt` | `TeamBalance.balance(players, teamCount, scorer)`, snake-draft distribution, `suggestSwap()` variance minimization, `autoBalance()` |
| `knockback/Knockback.kt` | `KnockbackProfile(horizontal, vertical, extraHorizontal, extraVertical, friction)`, `KnockbackManager` per-player profile overrides, `Entity.applyKnockback(source, profile)`, `Entity.applyDirectionalKnockback()`, `knockbackProfile(name) {}` DSL |
| `respawn/Respawn.kt` | `RespawnManager` per-player and default respawn points, `Player.setRespawnPoint()`, `Player.clearRespawnPoint()`, `Player.getCustomRespawnPoint()`, instance resolution by identity hash |
| `instancepool/InstancePool.kt` | `instancePool(name, poolSize) { factory }`, `InstancePool` with `acquire()`/`release()`, auto-warmup pool, excess unregistration, `availableCount`/`inUseCount`/`totalCount` |
| `timer/Timer.kt` | `gameTimer(name) { durationSeconds(); onTick {}; onComplete {}; display {} }` DSL, `GameTimer` with start/stop/reset, per-player viewer tracking, action bar display |

### World (15)
| Util | Summary |
|---|---|
| `world/WorldManager.kt` | Named instance management, `create(name) { generator(); flat(); void() }` DSL |
| `chunkloader/ChunkLoader.kt` | `ChunkLoader.loadRadius()`, `preloadAroundSpawn()`, `unloadOutsideRadius()` |
| `worldborder/WorldBorder.kt` | `instance.managedWorldBorder { diameter(); center() }` DSL, `shrinkTo()`, `expandTo()` |
| `worldreset/WorldReset.kt` | `resetChunks()`, `clearArea()`, `fillArea()`, `recreateInstance()` |
| `anvilloader/AnvilWorldLoader.kt` | `AnvilWorldLoader.load(name, path)` (validates region/ + .mca), `loadAndPreload(name, path, centerChunkX, centerChunkZ, radius)`, `verifyLoaded(instance, pos)` |
| `worldgenerator/WorldGenerator.kt` | `layeredGenerator {}` DSL, `flatGenerator()`, `voidGenerator()`, `checkerboardGenerator()` |
| `schematic/Schematic.kt` | Sponge v2 schematic loader, `schematic.paste(instance, origin)` |
| `region/Region.kt` | Sealed `Region` (Cuboid/Sphere/Cylinder), `RegionManager`, spatial queries |
| `boundary/Boundary.kt` | `boundary(name) { cuboid(); circle(); onBlocked() }` DSL, `BoundaryManager`, invisible wall system |
| `weathercontrol/WeatherControl.kt` | `instanceWeather(instance) { rainy(); duration() }` DSL, `WeatherController`, per-instance weather |
| `blocksnapshot/BlockSnapshot.kt` | `Instance.captureSnapshot(region)`, `BlockSnapshot.capture/restore/restoreAsync/diff/pasteAt/createInstance`, `Instance.blockRestore {}` DSL with auto-restore timer, packed XYZ storage |
| `selectiontool/SelectionTool.kt` | `SelectionManager`, `Player.setPos1/setPos2`, `Player.getSelection()`, wand item, particle boundary display, `Player.fillSelection(block)`, `countBlocks()` |
| `randomteleport/RandomTeleport.kt` | `randomTeleport(player) { minX(); maxX(); minZ(); maxZ(); instance(); maxAttempts(); safeCheck() }` DSL, `Player.randomTeleport {}` extension, safe location finding |
| `blockpalette/BlockPalette.kt` | `blockPalette(name) { block(Block.X, weight) }` DSL, weighted random block selection, `fillRegion()`, `Instance.fillWithPalette()` |
| `fallingblock/FallingBlockUtil.kt` | `spawnFallingBlock(instance, position, block, velocity, onLand)` with ground detection and 600-tick auto-remove, `launchBlock(instance, position, block, direction, speed)` directional launch |
| `structureblock/StructureBlock.kt` | `Structure(name, blocks)` with `paste()`, `pasteRotated90()`, `clear()`, `captureStructure(name, instance, from, to)` region capture, `StructureRegistry` CRUD |
| `voidteleport/VoidTeleport.kt` | `voidTeleport { threshold(-64.0); destination { player -> pos }; onTeleport {} }` DSL, `VoidTeleportManager` global `PlayerMoveEvent` listener, configurable Y threshold, respawnPoint default destination |

### Player (17)
| Util | Summary |
|---|---|
| `snapshot/InventorySnapshot.kt` | `InventorySnapshot.capture(player)` / `snapshot.restore(player)` |
| `combatlog/CombatLog.kt` | `CombatTracker`, `Player.isInCombat`, `tagCombat()` |
| `afk/AfkDetector.kt` | Movement/chat tracking, configurable threshold, AFK/return callbacks |
| `freeze/Freeze.kt` | `FreezeManager`, `Player.freeze()`, `Player.isFrozen` |
| `vanish/Vanish.kt` | `VanishManager`, `Player.vanish()`, packet-level hiding |
| `spectate/Spectate.kt` | `SpectateManager`, `Player.spectatePlayer()`, restores previous game mode |
| `compass/CompassTracker.kt` | `CompassTracker.track()`, action bar direction/distance, `Player.trackPlayer()` |
| `trail/Trail.kt` | `TrailManager`, `TrailConfig(particle, count)`, `Player.setTrail()` |
| `deathmessage/DeathMessage.kt` | `deathMessages { pvp(); fall(); void(); generic() }` DSL, damage type tracking via Tag, placeholder replacement, broadcast scope |
| `warmup/Warmup.kt` | `warmup(player, "Teleporting", 5.seconds) { cancelOnMove(); cancelOnDamage(); onComplete {} }` DSL, `WarmupManager`, action bar progress, cancel triggers (MOVE, DAMAGE, COMMAND) |
| `velocityhelper/VelocityHelper.kt` | `Player.launchUp()`, `launchForward()`, `launchToward()`, `knockbackFrom()`, `Entity.freeze()`, `calculateParabolicVelocity()` |
| `inventoryserializer/InventorySerializer.kt` | `InventorySerializer.serialize(player): ByteArray`, `deserialize(player, data)`, `Player.serializeInventory()`, `Player.deserializeInventory()` extensions |
| `playervault/PlayerVault.kt` | `playerVault { maxVaults(); rows(); titleFormat() }` DSL, `open(player, vaultId)`, `getVault()`, per-player vault storage |
| `inventoryutil/InventoryUtil.kt` | Player inventory extensions: `hasItem()`, `countItem()`, `removeItem()`, `giveItem()`, `firstEmptySlot()`, `clearInventory()`, `sortInventory()`, `swapSlots()` |
| `playerdata/PlayerData.kt` | `PlayerDataManager` full player state snapshots (position, health, food, gameMode, level, inventory), `Player.captureData()`, `Player.restoreData()`, `Player.restoreLatest()`, timestamped history |
| `signprompt/SignPrompt.kt` | `SignPromptManager` sign-based text input prompts, `Player.openSignPrompt(lines) { player, lines -> }`, callback-driven response handling, cancel support |
| `waypoint/Waypoint.kt` | `WaypointManager` global and per-player named waypoints with position/instance/icon, `Player.setWaypoint()`, `Player.removeWaypoint()`, `Player.getWaypoint()`, `Player.allWaypoints()` |

### Advanced (10)
| Util | Summary |
|---|---|
| `commandalias/CommandAlias.kt` | `registerAlias("tp", "teleport")`, `registerAliases()` batch |
| `entityeffect/EntityEffect.kt` | `entityEffect { entityType; durationTicks; onSpawn; onTick; onRemove }` DSL, `spawnTemporaryEntity()` |
| `permissions/Permissions.kt` | `PermissionManager`, group hierarchy with inheritance, `Player.hasOrbitPermission()` |
| `pathfinding/Pathfinding.kt` | A* pathfinding, `Pathfinder.findPath(instance, start, end)`, configurable max iterations |
| `replay/Replay.kt` | `ReplayRecorder`, `ReplayPlayer`, `ReplayManager`, position/block/chat frame recording |
| `entityspawnerpool/EntitySpawnerPool.kt` | `entityPool(EntityType.ZOMBIE, poolSize) { onAcquire {}; onRelease {} }` DSL, `EntityPool.acquire()/release()`, auto-expand, recycled entities |
| `entitycleanup/EntityCleanup.kt` | `entityCleanup(instance) { maxAge(); maxPerInstance(); excludeTypes(); warningMessage() }` DSL, `EntityCleanupManager`, timer-based cleanup, item-first priority, per-instance config |
| `entitymount/EntityMount.kt` | `EntityMountManager.mount(rider, vehicle)`, `dismount()`, `mountStack()`, `mountConfig { speedMultiplier(); jumpBoost(); steeringOverride() }` DSL, `Player.mountEntity()`/`dismountEntity()` extensions |
| `entityleash/EntityLeash.kt` | `EntityLeashManager.leash(entity, holder, maxDistance)`, `unleash()`, velocity correction scheduler, `LeashHandle`, `Entity.leashTo()`, `Entity.unleash()`, `Entity.isLeashed()`, `Entity.leashHolder()` |
| `entitystack/EntityStack.kt` | `StackedEntity(base, riders)` with `addRider()`/`removeTop()`/`removeAll()`/`despawn()`, `EntityStackManager` singleton with `createStack(instance, position, baseType)`, `getStack()`, `removeStack()`, `all()`, `clear()` |

### Infrastructure (26)
| Util | Summary |
|---|---|
| `cooldown/Cooldown.kt` | Generic `Cooldown<K>` with `isReady/use/tryUse/remaining/reset/cleanup`, `NamedCooldown` (player+key with warning message), `MaterialCooldown` (player+material), `SkillCooldown` (visual indicator via BossBar/ActionBar), `Player.isOnCooldown/useCooldown/cooldownRemaining` extensions |
| `spawnentity/SpawnEntity.kt` | `spawnEntity(EntityType.X) { position(); instance(); customName(); health(); onSpawn {} }` DSL, `Instance.spawnEntity()` extension |
| `countdown/Countdown.kt` | `countdown(seconds) { onTick(); onComplete() }` DSL |
| `scheduler/Scheduler.kt` | `delay(ticks)`, `repeat(ticks)`, `delayedRepeat()`, `repeatingTask {}` DSL |
| `itembuilder/ItemBuilder.kt` | `itemStack(material) { name(); lore(); unbreakable(); glowing() }` DSL |
| `teleport/Teleport.kt` | `TeleportManager`, warmup teleport with move cancel |
| `particle/Particle.kt` | `particleEffect(type, count, offset) {}` DSL, shapes (Circle, Sphere, Helix, Line, Cuboid), `Instance.spawnParticle/spawnParticleLine/spawnParticleCircle/spawnBlockBreakParticle` (uses `sendGroupedPacket` for batched delivery), `Player.showParticleShape {}` DSL |
| `sound/Sound.kt` | `soundEffect(type, source, volume, pitch) {}` DSL |
| `team/Team.kt` | `TeamManager.create(name) {}` DSL, `Player.joinTeam()` |
| `npc/Npc.kt` | Packet-based fake NPCs with `NpcVisual` sealed interface: `SkinVisual` (player skin), `EntityVisual` (any EntityType + raw metadata), `ModelVisual` (model-only, invisible INTERACTION hitbox). `npc(name) { skin(); entityType(); modelOnly(); metadata(); model {} }` DSL, configurable `nameOffset`, TextDisplay name, per-player visibility, optional `StandaloneModelOwner` for Blockbench model attachment (visibility synced), `Instance.spawnNpc()`, `Player.showNpc/hideNpc()` |
| `chat/Chat.kt` | `mm(text)`, `Player.sendMM()`, `Instance.broadcastMM()`, `message {}` builder |
| `placeholder/Placeholder.kt` | `PlaceholderRegistry`, `Player.resolvePlaceholders(text)` |
| `eventbus/EventBus.kt` | Custom `EventBus`, `on<T> {}`, `emit(event)`, `globalEventBus` |
| `metadata/EntityMetadata.kt` | `Entity.setString/getInt/setFloat/...` Tag shortcut extensions, `EntityPropertyRegistry` typed property system, `Entity.setProperty<T>/getProperty<T>/removeProperty/hasProperty/propertyKeys` |
| `entitytracker/EntityTracker.kt` | `Instance.nearbyEntities()`, `Player.nearestPlayer()`, `entitiesInLine()` |
| `protection/Protection.kt` | Unified protection: sealed `ProtectionZone` (RegionZone/ChunkZone/RadiusZone), `ProtectionFlag` enum (BREAK/PLACE/INTERACT/PVP/MOB_DAMAGE), `ProtectionManager` with single EventNode, `protectRegion {}`, `protectChunk {}`, `protectSpawn {}` DSL |
| `damage/Damage.kt` | `DamageTracker` per-player damage history, `DamageIndicator` floating TextDisplay damage numbers, `DamageMultiplierManager` per-player per-source multipliers, `Player.setDamageMultiplier/getDamageMultiplier/removeDamageMultiplier` extensions |
| `raytrace/RayTrace.kt` | `rayTraceBlock()`, `rayTraceEntity()`, `raycast()` (entity+block in one pass with bounding box checks), `Player.lookDirection/rayTraceBlock/rayTraceEntity/lookingAt/getLookedAtEntity/getLookedAtBlock` extensions |
| `hotbar/Hotbar.kt` | `hotbar(name) { slot(0, item) { ... } }` DSL, apply/remove/install |
| `portal/Portal.kt` | `portal(name) { region(); destination }` DSL, `PortalManager` |
| ~~`resourcepack/`~~ | Removed — pack sending delegated to proxy |
| `animation/Animation.kt` | `blockAnimation {}` / `entityAnimation {}` DSL, keyframes, interpolation, `packetOnly` mode for visual-only block animations via `BlockChangePacket` |
| `inventorylayout/InventoryLayout.kt` | `inventoryLayout { border(); slot(); pattern(); centerItems() }` DSL |
| `entityai/EntityAI.kt` | `creature.configureAI { hostile(); passive(); neutral() }` DSL, AI presets |
| `joinleavemessage/JoinLeaveMessage.kt` | `joinLeaveMessages { joinFormat(); leaveFormat() }` DSL, MiniMessage, uninstallable handle |
| `blockbreakanimation/BlockBreakAnimation.kt` | `Instance.animateBlockBreak(position, ticks)`, progressive 0-9 crack stages |
| `fireworkdisplay/FireworkDisplay.kt` | `fireworkShow(instance) { at(0) { launch(pos) { } } }` DSL, timed launches |
| `tpsmonitor/TPSMonitor.kt` | `TPSMonitor.install()`, `currentTPS`, `averageTPS`, `mspt`, 1200-tick ring buffer |
| `mobspawner/MobSpawner.kt` | `spawnMob(EntityType.ZOMBIE) { instance(); position(); health(); drops {}; equipment {} }` DSL, `waveSpawner { wave(1) { mob() } }` DSL, `MobSpawnerPoint` periodic spawning |
| `podium/Podium.kt` | `podium(instance) { first(); second(); third(); displayDuration() }` DSL, pedestal blocks, firework, auto-cleanup |
| `coinflip/CoinFlip.kt` | `coinFlip { onHeads {}; onTails {} }`, `diceRoll(sides) {}`, `weightedRandom<T> {}` DSL, animated reveal |
| `spectatorcam/SpectatorCam.kt` | `spectatorCam(player) { target(); mode(FIRST_PERSON/FREE_CAM/ORBIT) }` DSL, orbit camera, player cycling |
| `cinematic/CinematicCamera.kt` | `cinematic(player) { node(time, pos); lookAt(entity); loop(); onComplete {} }` DSL, keyframe path with catmull-rom/bezier/linear position interpolation, quaternion slerp rotation, dynamic lookAt target tracking, reuses `KeyframeInterpolator` + `quatSlerp` |
| `screen/Screen.kt` | `screen(player, cameraPos) { cursor {}; button(id, x, y, w, h) { onClick {}; hoverScale() }; label(id, x, y) { text() } }` DSL, packet-only 2D screen projection with ITEM_DISPLAY cursor, AABB hit-testing, hover feedback, delta-based mouse input via OAK_BOAT mount, `ScreenProjection.kt` math engine |
| `chestloot/ChestLoot.kt` | `chestLoot(name) { tier("common") { item() }; fillChestsInRegion() }` DSL, weighted tiers, amount ranges |
| `npcdialog/NPCDialog.kt` | `npcDialog(npcName) { page("greeting") { text(); option("quest") {} } }` DSL, tree-structured dialog, clickable chat options |
| `autorestart/AutoRestart.kt` | `autoRestart { after(6.hours); warnings(30.minutes, 10.minutes); warningMessage(); onRestart {} }` DSL, `AutoRestartManager.scheduleRestart/cancelRestart/getTimeRemaining`, broadcast warnings, kick on restart |
| `customrecipe/CustomRecipe.kt` | `shapedRecipe(result) { pattern(); ingredient() }`, `shapelessRecipe {}`, `smeltingRecipe()`, `RecipeRegistry` matching, `RecipeHandle` unregistration |
| `entityequipment/EntityEquipment.kt` | `Entity.equip { helmet(); chestplate(); mainHand() }` DSL, `Entity.clearEquipment()`, `Entity.getEquipmentSnapshot()`, `EquipmentSnapshot.apply()` |
| `worldedit/WorldEdit.kt` | `WorldEdit.copy/paste/rotate/flip/fill/replace/undo/redo`, `ClipboardData` packed Long coords, per-player undo/redo stacks (max 50), Region-aware, `fillPattern()` weighted random block fill |
| `commandbuilder/CommandBuilder.kt` | `command(name) { aliases(); permission(); playerOnly(); subCommand(); onPlayerExecute {}; onExecute() }` DSL, `CommandExecutionContext(player, args, locale)`, virtual-thread execution, typed arguments, tab completion, recursive sub-commands, `RankManager` permissions |
| `commandbuilder/CommandHelpers.kt` | `OnlinePlayerCache` (5s refresh from SessionStore), `suggestPlayers(prefix)`, `resolvePlayer(name)` (online then PlayerStore) |
| `entityformation/EntityFormation.kt` | `entityFormation { circle(); line(); grid(); wedge() }` DSL, `Formation.apply(entities, center)`, `animate(speed)`, yaw rotation |
| `musicsystem/MusicSystem.kt` | `song(name) { bpm(); note(tick, instrument, pitch) }` DSL, `Instance.playSong(pos, song)`, 16 instruments, tick-based playback, `SongManager` |
| `broadcastscheduler/BroadcastScheduler.kt` | `broadcastScheduler { intervalSeconds(300); shuffled = true; message("<gold>Welcome!") }` DSL, `BroadcastScheduler` with `start()`/`stop()`/`isRunning`, cyclic message rotation to all online players |
| `motd/Motd.kt` | `motd { line1 = "<gradient:gold:yellow>Nebula"; line2 = "<gray>Play now!" }` DSL, `MotdManager` singleton with `AtomicReference<MotdConfig>`, `ServerListPingEvent` listener, MiniMessage rendering |
| `pagination/Pagination.kt` | `paginatedView<T> { items(); pageSize = 10; render { item, index -> }; header {}; footer {} }` DSL, `PaginatedView.send(player, page)`, translated header/footer, auto page clamping |

## Cosmetics System — `cosmetic/`
Player visual customization system with persistent ownership/equip state via Hazelcast stores and GUI menus.

### Categories
| Category | Visual Effect | Utility |
|---|---|---|
| `ARMOR_SKIN` | Custom armor texture/model on player | `CustomArmorRegistry.equipFullSet()` |
| `KILL_EFFECT` | Particle burst at victim's death location | `Instance.showParticleShape {}` |
| `TRAIL` | Particle trail behind player while moving | Scheduled + `Instance.spawnParticleAt()` |
| `WIN_EFFECT` | Celebratory particles around winner | `Player.showParticleShape {}` |
| `PROJECTILE_TRAIL` | Particles along arrow flight path | Tick task + `Instance.spawnParticleAt()` |

### CosmeticDefinition (`CosmeticDefinition.kt`)
- `CosmeticRarity` enum: `COMMON` (`<gray>`), `RARE` (`<blue>`), `EPIC` (`<dark_purple>`), `LEGENDARY` (`<gold>`)
- `CosmeticDefinition` data class: `id`, `category` (CosmeticCategory), `nameKey`, `descriptionKey`, `rarity`, `material` (GUI display), `data` (category-specific config map)

### CosmeticRegistry (`CosmeticRegistry.kt`)
In-memory `object` singleton backed by `ConcurrentHashMap`:
- `register(definition)` — adds to registry
- `get(id)` / `operator []` — lookup by ID
- `byCategory(category)` — filter by category
- `all()` — all definitions
- `loadFromResources(resources)` — loads `cosmetics.json` via `ResourceManager.loadOrCopyDefault`

### CosmeticMenu (`CosmeticMenu.kt`)
GUI menus using `gui {}` and `paginatedGui {}` DSL:
- `openCategoryMenu(player)` — 3-row GUI with one icon per category
- `openCosmeticList(player, category)` — paginated list, items show owned/equipped status via glowing/lore, click to equip/unequip
- Equip/unequip via `CosmeticStore.executeOnKey(uuid, EquipCosmeticProcessor(...))`

### CosmeticApplier (`CosmeticApplier.kt`)
Applies visual effects based on equipped cosmetics:
- `applyArmorSkin(player, cosmeticId)` — `data["armorId"]` → `CustomArmorRegistry[armorId]?.equipFullSet(player)`
- `clearArmorSkin(player)` — removes all armor equipment
- `playKillEffect(instance, position, cosmeticId)` — reads `data["particle"]` + `data["shape"]`, calls `instance.showParticleShape {}`
- `playWinEffect(player, cosmeticId)` — same pattern on player
- `spawnTrailParticle(instance, position, cosmeticId)` — single particle spawn per tick
- `spawnProjectileTrailParticle(instance, position, cosmeticId)` — projectile particle spawn

### CosmeticListener (`CosmeticListener.kt`)
Event listeners via `EventNode.all("cosmetic-listeners")`:
- **Per-mode filtering**: `@Volatile var activeConfig: CosmeticConfig` — set by `Orbit.kt` from `mode.cosmeticConfig` before `install()`. Every cosmetic operation checks `isAllowed(category, cosmeticId)` which verifies `category.name in activeConfig.enabledCategories && cosmeticId !in activeConfig.blacklist`.
- **Trail**: `PlayerMoveEvent` — throttled to 200ms, spawns trail particle at player position (gated by TRAIL category + blacklist)
- **Projectile trail**: 2-tick scheduled task — scans arrows, reads shooter tag, spawns particle (gated by PROJECTILE_TRAIL category + blacklist)
- **Kill effect**: `onPlayerEliminated(killer, victimPosition)` — hook from game modes (gated by KILL_EFFECT category + blacklist)
- **Win effect**: `onGameWon(winner)` — hook from game modes (gated by WIN_EFFECT category + blacklist)
- **Armor skin**: `PlayerSpawnEvent` — applies saved armor skin on join (gated by ARMOR_SKIN category + blacklist)

### Config — `cosmetics.json`
JSON array of `CosmeticDefinition` objects. Bundled default has 10 sample cosmetics:
- `armor_knight` (ARMOR_SKIN/RARE), `kill_flame_burst` (KILL_EFFECT/COMMON), `kill_heart_explosion` (KILL_EFFECT/EPIC)
- `trail_flame` (TRAIL/COMMON), `trail_soul` (TRAIL/RARE), `trail_enchant` (TRAIL/EPIC)
- `win_firework_helix` (WIN_EFFECT/RARE), `win_totem` (WIN_EFFECT/LEGENDARY)
- `projectile_flame` (PROJECTILE_TRAIL/COMMON), `projectile_dragon` (PROJECTILE_TRAIL/LEGENDARY)

### Integration
- Store registered in `hazelcastModule { stores { +CosmeticStore } }` in Orbit.kt
- Registry loaded after `app.start()`: `CosmeticRegistry.loadFromResources(app.resources)`
- Active config wired from mode: `CosmeticListener.activeConfig = mode.cosmeticConfig`
- Listener installed on global handler: `CosmeticListener.install(handler)`
- Command: `/cosmetics` opens `CosmeticMenu.openCategoryMenu(player)`
- JSON configs (`hub.json`, `hoplite.json`) include `cosmetics` section with `enabledCategories` and `blacklist`. Omitting the section uses Gson defaults (all categories enabled, empty blacklist).

## Translation System — `translation/`
- `OrbitTranslations.register(translations)` registers all keys for mechanics and utilities in `en` locale. Hub text is config-driven (not translations).
- `TranslationExtensions.kt` provides:
  - `Player.translate(key, vararg args)` — locale-aware translation returning `Component`
  - `Player.translateRaw(key, vararg args)` — locale-aware raw string translation
  - `translateDefault(key, vararg args)` — default-locale translation (no player context)
  - `translateFor(uuid, key, vararg args)` — UUID-based locale-aware translation
- All utility player-facing strings route through the translation system:
  - `achievement/Achievement.kt` — unlock notification title (`orbit.util.achievement.unlocked`)
  - `compass/CompassTracker.kt` — action bar display (`orbit.util.compass.display`)
  - `instancepool/InstancePool.kt` — kick message on release (`orbit.util.instance_pool.released`)
  - `stattracker/StatTracker.kt` — rank entry rendering (`orbit.util.leaderboard.entry`)
  - `matchresult/MatchResult.kt` — draw/winner/mvp/stats/duration labels (`orbit.util.match_result.*`)
  - `pagination/Pagination.kt` — page header and navigation footer (`orbit.util.pagination.*`)
  - `timer/Timer.kt` — default timer display (`orbit.util.timer.display`)
  - `world/WorldManager.kt` — kick message on delete (`orbit.util.world.deleted`)
  - `anvilloader/AnvilWorldLoader.kt` — kick message on unload (`orbit.util.world.unloading`)
- All mechanic player-facing strings route through the translation system:
  - `anvil/AnvilModule.kt` — inventory title (`orbit.mechanic.anvil.title`)
  - `armortrim/ArmorTrimModule.kt` — inventory title (`orbit.mechanic.armor_trim.title`)
  - `barrel/BarrelModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.barrel.title`)
  - `blastfurnace/BlastFurnaceModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.blast_furnace.title`)
  - `brewingstand/BrewingStandModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.brewing_stand.title`)
  - `bundle/BundleModule.kt` — inventory title (`orbit.mechanic.bundle.title`)
  - `cartographytable/CartographyTableModule.kt` — inventory title (`orbit.mechanic.cartography_table.title`)
  - `commandblock/CommandBlockModule.kt` — info message with `<command>` placeholder (`orbit.mechanic.command_block.info`)
  - `crafter/CrafterModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.crafter.title`)
  - `crafting/CraftingModule.kt` — inventory title (`orbit.mechanic.crafting.title`)
  - `doublechest/DoubleChestModule.kt` — large/single chest titles via `translateDefault` (`orbit.mechanic.double_chest.title`, `orbit.mechanic.chest.title`)
  - `dropper/DropperModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.dropper.title`)
  - `enchanting/EnchantingModule.kt` — inventory title (`orbit.mechanic.enchanting.title`)
  - `enderchest/EnderChestModule.kt` — per-player inventory title (`orbit.mechanic.ender_chest.title`)
  - `fletchingtable/FletchingTableModule.kt` — inventory title (`orbit.mechanic.fletching_table.title`)
  - `grindstone/GrindstoneModule.kt` — inventory title (`orbit.mechanic.grindstone.title`)
  - `headdrop/HeadDropModule.kt` — skull name/lore with `<victim>`/`<killer>` placeholders (`orbit.mechanic.head_drop.name`, `orbit.mechanic.head_drop.lore`)
  - `hopper/HopperModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.hopper.title`)
  - `itemrepair/ItemRepairModule.kt` — inventory title (`orbit.mechanic.item_repair.title`)
  - `lectern/LecternModule.kt` — book header and empty messages (`orbit.mechanic.lectern.book_header`, `orbit.mechanic.lectern.empty`)
  - `loom/LoomModule.kt` — inventory title (`orbit.mechanic.loom.title`)
  - `map/MapModule.kt` — inventory title (`orbit.mechanic.map.title`)
  - `shulkerbox/ShulkerBoxModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.shulker_box.title`)
  - `sign/SignModule.kt` — sign header and line messages with `<number>`/`<text>` placeholders (`orbit.mechanic.sign.header`, `orbit.mechanic.sign.line`)
  - `smithingtable/SmithingTableModule.kt` — inventory title (`orbit.mechanic.smithing_table.title`)
  - `smoker/SmokerModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.smoker.title`)
  - `stonecutter/StonecutterModule.kt` — inventory title (`orbit.mechanic.stonecutter.title`)
  - `trading/TradingModule.kt` — inventory title (`orbit.mechanic.trading.title`)
  - `recoverycompass/RecoveryCompassModule.kt` — action bar with `<direction>`/`<distance>` placeholders (`orbit.mechanic.recovery_compass.distance`)

## Admin Commands

### `/me` — ModelEngine Command (`utils/modelengine/ModelEngineCommand.kt`)
Permission: `orbit.modelengine`

| Sub-command | Args | Action |
|---|---|---|
| `list` | — | List all registered blueprints with bone count + animation count |
| `info <blueprint>` | `blueprint: Word` | Show blueprint details: bones (tree), animations (name, duration, loop mode), root bones |
| `spawn <blueprint> [scale]` | `blueprint: Word`, `scale: Float (default 1.0)` | Spawn `standAloneModel` at player position, show to player |
| `despawn` | — | Remove all standalone models spawned via `/me spawn` |
| `reload <name>` | `name: Word` | Re-parse `data/models/<name>.bbmodel` via `ModelGenerator.generateRaw`, re-register blueprint |
| `entities` | — | List all tracked `ModeledEntity` instances with position, model names, viewer count |
| `ids` | — | Show ModelIdRegistry stats: total assigned IDs, sample entries |

- Tab completion for `<blueprint>` from `ModelEngine.blueprints().keys`
- Tab completion for `<name>` from `resources.list("models", "bbmodel")`
- Spawned models tracked in file-level `ConcurrentHashMap.newKeySet<StandaloneModelOwner>()` for `despawn`
- Registered in `Orbit.kt` after `CustomContentRegistry.init()`

### `/cc` — CustomContent Command (`utils/customcontent/CustomContentCommand.kt`)
Permission: `orbit.customcontent`

| Sub-command | Args | Action |
|---|---|---|
| `items` | — | List all custom items: id, base material, CMD ID |
| `blocks` | — | List all custom blocks: id, hitbox type, allocated state, CMD ID |
| `give <item> [amount]` | `item: Word`, `amount: Int (default 1)` | Give `CustomItem.createStack(amount)` to player |
| `info <id>` | `id: Word` | Show full details of item or block (checks both registries) |
| `pack` | — | Trigger `CustomContentRegistry.mergePack()`, report size + SHA-1 |
| `allocations` | — | Show per-hitbox pool usage: used/total for each `BlockHitbox` type |

- Tab completion for `<item>` from `CustomItemRegistry.all().map { it.id }`
- Tab completion for `<id>` from combined item + block IDs
- Registered in `Orbit.kt` after `CustomContentRegistry.init()`. Pack sending removed — delegated to proxy.
