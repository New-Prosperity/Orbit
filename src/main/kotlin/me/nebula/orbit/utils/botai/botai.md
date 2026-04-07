# BotAI

Autonomous bot AI system (actions, goals, brain, personality, skill calibration, memory, mining knowledge) and pathfinding/navigation for Player-type bot entities. Works with `FakePlayer` bots or any `Player` instance. Includes lobby filler for spawning NPCs that play alongside real players.

## AI System

4-layer architecture: **Actions** (atomic operations), **Goals** (high-level objectives), **Brain** (goal selection, humanization, hotbar management), **Skill** (per-bot calibration of accuracy, reaction time, etc.).

### Quick Start
```kotlin
BotAI.install()

val brain = BotAI.attachSurvivalAI(player, skill = BotSkillLevels.SKILLED)

BotAI.detach(player)
BotAI.uninstall()
```

### Preset Configurations
All presets accept optional `personality` and `skill` parameters.

| Preset | Goals |
|---|---|
| `attachSurvivalAI` | Survive, Flee, Attack, EquipArmor, ToolProgression, GatherWood, StripMine, SmeltOres, CraftTool(pickaxe), InventoryManagement, Explore |
| `attachCombatAI` | Survive, CriticalAttack, Attack, ShieldDefense, RangedAttack, UsePotion, Flee, EquipArmor |
| `attachPvPAI` | Survive, CriticalAttack, Attack, ShieldDefense, RangedAttack, UsePotion, Flee, EquipArmor, Bridge |
| `attachGathererAI` | Survive, GatherWood, CraftTool(pickaxe), CraftTool(axe), EquipArmor, Explore |
| `attachPassiveAI` | Survive, Explore |
| `attachMinerAI` | Survive, Flee, ToolProgression, GatherWood, StripMine, CaveExplore, SmeltOres, PlaceFurnace, EquipArmor, InventoryManagement, GatherResource(diamond), Explore |

### Custom AI
```kotlin
val brain = BotAI.attach(
    player,
    AttackNearestGoal(range = 24.0),
    FleeGoal(healthThreshold = 3f),
    StripMineGoal(targetY = 11),
    ToolProgressionGoal(),
    personality = BotPersonalities.WARRIOR,
)
```

### Actions
| Action | Description |
|---|---|
| `WalkTo(target)` | Walk to a point (speed 0.15) |
| `SprintTo(target)` | Sprint to a point (speed 0.22) |
| `BreakBlock(pos)` | Walk to block, swing arm periodically, call `Instance.breakBlock()` after break time (fires `PlayerBlockBreakEvent`, delegates drops to BlockDropsModule) |
| `PlaceBlock(pos, block)` | Walk to position, fire `PlayerBlockPlaceEvent`, place block if not cancelled |
| `AttackEntity(target, times)` | Approach and attack N times |
| `UseItem` | Swing main hand |
| `HoldSlot(slot)` | Set held slot |
| `EquipItem(slot, item)` | Equip armor/equipment |
| `PickupNearbyItems(radius)` | Walk toward nearest `ItemEntity` — Minestom handles actual pickup on proximity |
| `OpenContainer(pos)` | Walk to block, fire `PlayerBlockInteractEvent` + `BlockHandler.onInteract()` to open real inventory |
| `CraftItem(result, recipe)` | Consume materials from player inventory, give result |
| `EatFood` | Find food in inventory, fire `PlayerUseItemEvent` — FoodConsumptionModule handles nutrition/saturation/consumption |
| `Wait(ticks)` | Do nothing for N ticks |
| `LookAt(target)` | Face a point |
| `Sequence(actions)` | Execute actions in order |
| `MineTunnel(direction, length)` | Dig a 2-high straight tunnel with periodic torches |
| `MineStaircase(direction, depth)` | Dig a descending staircase (1 block per step) |
| `MineVein(startPos, targetBlock)` | BFS flood-fill mine all connected ore blocks (max 32) |
| `SmeltItems(furnacePos, input, fuel)` | Walk to furnace, fire interact event to open FurnaceModule inventory, place input+fuel, wait for `FurnaceBlockHandler.tick()` to cook, collect output |
| `PlaceTorch(pos)` | Consume torch, fire `PlayerBlockPlaceEvent`, place torch if not cancelled |
| `DigDown(depth)` | Dig down in a safe staircase pattern (alternating directions) |
| `CriticalHit(target)` | Sprint toward target, jump, wait for peak (velocity check), strike with 1.5x ATTACK_DAMAGE |
| `ShieldBlock(durationTicks)` | Activate off-hand shield via LivingEntityMeta, hold for duration, visual-only blocking |
| `ShootBow(target, chargeTicks)` | Equip bow, fire PlayerUseItemEvent to trigger ProjectilesModule charge, draw animation, release |
| `ThrowProjectile(type, target)` | Equip projectile item, face target, fire PlayerUseItemEvent (ProjectilesModule handles the rest) |
| `PlaceWaterBucket(pos)` | Place water block at position, swap water bucket to empty bucket |
| `BridgeForward(direction, length, block)` | Walk forward placing blocks under feet across gaps, consumes block material |
| `TowerUp(height, block)` | Jump + place block under self repeatedly (jump-peak detection), consumes blocks |
| `DrinkPotion` | Find potion/splash potion in inventory, fire PlayerUseItemEvent |

