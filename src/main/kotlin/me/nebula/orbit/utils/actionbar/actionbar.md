# Action Bar — Component-Based Action Bar System

Composable action bar with prioritized slots. Multiple components render left-to-right by priority, joined with a separator. Auto-resend prevents fade-out. Per-slot expiry for timed messages.

## Usage

### Set a slot
```kotlin
player.setActionBarSlot("compass", priority = 10, "<yellow>N <white>45m")
player.setActionBarSlot("cooldown", priority = 20, "<red>Fireball <white>3.2s", durationMs = 5000)
player.setActionBarSlot("coins", priority = 30, Component.text("+50 coins", NamedTextColor.GOLD), durationMs = 3000)
```
Lower priority = further left. Slots with the same ID are replaced. `durationMs = 0` (default) means permanent until removed.

### Update a slot's content
```kotlin
player.updateActionBarSlot("compass", "<yellow>NE <white>32m")
player.updateActionBarSlot("cooldown", Component.text("Fireball 1.5s", NamedTextColor.RED))
```
Only updates content — preserves priority and expiry. No-op if the slot doesn't exist.

### Remove a slot
```kotlin
player.removeActionBarSlot("compass")
```

### Clear everything
```kotlin
player.clearActionBar()
```

### Query
```kotlin
player.hasActionBarSlot("compass")    // Boolean
player.activeActionBarSlots           // Set<String>
```

## Rendering

Active slots are sorted by priority (ascending) and joined with `ActionBarManager.separator` (default: ` │ ` in dark gray).

Example with 3 active slots (priorities 10, 20, 30):
```
N 45m │ Fireball 3.2s │ +50 coins
```

The composed result is sent immediately on every `set`/`update`/`remove` call. A background tick (every 2 ticks) re-sends to prevent vanilla fade-out and cleans up expired slots.

## Configuration

```kotlin
ActionBarManager.separator = Component.text("  ", NamedTextColor.DARK_GRAY)
ActionBarManager.separator = Component.text(" \u2022 ", NamedTextColor.GRAY)
```

## Lifecycle

- `ActionBarManager.install(eventNode)` — registers disconnect cleanup (called in `Orbit.kt`)
- `ActionBarManager.tick()` — periodic re-send + expiry cleanup (scheduled every 2 ticks in `Orbit.kt`)
- Player disconnect → all slots removed automatically

## Example: Game Mode Integration

```kotlin
player.setActionBarSlot("health", 10, "<red>\u2764 <white>${hp}/${maxHp}")
player.setActionBarSlot("kills", 20, "<gray>Kills: <white>$kills")
player.setActionBarSlot("timer", 30, "<yellow>\u23F1 <white>${formatTime(remaining)}")

player.updateActionBarSlot("health", "<red>\u2764 <white>${hp}/${maxHp}")
player.updateActionBarSlot("timer", "<yellow>\u23F1 <white>${formatTime(remaining)}")

player.setActionBarSlot("pickup", 5, "<gold>+1 Diamond Sword", durationMs = 2000)

player.clearActionBar()
```
