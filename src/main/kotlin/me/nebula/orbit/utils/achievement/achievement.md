# Achievement

Network-wide achievement system with persistent progress (Gravity `AchievementStore`), categories, stat-based triggers, rewards, prerequisites, and vanilla advancement toast UI.

## DSL

```kotlin
val ach = achievement("first-kill") {
    name = Component.text("First Blood", NamedTextColor.RED)
    description = Component.text("Get your first kill")
    category = AchievementCategories.COMBAT
    icon = Material.DIAMOND_SWORD
    maxProgress = 1
    frameType = FrameType.TASK
    reward("coins", 500)
    reward("xp", 100)
    requires("tutorial-complete")
}
AchievementRegistry.register(ach)
```

## Rewards

`AchievementReward(type: String, amount: Int)` — attached to achievements via the builder.

```kotlin
achievement("master-slayer") {
    reward("coins", 1000)
    reward("xp", 250)
}
```

Access via `achievement.rewards` after registration.

## Prerequisites

Achievements can require other achievements to be completed before they can be unlocked.

```kotlin
achievement("advanced-combat") {
    requires("first-kill")
    requires("ten-kills")
}
```

Check eligibility:
```kotlin
AchievementRegistry.canUnlock(playerUuid, "advanced-combat") // true if all prerequisites completed
```

## Categories

Registry-based via `AchievementCategories`. Built-in: `GENERAL`, `COMBAT`, `SURVIVAL`, `SOCIAL`, `EXPLORATION`, `MASTERY`.

Custom categories for game modes:
```kotlin
val BR = AchievementCategories.register("battleroyale", "orbit.achievement.category.battleroyale")
```

| Method | Description |
|---|---|
| `register(id, displayKey)` | Register custom category, returns `AchievementCategory` |
| `[id]` | Lookup by ID |
| `all()` | All registered categories |
| `unregister(id)` | Remove custom category |

## Persistence

- `AchievementStore` in Gravity — SQL-backed, write-behind 5s
- `AchievementRegistry.loadPlayer(uuid)` on join, `unloadPlayer(uuid)` on disconnect
- `progress()` and `complete()` atomically update via `IncrementAchievementProcessor` / `SetAchievementCompletedProcessor`

## Triggers

```kotlin
AchievementTriggerManager.bindThreshold("kills-100", "kills", 100)
AchievementTriggerManager.evaluate(player, "kills", currentKills)
```

Custom trigger:
```kotlin
AchievementTriggerManager.bind("first-win", "wins") { uuid, _, value -> value >= 1 }
```

## AchievementRegistry

| Method | Description |
|---|---|
| `register(achievement)` | Register an achievement |
| `unregister(id)` | Remove by ID |
| `[id]` | Operator get |
| `all()` | All registered |
| `byCategory(category)` | Filter by category |
| `loadPlayer(uuid)` | Load from store on join |
| `unloadPlayer(uuid)` | Unload local cache |
| `progress(player, id, amount)` | Increment + persist |
| `complete(player, id)` | Force complete + persist |
| `isCompleted(player/uuid, id)` | Check completion |
| `completedCount(player/uuid)` | Count completed |
| `completedInCategory(uuid, cat)` | Count in category |
| `totalInCategory(cat)` | Total in category |
| `canUnlock(uuid, id)` | Check all prerequisites are completed |
| `onUnlock(handler)` | Custom unlock handler |

## Progress Bar

Standalone helper for rendering text-based progress bars:

```kotlin
progressBar(7, 10)       // [██████████████░░░░░░]
progressBar(3, 10, 10)   // [███░░░░░░░]
```

## Unlock Notification

Default: vanilla advancement toast via `player.sendNotification(Notification(...))` + `UI_TOAST_CHALLENGE_COMPLETE` sound. Override with `onUnlock { }`.
