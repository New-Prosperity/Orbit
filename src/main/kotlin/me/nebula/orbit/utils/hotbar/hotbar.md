# Hotbar

Named hotbar layout with per-slot click handlers. Tracks active players and manages item lifecycle. Supports per-player conditional visibility via `Condition<Player>`.

## DSL

```kotlin
val lobbyBar = hotbar("lobby") {
    clearOtherSlots = true
    slot(0, compassItem) { player -> player.sendMM("<yellow>Navigator") }
    slot(4, profileItem) { player -> openProfile(player) }
    slot(8, hostItem, visibleWhen = condition { hasTickets(it) }) { player -> openHost(player) }
}
```

## Conditional Slots

Slots accept an optional `visibleWhen: Condition<Player>` parameter. When set:
- `apply(player)` only places the item if the condition passes
- Click handler ignores interactions if the condition fails

Composes with the existing `Condition` DSL (`and`, `or`, `not`):

```kotlin
val vipOrAdmin = condition { isVip(it) } or condition { isAdmin(it) }
slot(8, specialItem, visibleWhen = vipOrAdmin) { player -> doStuff(player) }
```

## API

| Method | Description |
|---|---|
| `apply(player)` | Set visible hotbar items and track the player |
| `remove(player)` | Clear hotbar items and untrack the player |
| `isActive(player)` | Check if player has this hotbar |
| `install()` | Register click event listener globally |
| `uninstall()` | Remove event listener and clear tracked players |

## Properties

| Property | Default | Description |
|---|---|---|
| `name` | required | Unique identifier |
| `clearOtherSlots` | `true` | Clear slots 0-8 before applying |

## Example

```kotlin
val bar = hotbar("game") {
    slot(0, ItemStack.of(Material.STONE_SWORD)) { player ->
        player.sendMM("<red>Fight!")
    }
    slot(8, ItemStack.of(Material.BED)) { player ->
        player.sendMM("<gray>Leave?")
    }
}

bar.install()
bar.apply(player)

bar.remove(player)
bar.uninstall()
```
