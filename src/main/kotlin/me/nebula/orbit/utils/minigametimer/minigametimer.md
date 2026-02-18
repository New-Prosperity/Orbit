# MinigameTimer

Configurable game timer with display modes, milestone callbacks, and pause/resume support.

## DSL

```kotlin
val timer = minigameTimer("round-1") {
    duration(Duration.ofSeconds(120))
    display(DisplayMode.BOSS_BAR)
    displayFormat { ticks -> "<white>${ticks / 20}s remaining" }
    onTick { remaining -> }
    onHalf { println("Half time!") }
    onQuarter { println("Quarter time!") }
    milestone(Duration.ofSeconds(10)) { println("10 seconds left!") }
    onEnd { println("Time's up!") }
}
timer.addAllViewers(instance.players)
timer.start()
```

## Timer API

```kotlin
timer.start()
timer.pause()
timer.resume()
timer.stop()
timer.addTime(200)
timer.removeTime(100)
timer.addViewer(player)
timer.removeViewer(player)
timer.remaining        // ticks
timer.remainingDuration // Duration
timer.elapsed          // ticks
timer.isRunning
timer.isPaused
```

## Display Modes

- `BOSS_BAR` -- color changes green -> yellow -> red as time decreases.
- `ACTION_BAR` -- sends formatted text to action bar every tick.
- `TITLE` -- shows title for last 10 seconds, once per second.

## Milestones

- `onHalf {}` fires at 50% remaining.
- `onQuarter {}` fires at 25% remaining.
- `milestone(ticks/duration) {}` fires at a custom threshold.
- Each milestone fires exactly once per timer run.
