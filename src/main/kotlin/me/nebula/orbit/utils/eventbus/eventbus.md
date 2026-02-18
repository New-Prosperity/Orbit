# EventBus

Lightweight typed event bus with reified subscriptions and a global singleton.

## API

| Method | Description |
|---|---|
| `on<T> { handler }` | Subscribe to events of type `T`, returns `Subscription` |
| `emit(event)` | Dispatch an event to all matching handlers |
| `unsubscribe(type, handler)` | Remove a specific handler |
| `clear()` | Remove all handlers |
| `clear(type)` | Remove all handlers for a specific type |
| `listenerCount(type)` | Number of handlers for a type |

## Subscription

Call `subscription.cancel()` to unsubscribe.

## Global Instance

`globalEventBus` is a pre-created singleton `EventBus`.

## Example

```kotlin
data class PlayerKillEvent(val killer: UUID, val victim: UUID)
data class GameEndEvent(val winner: UUID)

val sub = globalEventBus.on<PlayerKillEvent> { event ->
    println("${event.killer} killed ${event.victim}")
}

globalEventBus.emit(PlayerKillEvent(killer.uuid, victim.uuid))

sub.cancel()

val bus = EventBus()
bus.on<GameEndEvent> { event -> announceWinner(event.winner) }
bus.emit(GameEndEvent(winner.uuid))
```
