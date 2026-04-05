# Replay

Game recording and playback with two modes: semantic frames (NPC-based) and raw packet capture (pixel-perfect). Both support MinIO persistent storage via `.nebr` binary format or legacy GZIP JSON.

## Binary Format (.nebr)

Custom binary format with Zstd-compressed tick chunks for O(1) seeking. Header includes match metadata, player table, and embedded or referenced world snapshot.

```
HEADER: magic(NeRr) + version + protocol + matchId + gamemode + map + timestamp + duration + players
WORLD:  REFERENCE(mapName) or EMBEDDED(Zstd NebulaWorld)
FRAMES: Zstd-compressed tick chunks (~1000 ticks each)
```

## Packet Recording (production)

`PacketReplayRecorder` captures all outgoing `ServerPacket.Play` via `PlayerPacketOutEvent`. Pixel-perfect recording — replays exactly what clients saw.

```kotlin
val recorder = PacketReplayRecorder()
recorder.start(gameInstance)
// ... match plays, packets recorded automatically ...
val replayFile = recorder.buildReplayFile(gameInstance, matchId, gameMode, mapName)
ReplayStorage.saveBinary(matchId, replayFile)
```

Integrated into `GameMode.enterPlaying()` → `enterEnding()` automatically.

## Semantic Recording (legacy)

Frame-based recording for NPC playback.

| Frame | Data |
|---|---|
| `Position` | uuid, pos, sneaking |
| `BlockChange` | x, y, z, blockId |
| `Chat` | uuid, message |
| `ItemHeld` | uuid, slot, item |
| `EntitySpawn` | uuid, name, skin |
| `EntityDespawn` | uuid |
| `Death` | uuid, killerUuid? |

## Playback

```kotlin
val replayFile = ReplayStorage.loadBinary(matchId)!!
val viewer = ReplayViewer(replayFile)
viewer.load().thenAccept { instance ->
    viewer.addViewer(spectator)
    viewer.play()
}
viewer.setSpeed(2.0)
viewer.pause()
viewer.seekTo(tick)
```

## Storage

### Binary (.nebr) — recommended
```kotlin
ReplayStorage.saveBinary("match-123", replayFile)
val loaded = ReplayStorage.loadBinary("match-123")
```

### Legacy JSON — backward compat
```kotlin
ReplayStorage.save("match-123", data, metadata)
val (metadata, data) = ReplayStorage.load("match-123")
```

Both stored in MinIO under the `replays/` scope.
