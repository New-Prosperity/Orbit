# Vanilla Module System

Togglable vanilla Minecraft mechanics. Each module is independent, per-instance, with typed configuration.

## Usage

### Enable/disable per instance
```kotlin
VanillaModules.enable(instance, "fall-damage")
VanillaModules.enable(instance, "hunger", ModuleConfig.of("depletionRate" to 0.5))
VanillaModules.disable(instance, "fall-damage")
VanillaModules.disableAll(instance)
```

### In-game command
```
/gamerule list                          — list all modules with ON/OFF status
/gamerule enable <module>               — enable module with defaults
/gamerule disable <module>              — disable module
/gamerule info <module>                 — show config params and current values
/gamerule set <module> <key> <value>    — set config param (auto-enables if off)
```

## Available Modules

| Module | Description | Config | State |
|--------|-------------|--------|-------|
| `block-pick` | Creative middle-click block picking | none | none |
| `fall-damage` | Damage from falling > 3 blocks | `multiplier`, `minimumDistance` | `Tag.Double("vanilla:fall_start_y")`, `Tag.Boolean("vanilla:was_on_ground")` |
| `void-damage` | Damage below Y threshold | `threshold`, `damage`, `tickRate` | none |
| `gravity-blocks` | Sand/gravel/anvil falling | none | none |
| `hunger` | Food depletion + starvation | `depletionRate`, `starvation`, `starvationDamage`, `starvationTickRate` | `Tag.Double("vanilla:exhaustion")`, `Tag.Double("vanilla:hunger_last_y")` |
| `natural-regen` | Health regen when food >= 18 | `minFood`, `fastRegenTicks`, `slowRegenTicks` | none |
| `fire-damage` | Fire/lava contact damage | `fireDamage`, `lavaDamage`, `fireTickRate`, `lavaTickRate`, `burnTickRate` | `Tag.Long("vanilla:fire_last_pos")`, `Tag.String("vanilla:fire_cached_block")` |
| `drowning` | Air supply depletion underwater | `maxAirTicks`, `damage`, `damageRate` | `Tag.Integer("vanilla:air_supply")`, `Tag.Long("vanilla:drowning_head_pos")`, `Tag.Boolean("vanilla:cached_underwater")` |
| `suffocation` | Damage when head inside solid block | `damage`, `tickRate` | `Tag.Long("vanilla:suffocation_head_pos")`, `Tag.Boolean("vanilla:cached_solid")` |
| `swimming` | Water/lava drag and gravity | `waterGravity`, `waterDrag`, `lavaGravity`, `lavaDrag` | `Tag.Boolean("vanilla:in_fluid")`, `HashMap<UUID, Aerodynamics>` (complex object) |
| `shield-blocking` | Shield blocks melee, axes disable shields | `disableTicks` | `Tag.Long("vanilla:shield_cooldown")` |
| `attack-cooldown` | 1.9+ attack cooldown scaling | none | `Tag.Long("vanilla:last_attack_tick")` |
| `slime-block` | Bounce on slime blocks | none | `Tag.Double("vanilla:slime_velocity_y")` |
| `lever-button` | Levers toggle powered, buttons activate temporarily | none | none |
| `note-block` | Note blocks play pitched sounds based on block below | none | none |
| `critical-hits` | 1.5x damage when falling and not on ground | `multiplier` | none |
| `sweep-attack` | Sweeping attack hits nearby entities | `sweepDamage`, `sweepRange` | none |

## Per-Player State

All per-player state uses Minestom entity `Tag<T>` constants declared as `private val` at file level. Tags auto-clean when the entity is removed, so no `PlayerDisconnectEvent` cleanup is needed. The only exception is `SwimmingModule.defaultAero` which stores `Aerodynamics` (a complex object without a Tag type) in a `HashMap<UUID, Aerodynamics>` with manual disconnect cleanup.

## Creating a Module

```kotlin
object MyModule : VanillaModule {
    override val id = "my-module"
    override val description = "Does something"
    override val configParams = listOf(
        ConfigParam.DoubleParam("speed", "Movement speed", 1.0, 0.1, 5.0),
        ConfigParam.BoolParam("enabled", "Feature toggle", true),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val speed = config.getDouble("speed", 1.0)
        val node = EventNode.all("vanilla-my-module")
        // add listeners
        return node
    }
}
```

Register in `Orbit.registerVanillaModules()`.

## BlockHandler Pattern (Container Modules)

Container modules (ChestModule, ShulkerBoxModule, FurnaceModule, BrewingStandModule, CampfireCookingModule) use Minestom's `BlockHandler` interface for block lifecycle instead of global event listeners.

### How it works
- A private `BlockHandler` implementation handles `onInteract` (open inventory / place food), `onDestroy` (cleanup + item drops), and optionally `tick` (smelting / brewing / cooking progress).
- Handlers are registered in `MinecraftServer.getBlockManager()` by block entity namespace (e.g., `minecraft:chest`, `minecraft:furnace`). This enables automatic handler restoration for blocks loaded from Anvil worlds.
- A `PlayerBlockPlaceEvent` listener in the EventNode attaches the handler to player-placed blocks via `Block.withHandler()`.
- Live `Inventory` / state objects are still stored in `ConcurrentHashMap` keyed by packed block position. The map is needed for mutable live objects that can't be serialized into block tags.
- Each handler checks `VanillaModules.isEnabled(instance, moduleId)` before acting, preserving per-instance enable/disable semantics.

### Benefits over global event listeners
- `onInteract` only fires for blocks with the handler attached (no block-type filtering needed).
- `onDestroy` fires automatically when the block is replaced or broken (no `PlayerBlockBreakEvent` listener needed).
- `tick` is called per-block by the chunk tick system (no global iteration over all entries).
- `onInteract` returning `false` correctly blocks item use (prevents placing blocks when opening containers).

## Shared Utilities

### `InventoryDrops.kt` — `dropInventoryContents(instance, inventory, x, y, z)`

Drops all non-air items from an `Inventory` as `ItemEntity` instances at the block center (`x+0.5, y+0.5, z+0.5`) with randomized velocity and 500ms pickup delay. Used by ChestModule, FurnaceModule, BrewingStandModule, and ShulkerBoxModule in their `onDestroy` handlers.

### `PlayerExtensions.kt` — `Player.isCreativeOrSpectator: Boolean`

`true` when `gameMode` is `CREATIVE` or `SPECTATOR`. Used across damage/hunger/regen modules to skip survival-only logic.

## Config Types

- `ConfigParam.BoolParam` — true/false
- `ConfigParam.IntParam` — integer with min/max
- `ConfigParam.DoubleParam` — decimal with min/max

All validated on input via `/gamerule set`.
