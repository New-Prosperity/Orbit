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
- Env vars: `SERVER_NAME` (required), `GAME_MODE` (optional), `SERVER_PORT` (default 25565), `HAZELCAST_LICENSE`, `VELOCITY_SECRET`, `P_SERVER_UUID`, `SERVER_HOST`.
- Hazelcast: lite member with 17 stores.
- Init order: `environment {}` → `appDelegate` → `app.start()` → `MinecraftServer.init()` → `resolveMode()` → `mode.install(handler)` → common listeners → `server.start()` → registration → shutdown hook.
- Common listeners: `AsyncPlayerConfigurationEvent` (locale cache, set spawning instance/respawn from mode), `PlayerDisconnectEvent` (evict locale), `OnlinePlayerCache` refresh (5s).
- `resolveMode(env)` selects `ServerMode` by `GAME_MODE` env var: `null` → `HubMode`, else → `error()` (extend here for minigames).

## Server Mode System — `mode/`

### `ServerMode` interface (`mode/ServerMode.kt`)
```kotlin
interface ServerMode {
    val defaultInstance: InstanceContainer
    val spawnPoint: Pos
    fun install(handler: GlobalEventHandler)
    fun shutdown()
}
```
Each mode owns its instance, event listeners, scheduled tasks, and shutdown. `Orbit.kt` delegates to it.

### `HubMode` (`mode/hub/HubMode.kt`)
- Env vars: `HUB_SPAWN_X/Y/Z/YAW/PITCH` (optional, defaults to 0.5/65.0/0.5/0/0).
- **Hub instance**: Validates and loads Anvil world from `worlds/hub/` (requires `region/` with `.mca` files), preloads chunks centered on spawn point, verifies block data post-load. Falls back to flat grass generator if directory missing.
- **Lobby**: `lobby {}` DSL — Adventure mode, full protection (break/place/damage/hunger/inventory), void teleport at Y=-64.
- **Scoreboard**: `PerPlayerScoreboard` with online count, rank, server name. Updates every 5s.
- **Tab list**: Header/footer with server branding, online count, server name. Updates every 5s.
- **Hotbar**: Compass (slot 4) opens server selector GUI.
- **Server selector**: 3-row GUI with border. Placeholder for game mode entries.
- No `MechanicLoader` — hub mode has no mechanics enabled.
- `shutdown()` cancels update task, uninstalls lobby and hotbar.

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

## Utility Framework — `utils/` (127 files)

### Spatial (1)
| Util | Summary |
|---|---|
| `blockindex/BlockPositionIndex.kt` | Per-instance spatial index for specific block types. Eliminates O(n³) cubic scans. `BlockPositionIndex(targetBlockNames, eventNode).install()`, `positionsNear(instance, center, radius)` O(k), `allPositions(instance)`, `scanChunk(instance, chunk, minY, maxY)`, `evictInstance(hash)`. Coordinate packing: `(x << 40) \| ((y & 0xFFFFF) << 20) \| (z & 0xFFFFF)`. Auto-tracks via `PlayerBlockPlaceEvent`/`PlayerBlockBreakEvent` on scoped eventNode. |

