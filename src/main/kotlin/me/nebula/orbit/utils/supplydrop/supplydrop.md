# Supply Drop

Falling airdrop system that spawns a descending armor stand entity with trail particles, lands with an explosion, and opens an interactive loot chest.

## Key Classes

- **`SupplyDrop`** — immutable config (origin, target, speed, loot, particles, sound, announce)
- **`ActiveSupplyDrop`** — manages the falling entity lifecycle, landing, chest spawning
- **`SupplyDropBuilder`** — DSL builder

## Usage

```kotlin
val drop = supplyDrop {
    origin(Pos(100.0, 200.0, 100.0))
    target(Pos(100.0, 64.0, 100.0))
    fallSpeed(0.5)
    lootTable(myLoot)
    trailParticle(Particle.FLAME)
    landingSound(SoundEvent.ENTITY_GENERIC_EXPLODE)
    announceRadius(100.0)
    announceKey("orbit.supplydrop.incoming")
    chestDuration(600)
    onLand { pos -> /* custom logic */ }
}

val active = drop.launch(instance)

active.isLanded   // check if landed
active.cancel()   // cancel mid-flight
```

## Builder Properties

| Property | Default | Description |
|---|---|---|
| `origin` | `(0, 200, 0)` | Spawn position (high altitude) |
| `target` | `(0, 64, 0)` | Landing position |
| `fallSpeed` | `0.5` | Blocks per tick descent rate |
| `lootTable` | **required** | `LootTable` for chest contents |
| `trailParticle` | `FLAME` | Particle spawned each tick during fall |
| `landingParticle` | `EXPLOSION` | Particle burst on landing |
| `landingSound` | `ENTITY_GENERIC_EXPLODE` | Sound on landing |
| `announceRadius` | `100.0` | Radius for announcement message |
| `announceKey` | `orbit.supplydrop.incoming` | Translation key for announcement |
| `chestDuration` | `600` | Ticks before chest auto-destroys (30s) |
| `onLand` | `null` | Callback when landing occurs |

## Lifecycle

1. `launch(instance)` spawns an armor stand with a chest helmet at `origin`
2. Announces to nearby players within `announceRadius`
3. Each tick: moves entity down by `fallSpeed`, spawns trail particle
4. On reaching `target`: removes falling entity, plays landing effects, spawns loot chest
5. Players interact with the chest armor stand to open the loot inventory
6. After `chestDuration` ticks, chest entity and event listener are cleaned up
