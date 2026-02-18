# Scheduler

Convenience functions and a DSL for Minestom's scheduler. Wraps `SchedulerManager` with concise top-level functions.

## Top-Level Functions

### Delay

```kotlin
delay(40) { player.sendMessage("2 seconds later") }

delay(Duration.ofSeconds(5)) { cleanup() }
```

### Repeat

```kotlin
repeat(20) { updateScoreboard() }

repeat(Duration.ofSeconds(1)) { tick() }
```

### Delayed Repeat

```kotlin
delayedRepeat(delayTicks = 100, intervalTicks = 20) { broadcastTip() }
```

### Run Async

```kotlin
runAsync { heavyComputation() }
```

## Repeating Task DSL

```kotlin
val task = repeatingTask {
    interval(20)
    delay(40)
    times(10)
    execute { doWork() }
}

task.cancel()
```

### DSL Methods

| Method | Description |
|--------|-------------|
| `interval(ticks)` | Tick interval between executions |
| `interval(duration)` | Duration interval (converted to ticks) |
| `delay(ticks)` | Initial delay before first execution |
| `delay(duration)` | Initial delay as Duration |
| `times(count)` | Limit total executions (auto-cancels after) |
| `execute(action)` | The action to run each interval |

All functions return a `Task` that can be cancelled. Omitting `times` creates an infinite repeating task.
