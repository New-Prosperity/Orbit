# Respawn

Per-player and global respawn point management with instance resolution.

## Usage

```kotlin
RespawnManager.setDefault(Pos(0.0, 64.0, 0.0), instance)

player.setRespawnPoint()
player.setRespawnPoint(Pos(100.0, 70.0, 200.0))

val respawn = player.getCustomRespawnPoint()
player.clearRespawnPoint()
```

## Key API

- `RespawnManager.setDefault(position, instance)` — set the global fallback respawn point
- `Player.setRespawnPoint(position)` — set personal respawn (defaults to current position)
- `Player.clearRespawnPoint()` — remove personal respawn
- `Player.getCustomRespawnPoint()` — get personal respawn or fall back to default
- `RespawnManager.resolveInstance(respawnPoint)` — resolve the `Instance` from a `RespawnPoint`
