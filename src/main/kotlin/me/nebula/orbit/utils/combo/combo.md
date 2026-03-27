# Combo Counter

Tracks consecutive hits per player within a configurable tick window. Supports visual display (action bar, title, boss bar), threshold callbacks, damage multipliers, and drop notification.

## Key Classes

- **`ComboConfig`** -- immutable configuration (window, display, thresholds, multiplier, onDrop)
- **`ComboDisplay`** -- enum: `ACTION_BAR`, `TITLE`, `BOSS_BAR`, `NONE`
- **`ComboThreshold`** -- fires handler when combo reaches a specific count
- **`ComboManager`** -- singleton managing per-player combo state, event listeners, tick task, disconnect cleanup
- **`ComboState`** -- per-player count + lastHitTick + optional boss bar

## Player Extensions

- **`Player.combo`** -- current combo count
- **`Player.comboMultiplier`** -- damage multiplier based on current combo

## DSL

```kotlin
val combo = comboCounter {
    windowTicks(40)
    display(ComboDisplay.ACTION_BAR)
    onCombo(5) { player -> player.playSound(SoundEvent.ENTITY_PLAYER_LEVELUP, 1f, 1.2f) }
    onCombo(10) { player -> player.sendMM("<gold><bold>MEGA COMBO!") }
    onDrop { player, count -> player.sendMM("<gray>Combo ended at <yellow>$count") }
    multiplier { count -> 1.0 + (count * 0.05) }
}
combo.install()
```

## API

| Method / Property | Description |
|-------------------|-------------|
| `comboCounter { }` | DSL builder returning `ComboConfig` |
| `ComboConfig.install()` | Registers event listener + tick task |
| `ComboConfig.uninstall()` | Cleans up listeners, tasks, and state |
| `ComboManager.onHit(attacker)` | Increment combo, check thresholds, update display |
| `ComboManager.getCombo(player)` | Current combo count |
| `ComboManager.getMultiplier(player)` | Current damage multiplier |
| `Player.combo` | Extension property for current combo |
| `Player.comboMultiplier` | Extension property for current multiplier |