### Goals
| Goal | Utility | Activates When |
|---|---|---|
| `SurviveGoal` | Health-based (up to 0.95) | Health < 8, has food |
| `FleeGoal(threshold)` | Health + distance based | Health < threshold, enemy nearby |
| `AttackNearestGoal(range)` | Distance + weapon based | Enemy player within range |
| `EquipBestArmorGoal` | 0.15 per unequipped slot | Unequipped armor in inventory |
| `CraftToolGoal(tool)` | 0.3-0.6 | Missing tool, has materials |
| `MineOreGoal(ore)` | 0.55 visible / 0.4 remembered | Ore seen by vision or remembered in memory. Cancels when 16+ ore drops collected. |
| `GatherWoodGoal` | 0.4 | No wood in inventory, uses vision to find logs |
| `BuildShelterGoal` | 0.4 | Has 20+ build blocks |
| `ExploreGoal` | 0.1 | Always (fallback) |
| `StripMineGoal(targetY)` | 0.45 base * resourcefulness | Has pickaxe |
| `CaveExploreGoal` | 0.4 base * curiosity | Has pickaxe, uses vision + exploration for cave detection |
| `SmeltOresGoal` | 0.5 * resourcefulness | Has raw ores + fuel |
| `ToolProgressionGoal` | 0.65 * resourcefulness | Can craft better tool tier |
| `GatherResourceGoal(resource, minCount)` | 0.5 * resourcefulness | Count < minCount |
| `InventoryManagementGoal` | 0.55 | Inventory > 80% full |
| `PlaceFurnaceGoal` | 0.5 | Has raw ores + cobblestone, no furnace visible or remembered |
| `CriticalAttackGoal(range)` | 0.75 * proximity + weapon bonus | Good weapon equipped, enemy within range. Boosted by aggression. |
| `ShieldDefenseGoal` | 0.8 * (1-healthRatio) | Shield in off-hand + nearby threats (within 8 blocks). Boosted by caution. |
| `RangedAttackGoal(range)` | 0.75 * distance factor | Has bow + arrows, enemy within range. Falls back to melee < 8 blocks. Boosted by caution. |
| `UsePotionGoal` | 0.85 at health < 6 | Has potions, health < 10. Boosted by caution. |
| `BridgeGoal` | 0.4 | Gap detected ahead + has 4+ build blocks. Boosted by resourcefulness. |
| `PatrolGoal(waypoints)` | 0.5 | Has waypoints (gametest) |
| `FollowGoal(targetUuid)` | 0.7 | Target player exists (gametest) |

### Mining Knowledge (`MiningKnowledge`)
Utility object for Minecraft block/tool/smelting data.

