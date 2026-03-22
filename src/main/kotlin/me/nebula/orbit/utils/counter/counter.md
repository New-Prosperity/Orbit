# Animated Counter

Smooth number animations with easing. Fast at start, slow at end. Works with any display system (action bar, HUD, scoreboard, title).

## Usage

```kotlin
player.animateCounter("coins", from = 1000, to = 1500) { value ->
    player.updateActionBarSlot("coins", "<gold>Coins: <white>$value")
}
```

## With Options

```kotlin
player.animateCounter(
    id = "xp",
    from = oldXp,
    to = newXp,
    durationTicks = 50,
    easing = Easing.EASE_OUT_CUBIC,
    onComplete = { player.playSound(SoundEvent.ENTITY_PLAYER_LEVELUP) },
) { value ->
    player.updateHud("xp_text", "$value XP")
    player.updateHud("xp_bar", (value * 10 / maxXp).toInt())
}
```

## Easing Functions

| Easing | Curve | Best for |
|--------|-------|----------|
| `EASE_OUT_CUBIC` | Fast→slow (default) | Coins, XP, scores |
| `EASE_OUT_QUAD` | Fast→slow (gentler) | Subtle counters |
| `EASE_OUT_QUART` | Fast→slow (snappier) | Dramatic reveals |
| `EASE_IN_QUAD` | Slow→fast | Countdowns |
| `EASE_IN_OUT_QUAD` | Slow→fast→slow | Health bars |
| `LINEAR` | Constant speed | Progress bars |

## Duration

Default: 50 ticks (2.5 seconds). At 20 TPS:
- 20 ticks = 1.0s (snappy)
- 40 ticks = 2.0s (smooth)
- 50 ticks = 2.5s (satisfying, default)
- 60 ticks = 3.0s (dramatic)

## Hub Return Example

```kotlin
handler.addListener(PlayerSpawnEvent::class.java) { event ->
    val player = event.player
    val data = LevelStore.load(player.uuid) ?: LevelData()
    val economy = EconomyStore.load(player.uuid)
    val coins = economy?.balances?.get("coins")?.toLong() ?: 0

    val previousCoins = player.getTag(PREV_COINS_TAG) ?: coins
    val previousXp = player.getTag(PREV_XP_TAG) ?: data.xp

    if (coins != previousCoins) {
        player.animateCounter("coins", from = previousCoins, to = coins) { value ->
            player.updateActionBarSlot("coins", 10, "<gold>Coins: <white>$value")
        }
    }

    if (data.xp != previousXp) {
        player.animateCounter("xp", from = previousXp, to = data.xp, durationTicks = 60) { value ->
            player.updateActionBarSlot("xp", 20, "<aqua>XP: <white>$value")
        }
    }

    player.setTag(PREV_COINS_TAG, coins)
    player.setTag(PREV_XP_TAG, data.xp)
}
```

## Multiple Concurrent Animations

Each animation has a unique `id`. Starting a new animation with the same `id` replaces the previous one.

```kotlin
player.animateCounter("coins", from = 0, to = 500) { ... }
player.animateCounter("xp", from = 100, to = 300) { ... }
player.animateCounter("kills", from = 0, to = 15) { ... }
```

## Control

```kotlin
player.stopCounter("coins")
player.stopAllCounters()
AnimatedCounterManager.isRunning(player, "coins")
```