### Display (12)
| Util | Summary |
|---|---|
| `gui/Gui.kt` | `gui(title, rows) {}` DSL, paginated GUI, event-based click handling |
| `scoreboard/Scoreboard.kt` | `scoreboard(title) { line(); animatedLine(); dynamicLine {} }` DSL, `PerPlayerScoreboard`, `AnimatedScoreboard`, `TeamScoreboard`, `ObjectiveTracker`, show/hide/update |
| `hologram/Hologram.kt` | TextDisplay holograms, `Instance.hologram {}` global + `Player.hologram {}` packet-based per-player, DSL builder, billboard/scale/background |
| `bossbar/BossBarManager.kt` | `bossBar(name, color, overlay) {}` DSL, show/hide/update |
| `tablist/TabList.kt` | `Player.tabList { header(); footer() }` DSL |
| `actionbar/ActionBar.kt` | `Player.showActionBar(msg, durationMs)`, `clearActionBar()` |
| `title/Title.kt` | `Player.showTitle { title(); subtitle(); fadeIn(); stay(); fadeOut() }` DSL |
| `healthdisplay/HealthDisplay.kt` | `healthDisplay { format { } }` DSL, periodic display name health suffix updates |
| `entityglow/EntityGlow.kt` | Per-player entity glow via `EntityMetaDataPacket` (no real entities), 20-tick metadata refresh, `Player.setGlowingFor()`, global glow, timed glow |
| `notification/Notification.kt` | `notify(player) { title(); message(); channels(CHAT, ACTION_BAR, TITLE, SOUND) }` DSL, `NotificationManager` broadcast/instance/player, multi-channel (CHAT, ACTION_BAR, TITLE, BOSS_BAR, SOUND), `announceChat()`, `announceActionBar()`, `announceTitle()` convenience functions |
| `playertag/PlayerTag.kt` | `playerTag(player) { prefix(); suffix(); nameColor(); priority() }` DSL, `PlayerTagManager`, priority-based tag stacking, display name/tab/above-head rendering |
| `clickablechat/ClickableChat.kt` | `clickableMessage(player) { text(); clickText("[HERE]") { action(OPEN_URL, url) }; hover() }` DSL, `Player.sendClickable {}`, RUN_COMMAND/SUGGEST_COMMAND/OPEN_URL/COPY_TO_CLIPBOARD actions |

### Game Framework (19)
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

### World (14)
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

### Player (13)
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

### Advanced (9)
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

### Infrastructure (23)
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
| `npc/Npc.kt` | Packet-based fake player NPCs, `npc(name) { skin(); onClick() }` DSL, TextDisplay name, per-player visibility, `Instance.spawnNpc()`, `Player.showNpc/hideNpc()` |
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
| `resourcepack/ResourcePack.kt` | `ResourcePackManager`, pack registration, auto-send on join |
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
| `chestloot/ChestLoot.kt` | `chestLoot(name) { tier("common") { item() }; fillChestsInRegion() }` DSL, weighted tiers, amount ranges |
| `npcdialog/NPCDialog.kt` | `npcDialog(npcName) { page("greeting") { text(); option("quest") {} } }` DSL, tree-structured dialog, clickable chat options |
| `autorestart/AutoRestart.kt` | `autoRestart { after(6.hours); warnings(30.minutes, 10.minutes); warningMessage(); onRestart {} }` DSL, `AutoRestartManager.scheduleRestart/cancelRestart/getTimeRemaining`, broadcast warnings, kick on restart |
| `customrecipe/CustomRecipe.kt` | `shapedRecipe(result) { pattern(); ingredient() }`, `shapelessRecipe {}`, `smeltingRecipe()`, `RecipeRegistry` matching, `RecipeHandle` unregistration |
| `entityequipment/EntityEquipment.kt` | `Entity.equip { helmet(); chestplate(); mainHand() }` DSL, `Entity.clearEquipment()`, `Entity.getEquipmentSnapshot()`, `EquipmentSnapshot.apply()` |
| `worldedit/WorldEdit.kt` | `WorldEdit.copy/paste/rotate/flip/fill/replace/undo/redo`, `ClipboardData` packed Long coords, per-player undo/redo stacks (max 50), Region-aware, `fillPattern()` weighted random block fill |
| `commandbuilder/CommandBuilder.kt` | `command(name) { aliases(); permission(); playerOnly(); subCommand(); onExecute() }` DSL, argument types, tab completion, recursive sub-commands |
| `entityformation/EntityFormation.kt` | `entityFormation { circle(); line(); grid(); wedge() }` DSL, `Formation.apply(entities, center)`, `animate(speed)`, yaw rotation |
| `musicsystem/MusicSystem.kt` | `song(name) { bpm(); note(tick, instrument, pitch) }` DSL, `Instance.playSong(pos, song)`, 16 instruments, tick-based playback, `SongManager` |

## Command DSL — `command/CommandDsl.kt`
- `minestomCommand(name, aliases) { permission; playerOnly; execute {}; suggest {} }` DSL
- `OnlinePlayerCache` refreshes from `SessionStore.all()` every 5s

## Player Resolver — `command/PlayerResolver.kt`
- `resolvePlayer(name): Pair<UUID, String>?` via online lookup then `PlayerStore`

## Translation System — `translation/`
- `OrbitTranslations.register(translations)` registers all keys for mechanics and utilities in `en` locale.
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