| Function | Description |
|---|---|
| `requiredTool(block)` | Minimum tool tier needed (null = any) |
| `blockDrops(block)` | What ItemStacks a block drops |
| `breakTime(block, tool)` | Break time in ticks with given tool (covers deepslate variants) |
| `isOre(block)` | Whether block is an ore |
| `smeltResult(input)` | What a material smelts into (delegates to `SMELTING_RECIPES` from FurnaceModule, null if not smeltable) |
| `toolTier(material)` | Tier number: wood=0, stone=1, iron=2, diamond=3, netherite=4 |
| `canMine(tool, block)` | Whether tool tier is sufficient for block |
| `itemValue(material)` | Priority value for inventory management |
| `materialToOre(material)` | Maps material (e.g. DIAMOND) to its ore block |
| `isPickaxe(material)` | Whether material is a pickaxe |
| `bestPickaxe(materials)` | Highest-tier pickaxe from a set of materials |

### Personality System
`BotPersonality(aggression, caution, resourcefulness, curiosity, teamwork)` modifies goal utility scoring. Higher aggression boosts attack goals, higher caution boosts flee goals, etc. Mining/crafting/smelting goals scale with `resourcefulness`. Cave exploration scales with `curiosity`.

| Preset | Traits |
|---|---|
| `WARRIOR` | aggro=0.9, caution=0.2, resource=0.3 |
| `SURVIVOR` | aggro=0.4, caution=0.8, resource=0.9 |
| `EXPLORER` | aggro=0.3, caution=0.5, curiosity=0.9 |
| `BERSERKER` | aggro=1.0, caution=0.0, resource=0.1 |
| `BUILDER` | aggro=0.1, caution=0.7, resource=1.0, curiosity=0.3 |
| `BALANCED` | all 0.5 |
| `random()` | all random |

### Skill Calibration System (`BotSkill.kt`)
`BotSkillLevel` controls how well a bot performs mechanically. Each parameter simulates a different aspect of human skill variance.

| Field | Default | Range | Effect |
|---|---|---|---|
| `aimAccuracy` | 0.7 | 0.0-1.0 | Chance of landing a melee hit; also offsets bow aim |
| `reactionTimeTicks` | 8 | 1-15 | Ticks before shield block activates |
| `criticalHitChance` | 0.5 | 0.0-1.0 | Chance of landing 1.5x crit (vs normal hit) |
| `blockChance` | 0.3 | 0.0-1.0 | Reserved for future shield timing |
| `movementJitter` | 0.1 | 0.0-0.3 | Gaussian offset to movement targets (0=robotic, 0.3=sloppy) |
| `decisionDelay` | 5 | 1-15 | Extra ticks added to goal evaluation interval |
| `miningEfficiency` | 0.8 | 0.5-1.0 | Reserved for break speed multiplier |
| `bridgingSpeed` | 0.6 | 0.3-1.0 | Reserved for bridge pace |

| Preset | Aim | Reaction | Crit | Jitter | Decision |
|---|---|---|---|---|---|
| `BEGINNER` | 0.4 | 15 | 0.2 | 0.25 | 15 |
| `CASUAL` | 0.6 | 10 | 0.4 | 0.15 | 8 |
| `AVERAGE` | 0.7 | 8 | 0.5 | 0.1 | 5 |
| `SKILLED` | 0.85 | 5 | 0.7 | 0.05 | 3 |
| `EXPERT` | 0.95 | 3 | 0.9 | 0.02 | 1 |

`BotSkillLevels.forRating(0.0..1.0)` linearly interpolates between BEGINNER and EXPERT.

Skill is wired into:
- **BotBrain.tick()**: `GOAL_EVAL_INTERVAL + skill.decisionDelay` for goal scoring frequency
- **BotAction.WalkTo/SprintTo**: Movement jitter applied via `BotMovement.moveToward(jitter)`
- **BotAction.AttackEntity**: `aimAccuracy` chance to deal damage (miss = swing but no damage)
- **BotAction.CriticalHit**: `criticalHitChance` to land 1.5x (else 1.0x normal hit)
- **BotAction.ShieldBlock**: `reactionTimeTicks` delay before shield activates
- **BotAction.ShootBow**: Aim offset proportional to `(1 - aimAccuracy) * 4.0` blocks

