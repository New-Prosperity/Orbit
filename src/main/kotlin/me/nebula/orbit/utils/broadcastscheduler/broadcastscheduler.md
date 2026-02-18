# BroadcastScheduler

Rotating scheduled broadcast messages to all online players.

## Usage

```kotlin
val scheduler = broadcastScheduler {
    intervalSeconds(300)
    shuffled = true
    message("<gold>Vote for us at example.com!")
    message("<green>Join our Discord!")
}
scheduler.start()
scheduler.stop()
```

## Key API

- `broadcastScheduler { }` — DSL to build a `BroadcastScheduler`
- `intervalSeconds(seconds)` — set interval between messages
- `message(text)` — add a MiniMessage broadcast
- `message(component)` — add a Component broadcast
- `shuffled` — randomize message order
- `BroadcastScheduler.start()` — begin broadcasting on the scheduler
- `BroadcastScheduler.stop()` — cancel the broadcast task
- `BroadcastScheduler.isRunning` — whether the scheduler is active
