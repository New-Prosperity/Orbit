# AutoRestart

Scheduled server restart with configurable warning broadcasts and player kicks.

## DSL

```kotlin
autoRestart {
    after(Duration.ofHours(6))
    warnings(
        Duration.ofMinutes(30),
        Duration.ofMinutes(10),
        Duration.ofMinutes(5),
        Duration.ofMinutes(1),
        Duration.ofSeconds(30),
        Duration.ofSeconds(10),
        Duration.ofSeconds(5),
    )
    warningMessage("<red>Server restarting in {time}!")
    kickMessage("<red>Server is restarting.")
    onRestart { MinecraftServer.stopCleanly() }
}
```

## Manager API

```kotlin
AutoRestartManager.scheduleRestart(Duration.ofMinutes(5))
AutoRestartManager.cancel()
AutoRestartManager.getTimeRemaining()
AutoRestartManager.isScheduled
```

## Behavior

- Broadcasts MiniMessage-formatted warnings at each configured interval before restart.
- `{time}` placeholder in warning template resolves to human-readable duration (e.g., `5m 30s`).
- Kicks all online players with configurable kick message before executing restart action.
- Only one restart schedule active at a time; calling `install()` or `scheduleRestart()` cancels any previous schedule.
