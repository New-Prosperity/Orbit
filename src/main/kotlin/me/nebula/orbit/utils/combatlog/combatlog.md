# Combat Log

Thread-safe combat tagging system that tracks which players are in combat and who attacked them.

## Key Classes

- **`CombatTracker`** -- singleton managing combat state for all players

## Player Extensions

- **`Player.isInCombat`** -- property checking active combat status
- **`Player.tagCombat(attacker?)`** -- tag the player as in combat
- **`Player.clearCombat()`** -- remove combat tag

## Usage

```kotlin
CombatTracker.setCombatDuration(10_000L)

player.tagCombat(attacker)

if (player.isInCombat) {
    val remaining = CombatTracker.remainingMs(player)
    val attacker = CombatTracker.lastAttacker(player)
}

player.clearCombat()

CombatTracker.clearExpired()
```

## API

| Method | Description |
|--------|-------------|
| `setCombatDuration(ms)` | Set global combat duration (default 15s) |
| `tag(player, attacker?)` | Tag player as in combat |
| `isInCombat(player)` | Check if combat tag is active |
| `remainingMs(player)` | Milliseconds left in combat |
| `lastAttacker(player)` | UUID of the last attacker |
| `clear(player)` | Remove combat tag |
| `clearExpired()` | Purge all expired entries |
