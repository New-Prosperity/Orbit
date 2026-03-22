# Hotbar

Named hotbar layout with per-slot click handlers, cooldowns, dynamic items, per-player overrides, and full inventory protection. Supports conditional visibility, click type detection, and auto-refresh.

## Quick Start

```kotlin
val lobbyBar = hotbar("lobby") {
    slot(0, compassItem) { player -> SelectorMenu.open(player) }
    slot(4, profileItem) { player -> openProfile(player) }
    slot(8, hostItem) { player -> openHost(player) }
}
lobbyBar.install()
lobbyBar.apply(player)
```

## Full-Featured Slot

```kotlin
val gameBar = hotbar("game") {
    lockInventory(true)
    preventDrop(true)
    preventSwap(true)
    preventPlace(true)
    refreshEvery(20)

    configuredSlot(0, swordItem) {
        cooldown(10)
        sound(SoundEvent.UI_BUTTON_CLICK, pitch = 1.2f)
        onClick { player, clickType ->
            when (clickType) {
                ClickType.RIGHT -> useAbility(player)
                ClickType.LEFT -> {} // normal attack
                else -> {}
            }
        }
    }

    configuredSlot(4, compassItem) {
        dynamicItem { player ->
            val target = getTrackedTarget(player)
            if (target != null) activeCompassItem else inactiveCompassItem
        }
        visibleWhen { it.gameMode != GameMode.SPECTATOR }
        onClick { player -> openTracker(player) }
    }

    configuredSlot(8, leaveItem) {
        sound(SoundEvent.UI_BUTTON_CLICK)
        cooldown(20)
        visibleWhen(hasPermission("game.leave"))
        onClick { player -> confirmLeave(player) }
    }
}
```

## Protection (all enabled by default)

| Setting | Default | Description |
|---------|---------|-------------|
| `lockInventory(bool)` | `true` | Block all inventory click interactions |
| `preventDrop(bool)` | `true` | Block item dropping (Q key) |
| `preventSwap(bool)` | `true` | Block offhand swap (F key) |
| `preventPlace(bool)` | `true` | Block placing hotbar items as blocks |
| `clearOtherSlots(bool)` | `true` | Clear slots 0-8 before applying |

## Cooldowns

Default 4 ticks (200ms) per slot. Prevents spam-clicking.

```kotlin
configuredSlot(0, item) {
    cooldown(20) // 1 second cooldown
    onClick { player -> doExpensiveAction(player) }
}
```

## Dynamic Items

Items that change based on player state. Re-evaluated on `apply()`, `refresh()`, and auto-refresh.

```kotlin
configuredSlot(4, defaultItem) {
    dynamicItem { player ->
        if (isQueuedForGame(player)) queuedItem else defaultItem
    }
}
```

## Auto-Refresh

Periodically re-evaluates dynamic items and visibility conditions.

```kotlin
hotbar("lobby") {
    refreshEvery(20) // every second
    configuredSlot(4, item) {
        dynamicItem { player -> buildDynamicItem(player) }
        visibleWhen { someCondition(it) }
    }
}
```

## Per-Player Overrides

Override a slot's item for a specific player without changing the hotbar definition.

```kotlin
bar.overrideSlot(player, 4, specialItem)
bar.clearOverride(player, 4)
bar.clearOverrides(player)
```

## Click Types

```kotlin
configuredSlot(0, item) {
    onClick { player, clickType ->
        when (clickType) {
            ClickType.RIGHT -> openMenu(player)
            ClickType.LEFT -> quickAction(player)
            else -> {}
        }
    }
}
```

## API

| Method | Description |
|--------|-------------|
| `apply(player)` | Set items, track player, respect conditions/overrides |
| `remove(player)` | Clear items, untrack, clear overrides |
| `refresh(player)` | Re-evaluate dynamic items and conditions for one player |
| `refreshAll()` | Re-evaluate for all active players |
| `refreshSlot(player, slot)` | Re-evaluate one slot for one player |
| `overrideSlot(player, slot, item)` | Set per-player item override |
| `clearOverride(player, slot)` | Remove one override |
| `clearOverrides(player)` | Remove all overrides for player |
| `isActive(player)` | Check if player has this hotbar |
| `install()` | Register event listeners + start auto-refresh |
| `uninstall()` | Remove listeners, cancel refresh, clear state |