### Human-Like Behavior (`BotBrain.humanize()`)
Called every tick to add realistic imperfections:
- **Random pauses**: 1-in-200 chance per tick to pause 10-30 ticks (simulates checking inventory, hesitating)
- **Sprint toggling**: When not in combat, 1-in-150 chance to drop sprint for 20-40 ticks
- **Random look variation**: 1-in-40 chance to jitter yaw +/-10 and pitch +/-5

### Hotbar Management (`BotBrain.organizeHotbar()`)
Auto-organizes inventory when items change (dirty-flag detection via hash):
- Slot 0: Best sword (netherite > diamond > iron > stone > wooden)
- Slot 1: Best pickaxe
- Slot 2: Best axe
- Slot 3: Bow/crossbow
- Slot 4: Build blocks (cobblestone, dirt, planks, etc.)
- Slot 5: Food (golden apple > cooked beef > bread > etc.)
- Off-hand: Shield (auto-moved)

### Memory System
`BotMemory` tracks locations (categorized, TTL-based), threats (damage-weighted with decay), resources (categorized), and player sightings (timestamped). Auto-expires stale entries on tick. Mining goals use memory categories: `mined_tunnel`, `explored_cave`, `furnace`. Both `knownLocations` and `resources` use LRU-capped `LinkedHashMap` (max 32 categories each) with access-order eviction to prevent unbounded category growth.

### Brain Tick Loop
Each tick: expire memory (every 20 ticks), vision scan (every 10 ticks), exploration tick, update player sightings (every 5 ticks), humanize (random pauses/sprint drops/look jitter), check inventory changes (every 10 ticks via hash, auto-organize hotbar on change), tick current action. Goal commitment enforced for 30 ticks before allowing goal switch (bypassed if health < 4). Goal scoring runs every `GOAL_EVAL_INTERVAL + skill.decisionDelay` ticks unless immediate re-evaluation is triggered (action complete, goal cancelled, significant health change >2). Empty action lists from `createActions()` are rejected (goal not committed). Personality modifiers and hysteresis threshold (0.1) apply during scoring. `requestReevaluation()` forces scoring on next tick. Goal eval counter is initialized from the player UUID hash to stagger evaluations across bots.

### Vision System (`BotVision`)
Raycast-based perception system. 32 rays cast every 10 ticks from the player's eye position in a cone (120 degree FOV, 24 block range). Only 768 block lookups per scan (32 rays x 24 steps) vs the old 35k+ omniscient block scan.

Ray distribution:
- 16 horizontal fan rays (FOV spread)
- 8 downward-angled rays (scanning ground/cave floor, 35 degree pitch)
- 4 upward-angled rays (scanning ceiling/trees, 40 degree pitch)
- 4 random peripheral rays (simulating peripheral vision)

Each ray steps forward in 1-block increments. Transparent blocks (air, water, flowers, torches) are passed through. Interesting blocks (ores, logs, crafting tables, furnaces, chests) are added to visible set. Solid/opaque blocks stop the ray. Bots can only see exposed blocks, not ore behind walls.

| Method | Description |
|---|---|
| `scan(instance)` | Cast rays, rebuild visible blocks (throttled to every 10 ticks) |
| `canSee(block)` | Nearest visible block of type |
| `canSeeAny(vararg blocks)` | Nearest visible block matching any type |
| `visibleOres()` | All visible ore blocks |
| `visibleEntities()` | Entities in line of sight (raycast verified) |
| `canSeePlayer(target)` | Whether a specific player is visible |
| `seesOpenSpace(direction)` | Whether there is open space (3+ air blocks) ahead in a direction |
| `visibleOfType(block)` | All visible blocks of a type |

### Exploration System (`BotExploration`)
Drives exploration decisions based on vision and memory. Replaces random walking with intelligent direction picking.

