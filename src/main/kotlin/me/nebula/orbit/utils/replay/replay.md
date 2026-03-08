# Replay

Game recording and playback system with NPC-ready frame types, speed control, perspective switching, and MinIO persistent storage.

## Frame Types

| Frame | Data | Description |
|---|---|---|
| `Position` | uuid, pos, sneaking | Player position + look + sneak state |
| `BlockChange` | x, y, z, blockId | Block modification |
| `Chat` | uuid, message | Chat message |
| `ItemHeld` | uuid, slot, item | Held item change |
| `EntitySpawn` | uuid, name, skin | Player join with skin data |
| `EntityDespawn` | uuid | Player leave |
| `Death` | uuid, killerUuid? | Player death |

## Recording

```kotlin
val recorder = ReplayRecorder()
recorder.start()
recorder.recordPlayerJoin(player)
recorder.recordPosition(player)
recorder.recordDeath(victim, killer)
val data = recorder.stop()
```

## Playback

```kotlin
val player = ReplayPlayer(data)
player.setSpeed(2.0)
player.setPerspective(someUuid)
player.onComplete { println("Done") }
player.play { frame ->
    when (frame) {
        is ReplayFrame.Position -> npc.teleport(frame.pos)
        is ReplayFrame.EntitySpawn -> spawnNpc(frame)
        is ReplayFrame.Death -> playDeathEffect(frame)
        ...
    }
}
```

### Controls

| Method | Description |
|---|---|
| `play(onFrame)` | Start playback with frame callback |
| `stop()` | Stop playback |
| `pause()` / `resume()` | Pause/resume |
| `setSpeed(double)` | Change speed (0.25x, 0.5x, 1x, 2x, 4x) |
| `seekTo(tick)` | Jump to specific tick |
| `setPerspective(uuid?)` | Follow specific player |
| `availablePerspectives()` | All recorded player UUIDs |
| `progressPercent` | 0.0-1.0 playback progress |

## Storage

### In-Memory

```kotlin
ReplayManager.save("game-123", data)
val loaded = ReplayManager.load("game-123")
```

### MinIO (Persistent)

```kotlin
ReplayStorage.configure(minioClient)
ReplayStorage.upload("game-123", data, ReplayMetadata(gameMode = "battleroyale", ...))
val (metadata, data) = ReplayStorage.download("game-123")!!
```

Replays are GZIP-compressed JSON stored at `replays/{name}.replay.gz`.

### Size Estimation

Per frame: ~80-120 bytes JSON (Position frame with UUID, coords, yaw/pitch, sneak).
At 20 ticks/s recording rate with 24 players over 15 minutes:
- Frames: 24 players × 18,000 ticks = 432,000 position frames
- Raw JSON: ~432K × 100 bytes = ~43 MB
- GZIP compressed: ~3-5 MB (position data compresses well)
- With block changes, deaths, spawns: +10-20% → ~4-6 MB per game

At 10 ticks/s (recommended): ~2-3 MB per game.
