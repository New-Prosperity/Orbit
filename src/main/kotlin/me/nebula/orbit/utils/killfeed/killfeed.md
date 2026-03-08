# KillFeed

Configurable kill feed system with multi-kill tracking, kill streaks, first blood, custom renderers, and effects. Designed as a composable utility — game modes configure it via DSL.

## API

### DSL

```kotlin
val feed = killFeed {
    tracker(playerTracker)
    renderer { event, viewer ->
        viewer.translate("orbit.killfeed.default",
            "killer" to (event.killer?.username ?: "?"),
            "victim" to event.victim.username,
        )
    }
    effect { event, viewers ->
        event.killer?.playSound(Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.PLAYER, 1f, 1f))
    }
    firstBlood("orbit.killfeed.first_blood")
    multiKillWindow(5000L)
    multiKill(2, "orbit.killfeed.double_kill")
    multiKill(3, "orbit.killfeed.triple_kill")
    multiKill(4, "orbit.killfeed.quad_kill")
    multiKill(5, "orbit.killfeed.penta_kill")
    streak(3, "orbit.killfeed.streak_3")
    streak(5, "orbit.killfeed.streak_5")
    streak(10, "orbit.killfeed.streak_10")
    broadcastTo { instance.players }
}
```

### Usage

```kotlin
feed.reportKill(KillEvent(killer = attacker, victim = target))
feed.clear()
```

### Features

| Feature | Description |
|---------|-------------|
| Custom renderer | Full control over kill message Component per viewer |
| Effects | Sounds, particles, or any side effect per kill |
| First blood | One-time announcement on first kill of the game |
| Multi-kill | Time-windowed consecutive kills (double, triple, etc.) |
| Kill streaks | Announcements at configurable streak milestones |
| Broadcast control | Choose who receives kill feed messages |

### Integration

Game modes create a `KillFeed` and call `reportKill()` in their damage/death handlers.
