# Replay

Game recording, playback, highlight detection, and commands. Two recording modes: semantic frames (NPC-based) and raw packet capture (pixel-perfect). Both support MinIO persistent storage via `.nebr` binary format or legacy GZIP JSON.

## Binary Format (.nebr)

Custom binary format with Zstd-compressed tick chunks for O(1) seeking. Header includes match metadata, player table, and embedded or referenced world snapshot.

```
HEADER: magic(NeRr) + version + protocol + matchId + gamemode + map + timestamp + duration + players
WORLD:  REFERENCE(mapName) or EMBEDDED(Zstd NebulaWorld)
FRAMES: Zstd-compressed tick chunks (~1000 ticks each)
```

### ItemHeld Frame
Binary: writes `slot` (VarInt) + `material` (String, e.g. `minecraft:diamond_sword`). On read, resolves `Material.fromKey()` to reconstruct `ItemStack.of(material)`.

## Packet Recording (production)

`PacketReplayRecorder` captures all outgoing `ServerPacket.Play` via `PlayerPacketOutEvent`. Pixel-perfect recording.

```kotlin
val recorder = PacketReplayRecorder()
recorder.start(gameInstance)
val replayFile = recorder.buildReplayFile(gameInstance, matchId, gameMode, mapName)
ReplayStorage.saveBinary(matchId, replayFile)
```

Integrated into `GameMode.enterPlaying()` -> `enterEnding()` automatically.

## Semantic Recording

`ReplayRecorder` captures frame-based events for NPC playback and highlight detection. Wired into `GameMode` alongside the packet recorder.

| Frame | Data |
|---|---|
| `Position` | uuid, pos, sneaking |
| `BlockChange` | x, y, z, blockId |
| `Chat` | uuid, message |
| `ItemHeld` | uuid, slot, item (material) |
| `EntitySpawn` | uuid, name, skin |
| `EntityDespawn` | uuid |
| `Death` | uuid, killerUuid? |

### GameMode Integration
- `enterPlaying()`: starts both `PacketReplayRecorder` and `ReplayRecorder`, records initial player joins, starts position recording task (every 4 ticks for alive players)
- `handleDeath()`: calls `semanticRecorder.recordDeath(victim, killer)`
- Game mechanics node: records `PlayerChatEvent` via `semanticRecorder.recordChat()`
- `enterEnding()`: stops both recorders, saves packet replay as `.nebr`, saves semantic replay as JSON, runs highlight detection, publishes `ReplayHighlightMessage` for MULTI_KILL/CLUTCH highlights

## Highlight Detection (`ReplayHighlights.kt`)

Analyzes semantic replay frames to detect notable moments.

| Type | Condition |
|---|---|
| `FIRST_BLOOD` | First death frame with a killer |
| `FINAL_KILL` | Last death frame with a killer |
| `MULTI_KILL` | 3+ kills by same player within 200 ticks (10s) |
| `LONG_RANGE_KILL` | Kill from 20+ blocks (position frame distance) |
| `CLUTCH` | Player wins as last alive against 2+ opponents |

`ReplayHighlights.detect(frames, metadata)` returns `List<ReplayHighlight>` sorted by tick.

## Playback (`ReplayViewer`)

```kotlin
val replayFile = ReplayStorage.loadBinary(matchId)!!
val viewer = ReplayViewer(replayFile)
viewer.load().thenAccept {
    viewer.addViewer(spectator)
    viewer.play()
}
viewer.setSpeed(2.0)
viewer.pause()
viewer.seekTo(tick)
viewer.setPerspective(playerUuid)
viewer.highlights()
```

### Entity Rendering
- Fake player entities with `Entity(EntityType.PLAYER)` + `PlayerInfoUpdatePacket` for skin data
- Custom names visible above entities
- Death animation: `DestroyEntitiesPacket` sent to viewers, then full despawn after 20 ticks
- Sneaking metadata from position frames
- POV mode: teleports viewers to the tracked player's position each frame

## `/replay` Command (`commands/ReplayCommand.kt`)

| Subcommand | Permission | Action |
|---|---|---|
| `list [page]` | `orbit.replay.admin` | Paginated list of saved replays (10/page), clickable play/info |
| `play <name>` | `orbit.replay` | Load and play a replay |
| `stop` | `orbit.replay` | Stop current playback, destroy viewer |
| `pause` | `orbit.replay` | Pause playback |
| `resume` | `orbit.replay` | Resume playback |
| `speed <0.5\|1\|2\|4>` | `orbit.replay` | Set playback speed |
| `seek <tick\|percent%>` | `orbit.replay` | Jump to tick or percentage |
| `pov <playerName>` | `orbit.replay` | Switch to player's perspective (free cam if no arg) |
| `highlights` | `orbit.replay` | Show detected highlights with click-to-seek |
| `info <name>` | `orbit.replay.admin` | Show replay metadata |

Viewer cleanup on disconnect via `cleanupReplayViewer()` called from `PlayerDisconnectEvent` in `Orbit.kt`.

## Storage

### Binary (.nebr)
```kotlin
ReplayStorage.saveBinary("match-123", replayFile)
val loaded = ReplayStorage.loadBinary("match-123")
```

### Legacy JSON
```kotlin
ReplayStorage.save("match-123", data, metadata)
val (metadata, data) = ReplayStorage.load("match-123")
```

Both stored in MinIO under the `replays/` scope.

## Replay Retention (Pulsar)

`ReplayCleanupRoutine` runs every 1 hour in Pulsar:
- Default replays: deleted after 7 days
- Semantic (highlighted) replays: deleted after 30 days
- Reads `STORAGE_URL` / `STORAGE_TOKEN` env vars for MinIO access

## Beacon Integration

On replay save, if MULTI_KILL or CLUTCH highlights are found, publishes `ReplayHighlightMessage` (Gravity NetworkMessage) via `NetworkMessenger` so Beacon can post to a highlights Discord channel.
