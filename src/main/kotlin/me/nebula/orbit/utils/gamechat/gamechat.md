# GameChatPipeline

Processor-based chat pipeline for game modes. Messages flow through an ordered chain of `ChatProcessor`s that can modify formatting, filter recipients, or cancel messages entirely. The same pipeline system is used everywhere — only the processors differ per mode.

## API

### ChatProcessor

Single-responsibility processor. Receives a mutable `ChatContext` and modifies it freely.

```kotlin
fun interface ChatProcessor {
    fun process(context: ChatContext)
}
```

### ChatContext

Mutable context passed through the pipeline:

| Field | Type | Description |
|-------|------|-------------|
| `sender` | `Player` | Message sender (read-only) |
| `rawMessage` | `String` | Original message text (read-only) |
| `recipients` | `MutableSet<Player>` | Recipients — remove to filter |
| `cancelled` | `Boolean` | Set `true` to drop the message |
| `prefixes` | `MutableList<String>` | MiniMessage prefix parts prepended to name |
| `nameColor` | `String` | MiniMessage color tag for the name |
| `messageColor` | `String` | MiniMessage color tag for the message body |
| `separator` | `String` | Between name and message (default `<gray>: `) |

### Pipeline DSL

```kotlin
val pipeline = gameChatPipeline {
    processor("mute_check", MuteCheckProcessor { uuid -> SanctionStore.isMuted(uuid) })
    processor("rank_prefix", RankPrefixProcessor())
    processor("team_prefix", TeamPrefixProcessor(tracker))
    processor("dead_dim", DeadPlayerDimProcessor(tracker))
    processor("spectator_isolation", SpectatorIsolationProcessor(tracker))
    processor("cooldown", CooldownProcessor(3000L))
}
pipeline.install()
pipeline.uninstall()
```

### Built-in Processors

| Processor | Purpose |
|-----------|---------|
| `RankPrefixProcessor` | Adds rank prefix, sets name color from `RankManager` |
| `TeamPrefixProcessor(tracker)` | Adds `[TeamName]` prefix for team modes |
| `DeadPlayerDimProcessor(tracker)` | Dims dead player messages (dark gray + skull) |
| `SpectatorIsolationProcessor(tracker)` | Spectators see only spectator chat, alive see only alive |
| `MuteCheckProcessor(predicate)` | Cancels message if sender is muted |
| `CooldownProcessor(millis)` | Enforces per-player chat cooldown |
| `RadiusChatProcessor(radius)` | Proximity chat — filters recipients by distance |

### Custom Processors

```kotlin
processor("custom") { context ->
    if (someCondition(context.sender)) {
        context.prefixes.add("<gold>[VIP] ")
    }
}
```

### Integration

`GameMode` calls `buildChatPipeline()` during `enterPlaying()`. Override in your mode:

```kotlin
override fun buildChatPipeline() = gameChatPipeline {
    processor("rank_prefix", RankPrefixProcessor())
    processor("dead_dim", DeadPlayerDimProcessor(tracker))
    processor("spectator_isolation", SpectatorIsolationProcessor(tracker))
}
```

When a pipeline is active, the built-in `isolateSpectatorChat` in `GameSettings.timing` is skipped.
