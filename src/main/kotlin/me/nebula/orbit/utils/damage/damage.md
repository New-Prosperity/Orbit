# Damage

Unified damage utilities: per-player damage history tracking, floating damage number indicators, and per-player per-source damage multipliers.

## DamageTracker

Records `DamageRecord` entries per player (max 50). Only tracks `Player` victims.

```kotlin
DamageTracker.record(victim, attacker, 5f, DamageType.PLAYER_ATTACK)

val history = player.damageHistory()
val lastAttacker = player.lastDamager()
val recent = player.recentDamage(withinMs = 3000L)
val total = DamageTracker.getTotalDamage(player, withinMs = 5000L)

DamageTracker.clear(player)
```

| Method | Description |
|---|---|
| `record(victim, attacker, amount, type)` | Record a damage event |
| `getHistory(player)` | Full history list |
| `getLastDamager(player)` | UUID of last player attacker |
| `getRecentDamage(player, withinMs)` | Records within time window (default 5s) |
| `getTotalDamage(player, withinMs)` | Sum of recent damage amounts |
| `clear(player)` | Clear player history |

### Player Extensions

| Extension | Returns |
|---|---|
| `player.damageHistory()` | `List<DamageRecord>` |
| `player.lastDamager()` | `UUID?` |
| `player.recentDamage(withinMs)` | `List<DamageRecord>` |

### DamageRecord

| Field | Type |
|---|---|
| `victimUuid` | `UUID` |
| `attackerUuid` | `UUID?` |
| `amount` | `Float` |
| `type` | `DamageType` |
| `timestamp` | `Long` |

## DamageIndicator

Spawns floating `TextDisplay` entities that rise and fade as damage numbers.

```kotlin
DamageIndicator.configure(
    format = { "<red>-%.1f HP".format(it) },
    lifetimeTicks = 30,
    riseSpeed = 0.08,
)

DamageIndicator.spawn(instance, position, 7.5f)
```

| Method | Description |
|---|---|
| `configure(format?, lifetimeTicks?, riseSpeed?)` | Set display format (MiniMessage), lifetime, rise speed |
| `spawn(instance, position, damage)` | Spawn a damage number at position with random XZ offset |

Defaults: format = `<red>%.1f`, lifetime = 20 ticks, rise speed = 0.05 blocks/tick.

## DamageSource

| Source | Mapped DamageTypes |
|---|---|
| `MELEE` | PLAYER_ATTACK, MOB_ATTACK |
| `PROJECTILE` | ARROW, TRIDENT |
| `FALL` | FALL |
| `FIRE` | ON_FIRE, IN_FIRE, LAVA |
| `EXPLOSION` | EXPLOSION, PLAYER_EXPLOSION |
| `MAGIC` | MAGIC, INDIRECT_MAGIC |
| `VOID` | OUT_OF_WORLD |
| `ALL` | Catch-all / global multiplier |

## DamageMultiplierManager

Per-player per-source damage multipliers applied via an EventNode on `EntityDamageEvent`. Source-specific and `ALL` multipliers stack multiplicatively.

```kotlin
DamageMultiplierManager.install()

player.setDamageMultiplier(DamageSource.MELEE, 1.5)
player.setDamageMultiplier(DamageSource.ALL, 0.8)

val mult = player.getDamageMultiplier(DamageSource.MELEE)

player.removeDamageMultiplier(DamageSource.MELEE)
player.clearDamageMultipliers()

DamageMultiplierManager.clearAll()
DamageMultiplierManager.uninstall()
```

| Method | Description |
|---|---|
| `install()` | Register damage event listener |
| `uninstall()` | Remove listener and clear all multipliers |
| `setMultiplier(player, source, multiplier)` | Set multiplier (must be >= 0) |
| `getMultiplier(player, source)` | Get multiplier (default 1.0) |
| `removeMultiplier(player, source)` | Remove a specific multiplier |
| `clearPlayer(player)` | Remove all multipliers for player |
| `clearAll()` | Remove all multipliers |

### Player Extensions

| Extension | Description |
|---|---|
| `player.setDamageMultiplier(source, multiplier)` | Set multiplier |
| `player.getDamageMultiplier(source)` | Get multiplier |
| `player.removeDamageMultiplier(source)` | Remove multiplier |
| `player.clearDamageMultipliers()` | Clear all for player |