**ExplorationInterest** sealed interface — scored interests the bot can react to:
| Interest | Priority | Description |
|---|---|---|
| `VisibleOre` | 0.3-0.9 (diamond=0.9, iron=0.6, coal=0.3) | Ore seen by vision rays |
| `CaveOpening` | 0.5 | Air pocket below surface with adjacent stone |
| `Tree` | 0.4 | Visible log block |
| `Container` | 0.6 | Chest, trapped chest, barrel |
| `OpenArea` | 0.3 | Open space ahead |
| `UnexploredDirection` | 0.2 | Fallback — walk toward unexplored area |
| `EnemyPlayer` | aggression-scaled | Visible player, boosted if known threat |
| `DroppedItem` | 0.4 | Nearby item entity |

**Direction picking**: Prefers directions with fewest explored locations (memory-based), open space (vision-based), and cave following when underground (most air blocks ahead). Changes direction every 200-400 ticks or when hitting solid walls.

**Auto-remembering**: Vision automatically feeds memory — seen crafting tables, furnaces, ores, logs, and containers are remembered for later use by goals.

### Brain Helpers
- `hasItem(material)`, `hasItemMatching(predicate)`, `countItem(material)`, `findSlot(material)`
- `hasPickaxe()` - checks if any pickaxe is in inventory
- `consumeItem(material, count)`, `giveItem(item)`
- `findNearestBlock(block)` (vision-first, then memory fallback), `findNearestEntity(type, radius)`, `findNearestPlayer(radius)`, `findNearestItem(radius)`, `findEntityByUuid(uuid)`
- `vision` — `BotVision` instance for raycast-based perception
- `exploration` — `BotExploration` instance for exploration decisions
- `requestReevaluation()` - forces goal scoring on next tick

## GameTest Integration

### TestBotBehavior Delegation
`TestBotController.setBehavior()` delegates to `BotAI.attach/detach`. The `TestBehavior` enum maps to BotAI configurations:
- `IDLE` -> no goals (detach only)
- `WANDER` -> ExploreGoal with EXPLORER personality
- `AGGRESSIVE` -> AttackNearest + Survive + EquipArmor
- `DEFENSIVE` -> Flee + AttackNearest + Survive + EquipArmor (high caution)
- `PATROL` -> PatrolGoal with waypoints
- `FOLLOW` -> FollowGoal with target UUID
- `FLEE` -> FleeGoal with threshold=20 (always flee)
- `RANDOM_ACTIONS` -> Survive + Attack + GatherWood + Explore (random personality)
- `CHAOS` -> AttackNearest + Survive + Explore (BERSERKER personality)

### GameTestContext AI Methods
```kotlin
fun Player.attachAI(preset: String = "survival"): BotBrain
fun Player.attachAI(personality: BotPersonality, vararg goals: BotGoal): BotBrain
fun Player.detachAI()
fun Player.brain(): BotBrain?
fun Player.setPersonality(personality: BotPersonality)
fun attachAllAI(preset: String = "survival")
fun detachAllAI()
```

### LiveTestSession AI Methods
```kotlin
fun attachAI(player: Player, preset: String = "survival"): BotBrain
fun attachAllAI(preset: String = "survival")
fun detachAI(player: Player)
fun detachAllAI()
```

### Commands (`/orbit test live ai`)
```
/orbit test live ai <player> <preset>     - attach AI (survival/combat/pvp/gatherer/passive/miner)
/orbit test live ai <player> off          - detach AI
/orbit test live ai all <preset>          - attach AI to all bots
/orbit test live ai <player> info         - show current goal and personality
```

### FakePlayerManager Integration
`FakePlayerManager.setBehavior()` delegates WANDER, FOLLOW_NEAREST, LOOK_AROUND to `BotAI.attachPassiveAI()`, and SPRINT_RANDOM to `BotAI.attach()` with high curiosity personality. Only CIRCLE has its own tick logic (uses `BotMovement.moveToward()`). `remove()` calls `BotAI.detach()`.

### Installation
`BotAI.install()` called in `Orbit.kt` during startup (after `ModelEngine.install()`). `BotAI.uninstall()` called in shutdown hook.

## Pathfinding

```kotlin
val path: List<Pos>? = BotPathfinder.findPath(
    instance = instance,
    start = player.position,
    end = targetPos,
    maxIterations = 500,
    maxDistance = 64.0,
)
```

