# Teleport

Managed teleportation with optional warmup, movement cancellation, and cross-instance support.

## Key Classes

- **`TeleportManager`** -- singleton managing instant and warmup teleports
- **`PendingTeleport`** -- data class for in-progress warmup teleports

## Player Extensions

- **`Player.safeTeleport(target, instance?)`** -- instant teleport (cross-instance if needed)
- **`Player.teleportWithWarmup(...)`** -- warmup teleport with movement cancellation

## Usage

### Instant Teleport

```kotlin
player.safeTeleport(Pos(0.0, 64.0, 0.0))

player.safeTeleport(Pos(0.0, 64.0, 0.0), instance = lobbyInstance)
```

### Warmup Teleport

```kotlin
player.teleportWithWarmup(
    target = Pos(100.0, 64.0, 200.0),
    warmupTicks = 60,
    targetInstance = arenaInstance,
    onTick = { remaining -> player.showActionBar("<yellow>Teleporting in ${remaining / 20}s") },
    onComplete = { player.sendMessage("<green>Teleported!") },
    onCancel = { player.sendMessage("<red>Teleport cancelled - you moved!") },
)
```

### Management

```kotlin
TeleportManager.cancel(player.uuid)
TeleportManager.isPending(player.uuid)
TeleportManager.cancelAll()
```

Warmup teleports cancel if the player moves more than 0.5 blocks (configurable via `moveThreshold`). Only one pending teleport per player; new requests cancel the previous.
