# Achievement

Achievement system with progress tracking, unlock notifications, and a global registry.

## DSL

```kotlin
val ach = achievement("first-kill") {
    name = Component.text("First Blood", NamedTextColor.RED)
    description = Component.text("Get your first kill")
    icon = Material.DIAMOND_SWORD
    hidden = false
    maxProgress = 1
}
```

## AchievementRegistry

| Method | Description |
|---|---|
| `register(achievement)` | Register an achievement |
| `unregister(id)` | Remove by ID |
| `[id]` | Operator get |
| `all()` | All registered achievements |
| `progress(player, id, amount)` | Increment progress (default +1), auto-unlocks |
| `getProgress(player, id)` | Current progress value |
| `isCompleted(player, id)` | Check if unlocked |
| `completedCount(player)` | Number of completed achievements |
| `totalCount()` | Total registered achievements |
| `onUnlock(handler)` | Custom unlock handler (overrides default title + sound) |
| `resetPlayer(uuid)` | Clear all progress for a player |
| `clear()` | Reset everything |

## Default Unlock Behavior

Shows a gold title "Achievement Unlocked!" with the achievement name as subtitle and plays `UI_TOAST_CHALLENGE_COMPLETE`.

## Example

```kotlin
val killAch = achievement("kills-10") {
    name = Component.text("Warrior")
    description = Component.text("Get 10 kills")
    maxProgress = 10
}
AchievementRegistry.register(killAch)

AchievementRegistry.progress(player, "kills-10")

AchievementRegistry.onUnlock { player, ach ->
    player.sendMM("<gold>Unlocked: ${MiniMessage.miniMessage().serialize(ach.name)}")
}

val done = AchievementRegistry.isCompleted(player, "kills-10")
```
