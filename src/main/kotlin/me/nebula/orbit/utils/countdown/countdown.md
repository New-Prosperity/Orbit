# Countdown

DSL for creating ticking countdowns with configurable interval and completion callback.

## Key Classes

- **`Countdown`** — manages a scheduled repeating task that counts down
- **`CountdownBuilder`** — DSL builder

## Usage

```kotlin
val timer = countdown(Duration.ofSeconds(30)) {
    interval(Duration.ofSeconds(1))
    onTick { remaining -> broadcast("${remaining.seconds}s left") }
    onComplete { broadcast("Time's up!") }
}

timer.start()
timer.remaining
timer.isRunning
timer.stop()
timer.restart()
```

## Details

- Uses `MinecraftServer.getSchedulerManager()` internally
- `onTick` is called immediately on `start()` with the full duration
- `remaining` property returns `Duration.ZERO` when not running
- Calling `start()` on a running countdown throws `IllegalStateException`