| Parameter | Default | Description |
|---|---|---|
| `instance` | required | World instance for block lookups |
| `start` | required | Starting position |
| `end` | required | Target position |
| `maxIterations` | 500 | A* iteration cap for performance |
| `maxDistance` | 64.0 | Maximum straight-line distance |

Returns `null` if no path found or distance exceeds limit.

### A* Features
- Block-level grid nodes with Manhattan distance heuristic
- Jump detection: climbs 1-block steps when space above allows
- Drop detection: falls up to 3 blocks safely
- Water awareness: water is walkable but slower (0.6x cost multiplier)
- Passable block set: air, flowers, torches, signs, rails, crops, redstone, etc.
- Diagonal movement with higher cost (14 vs 10 for cardinal)
- Long-packed position keys for O(1) visited lookups

## Path Following

```kotlin
val follower = BotPathFollower(player)

follower.navigateTo(instance, targetPos)

repeat(1) { follower.tick() }

follower.stop()

follower.repath(instance, newTarget)
```

### Properties

| Property | Type | Description |
|---|---|---|
| `currentPath` | `List<Pos>?` | Active waypoint list, null when idle |
| `currentIndex` | `Int` | Index of current target waypoint |
| `isNavigating` | `Boolean` | True if actively following a path or pathfinding in progress |
| `isComplete` | `Boolean` | True if reached end of path and no pathfinding pending |

### Movement Behavior
- Walk speed: 0.2158 blocks/tick (4.317 blocks/sec)
- Sprint speed: 0.2806 blocks/tick (5.612 blocks/sec) when target > 8 blocks away
- Jump velocity: 8.0 Minestom units when path goes up 1+ block
- Reactive jump: jumps when a solid block is detected ahead at feet level
- Water: 0.6x speed multiplier when in water
- View: yaw/pitch face direction of travel, pitch adjusts for elevation changes
- Stuck detection: if position unchanged for 20 ticks, automatically repaths (async)
- Pathfinding is async (virtual thread): `navigateTo` and `repath` return immediately, path applied on next `tick()` when ready

### Velocity-Based Movement
Movement uses `player.velocity` (not teleport), so Minestom handles smooth interpolation on the client side. Players see natural walking/sprinting/jumping.

## Performance
- **Goal evaluation**: Zero-allocation single-pass max finding loop (no filter/map/sort chains). Inventory state cached per tick (one scan of 36 slots, then O(1) lookups). Idle exponential backoff when no goal activates (up to 5 second intervals).
- **Vision system**: 32 rays x 24 steps = 768 block lookups per scan (every 10 ticks). Plain `ArrayList` for visible blocks (single-threaded scan+read). Packed Long position deduplication prevents re-checking blocks seen by multiple rays.
- **Exploration system**: Single-pass through `vision.allVisible()` replaces 13+ separate `visibleOfType()` calls. Cardinal directions are a companion object constant (zero Vec allocations per call). Cave following checks 8 directions x 8 steps = 64 block lookups.
- **Pathfinding**: Repath cooldown (40 ticks) prevents virtual thread spam. Failure counter (max 3) gives up on unreachable targets. Guard against concurrent pathfinding threads.
- **Memory**: LRU-capped `LinkedHashMap` for `knownLocations`/`resources` (max 32 categories, access-order eviction) + `ArrayList` per category (all writes from tick thread, no synchronization needed). Hard caps: 50 entries per category, 32 categories max, 30 player sightings, 20 threats. Prevents unbounded growth. Read-only accessors (`recallLocations`, `nearestResource`) use `filter` instead of mutating internal lists.
- **Entity queries**: Single-pass loops for `findNearestPlayer`, `findNearestEntity`, `findNearestItem` (zero intermediate list allocations).
- **String allocations**: `block.name()` / `resource.name()` cached in goal fields, not called repeatedly in hot paths.
- **findNearestBlock**: Vision-first (O(n) over visible blocks list, typically <100 entries), then memory fallback (O(n) over remembered locations). No cubic block scanning. No unused radius parameter.
- Pathfinder uses `PriorityQueue` + `HashMap<Long, Int>` with packed position keys
- Default 500 iteration cap prevents runaway searches
- A* pathfinding runs on virtual threads (non-blocking), path following remains on tick thread
- Path follower `tick()` is O(1) per call (no allocations in hot path beyond Vec)
- Goal scoring throttled to every `GOAL_EVAL_INTERVAL + skill.decisionDelay` ticks (immediate re-eval on action complete, cancel, or health change >2)
- Player sighting updates throttled to every 5 ticks (80% reduction)
- Memory cleanup throttled to every 20 ticks (1 second interval)
- **Hotbar management**: Dirty-flag detection via inventory hash (O(n) scan every 10 ticks, not every tick). Swap-based reorganization avoids creating intermediate ItemStacks. `markInventoryDirty()` bypasses the hash check for immediate reorganization.
- **Humanize**: O(1) per tick (3 random checks, no allocations)
- Safe for 50+ concurrent bots at 20Hz tick rate

