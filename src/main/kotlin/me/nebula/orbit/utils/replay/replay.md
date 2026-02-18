# Replay

Recording and playback system for in-game events (positions, block changes, chat, items).

## Overview

Records game events (player positions, block changes, chat messages, item holds) with tick offsets. Playback reconstructs events in real-time via callbacks. In-memory storage via ReplayManager.

## Key API

### Recording

- `ReplayRecorder` - Records events with millisecond-to-tick conversion (50ms per tick)
  - `start()` - Begin recording
  - `stop(): ReplayData` - Stop and return recorded data
  - `recordPosition(player: Player)` - Capture player position
  - `recordBlockChange(x, y, z, blockId)` - Log block change
  - `recordChat(player: Player, message: String)` - Log chat message
  - `isRecording: Boolean` - Check if recording

### Playback

- `ReplayPlayer(data: ReplayData)` - Replays recorded events
  - `play(onFrame: (ReplayFrame) -> Unit)` - Start playback with frame callback
  - `stop()` - Stop playback
  - `isPlaying: Boolean` - Check if playing
- `ReplayData` - Immutable replay data container
  - `durationTicks: Int` - Total replay length

### Storage

- `ReplayManager` - In-memory replay storage
  - `save(name: String, data: ReplayData)` - Store replay
  - `load(name: String): ReplayData?` - Retrieve replay
  - `delete(name: String)` - Remove replay
  - `list(): Set<String>` - List all replays

## Examples

```kotlin
val recorder = ReplayRecorder()
recorder.start()
recorder.recordPosition(player)
recorder.recordChat(player, "Hello!")
val data = recorder.stop()

ReplayManager.save("match_1", data)

val loaded = ReplayManager.load("match_1")
if (loaded != null) {
    val player = ReplayPlayer(loaded)
    player.play { frame ->
        when (frame) {
            is ReplayFrame.Position -> println("${frame.uuid} at ${frame.pos}")
            is ReplayFrame.Chat -> println("${frame.uuid}: ${frame.message}")
            else -> {}
        }
    }
}
```
