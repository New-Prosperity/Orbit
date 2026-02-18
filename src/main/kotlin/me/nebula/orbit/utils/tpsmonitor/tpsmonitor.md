# TPS Monitor

Server TPS (ticks per second) monitoring with a 1200-tick ring buffer (1 minute at 20 TPS).

## TPSMonitor

| Property | Description |
|---|---|
| `currentTPS` | TPS based on last tick delta (max 20.0) |
| `averageTPS` | 1-minute rolling average TPS (max 20.0) |
| `mspt` | Milliseconds per tick (last tick) |
| `averageMspt` | 1-minute rolling average MSPT |

| Method | Description |
|---|---|
| `install()` | Start monitoring via scheduler |
| `stop()` | Stop monitoring |
| `reset()` | Clear all recorded tick times |

## Example

```kotlin
TPSMonitor.install()

val tps = TPSMonitor.currentTPS
val avg = TPSMonitor.averageTPS
val mspt = TPSMonitor.mspt

if (tps < 18.0) {
    broadcastAllMM("<red>Server TPS: %.1f".format(tps))
}
```

## Notes

- Target TPS is 20.0 (one tick every 50ms)
- Ring buffer stores 1200 samples (1 minute at 20 TPS)
- Values are capped at 20.0 TPS maximum
- `install()` automatically stops any previous monitoring task
