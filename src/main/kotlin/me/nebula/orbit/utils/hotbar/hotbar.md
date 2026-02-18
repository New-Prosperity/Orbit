# Hotbar

Named hotbar layout with per-slot click handlers. Tracks active players and manages item lifecycle.

## DSL

```kotlin
val lobbyBar = hotbar("lobby") {
    clearOtherSlots = true
    slot(0, compassItem) { player -> player.sendMM("<yellow>Navigator") }
    slot(4, profileItem) { player -> openProfile(player) }
    slot(8, settingsItem) { player -> openSettings(player) }
}
```

## API

| Method | Description |
|---|---|
| `apply(player)` | Set hotbar items and track the player |
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
