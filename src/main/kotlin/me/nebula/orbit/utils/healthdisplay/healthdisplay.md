# Health Display

Shows player health below their name tag by appending a formatted suffix to `displayName`. Updates periodically via scheduler.

## HealthDisplayManager

| Method | Description |
|---|---|
| `install(format, intervalTicks)` | Start periodic display name updates |
| `stop()` | Cancel the update task |
| `setFormat(format)` | Change the format function |
| `updatePlayer(player)` | Manually update a single player |

## DSL

```kotlin
healthDisplay {
    format { player -> "<red>${player.health.toInt()}\u2764" }
    interval(20)
}
```

## Custom Format

```kotlin
healthDisplay {
    format { player ->
        val hearts = (player.health / 2).toInt()
        "<green>$hearts <red>\u2764"
    }
    interval(10)
}
```

## Cleanup

```kotlin
HealthDisplayManager.stop()
```
