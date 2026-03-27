# Countdown

DSL for creating ticking countdowns with configurable interval, milestones, pause/resume, and completion callback.

## Key Classes

- **`Countdown`** — manages a scheduled repeating task that counts down
- **`MilestoneAction`** — data class holding a handler and optional SoundEvent, fired when remaining time crosses a threshold
- **`CountdownBuilder`** — DSL builder

## Usage

```kotlin
val timer = countdown(30.seconds) {
    interval(1.seconds)
    onTick { remaining -> broadcast("${remaining.inWholeSeconds}s left") }
    onComplete { broadcast("Time's up!") }
    milestone(10.seconds) { broadcast("10 seconds!") }
    milestone(5.seconds, SoundEvent.BLOCK_NOTE_BLOCK_PLING) { broadcast("5 seconds!") }
}

timer.start()
timer.remaining
timer.isRunning
timer.isPaused
timer.pause()
timer.resume()
timer.cancel()
timer.stop()
timer.restart()
```

## Details

- Uses `MinecraftServer.getSchedulerManager()` internally
- `onTick` is called immediately on `start()` with the full duration
- `remaining` property returns `Duration.ZERO` when not running (and not paused)
- Calling `start()` on a running countdown throws `IllegalStateException`
- **Milestones** fire once when remaining time crosses the threshold (keyed by millis, deduplicated via `firedMilestones` set)
- **Pause/Resume** — `pause()` saves remaining time and stops the task; `resume()` restarts from the saved remaining time
- **Cancel** — alias for stop that does NOT fire `onComplete`
- `restart()` resets everything and calls `start()` fresh
