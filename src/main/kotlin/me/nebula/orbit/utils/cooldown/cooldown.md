# Cooldown

Thread-safe cooldown system with four variants: generic key-based, named player cooldowns with warning messages, material-based item cooldowns, and skill cooldowns with visual indicators. Includes a message rate-limiter.

## Cooldown\<K\>

Generic cooldown tracker keyed by any type.

```kotlin
val attackCooldown = Cooldown<UUID>(Duration.ofSeconds(3))

if (attackCooldown.tryUse(player.uuid)) {
    // action allowed
}

val left = attackCooldown.remaining(player.uuid)
attackCooldown.reset(player.uuid)
attackCooldown.cleanup()
```

| Method | Description |
|---|---|
| `isReady(key)` | True if cooldown expired or unused |
| `use(key)` | Start the cooldown |
| `tryUse(key)` | Atomic check + use, returns success |
| `remaining(key)` | Time left as `Duration` |
| `reset(key)` | Clear one entry |
| `resetAll()` | Clear all entries |
| `cleanup()` | Purge expired entries |

## NamedCooldown

Player + named key cooldowns. Supports an optional MiniMessage warning sent when on cooldown (`{remaining}` placeholder for seconds).

```kotlin
namedCooldown("fireball") {
    duration(Duration.ofSeconds(5))
    message("<red>Fireball on cooldown: {remaining}s")
}

if (player.useCooldown("fireball")) {
    // fire
}
```

| Method | Description |
|---|---|
| `NamedCooldown.register(config)` | Register a named cooldown config |
| `NamedCooldown.check(player, name)` | True if ready; sends warning if not |
| `NamedCooldown.use(player, name)` | Start cooldown |
| `NamedCooldown.tryUse(player, name)` | Check + use |
| `NamedCooldown.remaining(player, name)` | Remaining `Duration` |
| `NamedCooldown.reset(player, name)` | Reset one entry |
| `NamedCooldown.resetAll(player)` | Reset all entries for a player |
| `NamedCooldown.cleanup()` | Purge expired |

### Player Extensions

| Extension | Description |
|---|---|
| `player.isOnCooldown(name)` | True if on cooldown |
| `player.useCooldown(name)` | Try to use (returns success) |
| `player.cooldownRemaining(name)` | Remaining `Duration` |

## MaterialCooldown

Per-player per-material cooldown in ticks. Auto-cleanup via scheduled task.

```kotlin
MaterialCooldown.installCleanup()

player.setItemCooldown(Material.ENDER_PEARL, 40)

if (player.hasItemCooldown(Material.ENDER_PEARL)) {
    val ticksLeft = player.itemCooldownRemaining(Material.ENDER_PEARL)
}

player.clearItemCooldown(Material.ENDER_PEARL)
```

| Method | Description |
|---|---|
| `MaterialCooldown.installCleanup()` | Start periodic cleanup task |
| `MaterialCooldown.set(player, material, ticks)` | Set cooldown in ticks |
| `MaterialCooldown.has(player, material)` | Check if on cooldown |
| `MaterialCooldown.remaining(player, material)` | Remaining ticks |
| `MaterialCooldown.clear(player, material)` | Clear one entry |
| `MaterialCooldown.clearAll(player)` | Clear all for player |

### Player Extensions

| Extension | Description |
|---|---|
| `player.setItemCooldown(material, ticks)` | Set material cooldown |
| `player.hasItemCooldown(material)` | Check if on cooldown |
| `player.itemCooldownRemaining(material)` | Remaining ticks |
| `player.clearItemCooldown(material)` | Clear cooldown |

## SkillCooldown

Player + skill key with a visual indicator (BossBar, ActionBar, ItemCooldown, or None) and an optional `onReady` callback.

```kotlin
skillCooldown("dash") {
    duration(10.seconds)
    indicator(CooldownIndicator.BOSS_BAR)
    onReady { it.sendMessage(mm("<green>Dash ready!")) }
}

if (player.useSkill("dash")) {
    // skill fired, visual indicator starts
}

val ready = player.isSkillReady("dash")
val remaining = player.skillRemaining("dash")
```

| CooldownIndicator | Behavior |
|---|---|
| `BOSS_BAR` | Yellow boss bar with progress drain and countdown text |
| `ACTION_BAR` | Action bar with progress bar and countdown text |
| `ITEM_COOLDOWN` | No built-in display (for custom handling) |
| `NONE` | No display; still fires `onReady` callback |

| Method | Description |
|---|---|
| `SkillCooldown.register(config)` | Register a skill config |
| `SkillCooldown.unregister(name)` | Unregister and clear entries |
| `SkillCooldown.use(player, name)` | Use skill, returns false if on cooldown |
| `SkillCooldown.isReady(player, name)` | Check if ready |
| `SkillCooldown.remaining(player, name)` | Remaining `kotlin.time.Duration` |
| `SkillCooldown.reset(player, name)` | Reset and cancel display |
| `SkillCooldown.clearPlayer(player)` | Clear all skill cooldowns for player |
| `SkillCooldown.clearAll()` | Clear everything |

### Player Extensions

| Extension | Description |
|---|---|
| `player.useSkill(name)` | Use skill (returns success) |
| `player.isSkillReady(name)` | Check readiness |
| `player.skillRemaining(name)` | Remaining `kotlin.time.Duration` |

## MessageCooldownManager

Rate-limits player messages with optional MiniMessage warning.

```kotlin
val chatLimiter = messageCooldown {
    cooldownSeconds(3)
    warningMessage("<red>Slow down!")
}

if (chatLimiter.tryUse(player)) {
    // allow message
}
```

| Method | Description |
|---|---|
| `canSend(player)` | True if cooldown expired |
| `recordMessage(player)` | Record a send timestamp |
| `tryUse(player)` | Check + record + send warning if blocked |
| `getRemaining(player)` | Remaining `Duration` |
| `reset(player)` | Clear one player |
| `resetAll()` | Clear all |
| `cleanup()` | Purge expired |
