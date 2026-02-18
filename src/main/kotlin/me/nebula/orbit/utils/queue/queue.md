# Queue

Two queue types: `GameQueue` (min/max players, countdown, auto-start) and `SimpleQueue` (lightweight FIFO with ready threshold).

## GameQueue

### Create

```kotlin
val queue = gameQueue("bedwars") {
    minPlayers(4)
    maxPlayers(16)
    countdownTicks(200)
    onStart { participants -> launchGame(participants) }
    onCountdownTick { remaining -> broadcastCountdown(remaining) }
    onJoin { uuid -> notifyJoin(uuid) }
    onLeave { uuid -> notifyLeave(uuid) }
}
QueueRegistry.register(queue)
```

### Operations

```kotlin
val result: JoinResult = queue.join(player)
queue.leave(player)
queue.forceStart()
queue.reset()
```

### Queries

```kotlin
queue.size
queue.isFull
queue.canStart
queue.state
queue.countdownRemaining
queue.contains(uuid)
queue.players
```

### GameQueueBuilder API

| Method | Default | Description |
|---|---|---|
| `minPlayers(Int)` | `2` | Minimum to start countdown |
| `maxPlayers(Int)` | `16` | Maximum capacity |
| `countdownTicks(Int)` | `200` | Ticks before auto-start |
| `onStart(List<UUID>)` | required | Called with participants on start |
| `onCountdownTick(Int)` | optional | Called each tick during countdown |
| `onJoin(UUID)` | optional | Called when a player joins |
| `onLeave(UUID)` | optional | Called when a player leaves |

### Enums

- `QueueState`: `WAITING`, `COUNTDOWN`, `STARTING`
- `JoinResult`: `SUCCESS`, `ALREADY_QUEUED`, `FULL`, `STARTING`

### QueueRegistry

```kotlin
QueueRegistry.register(queue)
QueueRegistry["bedwars"]
QueueRegistry.require("bedwars")
QueueRegistry.all()
QueueRegistry.unregister("bedwars")
QueueRegistry.clear()
```

### Player Extensions (GameQueue)

```kotlin
player.joinQueue("bedwars")
player.leaveQueue("bedwars")
player.queuePosition("bedwars")
```

Countdown starts automatically at `minPlayers`. Cancels if players drop below. `forceStart` bypasses the countdown.

---

## SimpleQueue

Lightweight FIFO queue with no countdown logic. Fires `onReady` when `requiredPlayers` is reached.

### Create

```kotlin
val sq = simpleQueue("duel_1v1") {
    maxSize(20)
    requiredPlayers(2)
    onJoin { player, position -> player.sendMessage("You are #$position") }
    onLeave { player -> player.sendMessage("Left queue") }
    onReady { players -> startDuel(players) }
}
```

### Operations

```kotlin
sq.join(player)
sq.leave(player)
sq.contains(player)
sq.position(player)
sq.players()
sq.size
sq.isEmpty
sq.clear()
```

### SimpleQueueBuilder API

| Method | Default | Description |
|---|---|---|
| `maxSize(Int)` | `Int.MAX_VALUE` | Maximum queue capacity |
| `requiredPlayers(Int)` | `1` | Players needed to trigger `onReady` |
| `onJoin(Player, Int)` | no-op | Called with player and queue position |
| `onLeave(Player)` | no-op | Called when a player leaves |
| `onReady(List<Player>)` | no-op | Called when `requiredPlayers` are queued |

When `requiredPlayers` is reached, the first N players are removed from the queue and passed to `onReady`.
