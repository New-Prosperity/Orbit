# AFK Detector

Automatic AFK detection based on player movement and chat activity. Registers a scoped event node and periodic check task.

## Key Classes

- **`AfkDetector`** -- singleton managing AFK state globally

## Player Extensions

- **`Player.isAfk`** -- property checking AFK status

## Usage

```kotlin
AfkDetector.start(
    thresholdMs = 300_000L,
    onAfk = { player -> player.sendMessage("<gray>You are now AFK") },
    onReturn = { player -> player.sendMessage("<gray>Welcome back") },
)

if (player.isAfk) {
    // handle AFK player
}

AfkDetector.markActive(player)

AfkDetector.stop()
```

## Behavior

- Listens to `PlayerMoveEvent` and `PlayerChatEvent` to track activity
- Checks every 10 seconds for players exceeding the threshold
- Automatically cleans up disconnected players
- `onAfk` fires once when a player becomes AFK
- `onReturn` fires once when an AFK player becomes active again
