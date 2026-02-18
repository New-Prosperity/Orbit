# StatTracker

Per-player in-memory statistics tracking with derived stats, leaderboards, and a standalone `Leaderboard` class.

## Setup

```kotlin
statTracker {
    stat("kills")
    stat("deaths")
    stat("blocks_broken")
    derived("kdr", "kills", "deaths") { stats ->
        stats["kills"]!!.toDouble() / stats["deaths"]!!.coerceAtLeast(1)
    }
}
```

## Operations

```kotlin
StatTracker.increment(player, "kills")
StatTracker.increment(player, "kills", 5)
StatTracker.decrement(player, "deaths")
StatTracker.set(player, "blocks_broken", 100)
StatTracker.get(player, "kills")
StatTracker.getDouble(uuid, "kdr")
StatTracker.getAll(player)
StatTracker.reset(player, "kills")
StatTracker.resetAll(player)
StatTracker.resetStat("kills")
StatTracker.clear()
StatTracker.players()
```

## Top-N Queries

```kotlin
val topKillers = StatTracker.top("kills", limit = 10)
val topKdr = StatTracker.topDouble("kdr", limit = 5)
```

## Rendered Leaderboard

```kotlin
val lines: List<Component> = StatTracker.renderLeaderboard("kills", limit = 10) { uuid ->
    playerNameFromUuid(uuid)
}

player.sendLeaderboard("kills", limit = 10) { uuid -> playerNameFromUuid(uuid) }
```

Uses translation key `orbit.util.leaderboard.entry` with placeholders `rank`, `name`, `score`.

## Standalone Leaderboard

`Leaderboard` is a self-contained score tracker independent of `StatTracker`, useful for custom scoring systems.

### Create via DSL

```kotlin
val lb = leaderboard("top_builders") {
    displayName(Component.text("Top Builders"))
    maxEntries(15)
    ascending(false)
}
```

### Operations

```kotlin
lb.setScore(uuid, "Steve", 100.0)
lb.addScore(uuid, "Steve", 25.0)
lb.getScore(uuid)
lb.removePlayer(uuid)
lb.rank(uuid)
lb.size
lb.clear()
```

### Display

```kotlin
val top: List<LeaderboardEntry> = lb.top(10)
val rendered: List<Component> = lb.render(10)
lb.sendTo(player, 10)
```

### LeaderboardBuilder API

| Method | Default | Description |
|---|---|---|
| `displayName(Component)` | `Component.text(name)` | Title component |
| `maxEntries(Int)` | `10` | Max entries in top queries |
| `ascending(Boolean)` | `false` | Sort order (true = lowest first) |

### LeaderboardRegistry

```kotlin
LeaderboardRegistry["top_builders"]
LeaderboardRegistry.all()
LeaderboardRegistry.unregister("top_builders")
LeaderboardRegistry.clear()
```

`leaderboard()` DSL auto-registers into `LeaderboardRegistry`.

## Storage

All in-memory via `ConcurrentHashMap`. Cleared on restart.
