# GracePeriod

Timed invulnerability periods with optional cancel-on-move/attack and callbacks.

## Usage

```kotlin
gracePeriod("spawn-protection") {
    duration(Duration.ofSeconds(10))
    cancelOnMove()
    cancelOnAttack()
    onEnd { player -> player.sendMessage("Protection ended.") }
    onCancel { player -> player.sendMessage("Protection cancelled.") }
}

GracePeriodManager.apply(player, "spawn-protection")
player.isInGracePeriod
GracePeriodManager.remaining(player)
GracePeriodManager.cancel(player)
```

## Key API

- `gracePeriod(name) { }` — DSL to register a `GracePeriodConfig`
- `GracePeriodManager.apply(player, configName)` — start a grace period for a player
- `GracePeriodManager.isProtected(player)` — check if player is currently protected
- `GracePeriodManager.remaining(player)` — time remaining in the grace period
- `GracePeriodManager.cancel(player)` — manually cancel protection
- `GracePeriodManager.clearAll()` — remove all active grace periods
- `GracePeriodManager.uninstall()` — remove event listeners and cleanup
- `Player.isInGracePeriod` — extension property for `isProtected`