## Lobby Filler (`BotLobbyFiller`)

Spawns and manages AI bots to fill game lobbies, making games feel populated.

### Quick Start
```kotlin
BotLobbyFiller.startFilling("game-1", gameMode, FillerConfig(
    targetPlayerCount = 12,
    minRealPlayers = 1,
    fillDelay = 10.seconds,
    staggerInterval = 3.seconds,
    skillRange = 0.3f..0.7f,
    preset = "survival",
    removeOnRealJoin = true,
))

BotLobbyFiller.stopFilling("game-1")
```

### FillerConfig

| Field | Default | Description |
|---|---|---|
| `targetPlayerCount` | required | Fill game to this many total players |
| `minRealPlayers` | 1 | Don't start filling until N real players join |
| `fillDelay` | 10s | Wait before starting to fill |
| `staggerInterval` | 3s | Time between each bot spawn |
| `skillRange` | 0.3-0.7 | Random skill rating for each bot (interpolated BEGINNER-EXPERT) |
| `preset` | "survival" | AI preset (survival/combat/pvp/miner/gatherer/passive) |
| `removeOnRealJoin` | true | Remove worst-skilled bot when real player joins full game |

### Behavior
1. Waits for `minRealPlayers` to join
2. Waits `fillDelay` after threshold met
3. Spawns bots one at a time (staggered) until `targetPlayerCount` reached
4. Each bot gets random skill from `skillRange` and random personality
5. When a real player joins and game is full, removes the lowest-skilled bot
6. When a real player leaves during WAITING/STARTING, spawns a replacement bot

### Lifecycle Integration
`startFilling()` installs a global event node that listens for `PlayerSpawnEvent` and `PlayerDisconnectEvent`, automatically calling `onRealPlayerJoin`/`onRealPlayerLeave` when non-bot players join or leave the game instance. GameMode does not need to call these manually. `stopFilling()` is called automatically from `GameMode.enterEnding()` using `Orbit.serverName` as the game ID, ensuring all filler bots are cleaned up when a game ends. Production logging (info/debug) tracks fill start, bot spawns, bot removals, and cleanup counts.

### API

| Method | Description |
|---|---|
| `startFilling(gameId, gameMode, config)` | Begin filling a game |
| `stopFilling(gameId)` | Stop filling and remove all filler bots |
| `onRealPlayerJoin(gameId, player)` | Notify filler of real player join (may remove a bot) |
| `onRealPlayerLeave(gameId, player)` | Notify filler of real player leave (may add a bot) |
| `getFillerBots(gameId)` | Get UUIDs of filler bots for a game |
| `isFillerBot(uuid)` | Check if a UUID is a filler bot |
| `getStatus(gameId)` | Get human-readable filler status string |
| `activeGameIds()` | Get all game IDs with active fillers |

### Commands (`/orbit fill`)
```
/orbit fill start <targetCount> [preset] [skillMin-skillMax]  - Start filling current game
/orbit fill stop                                               - Stop filling and remove bots
/orbit fill status                                             - Show filler state
```
