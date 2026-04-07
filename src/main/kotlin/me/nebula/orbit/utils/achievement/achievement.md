# Achievement

Network-wide achievement system with persistent progress (Gravity `AchievementStore`), categories, stat-based triggers, rewards, prerequisites, rarity, tier groups, points, milestones, and vanilla advancement toast UI.

## DSL

```kotlin
val ach = achievement("first-kill") {
    name = Component.text("First Blood", NamedTextColor.RED)
    description = Component.text("Get your first kill")
    category = AchievementCategories.COMBAT
    icon = Material.DIAMOND_SWORD
    maxProgress = 1
    points = 15
    rarity = AchievementRarity.UNCOMMON
    tierGroup = "kill_master"
    tierLevel = 1
    frameType = FrameType.TASK
    reward("coins", 500)
    reward("xp", 100)
    reward("cosmetic", 0, "kill_effect_blood")
    requires("tutorial-complete")
}
AchievementRegistry.register(ach)
```

## Rewards

`AchievementReward(type: String, amount: Int, value: String = "")` — attached to achievements via the builder.

Supported types:
- `"coins"` — adds coins via `AddBalanceProcessor`
- `"xp"` — adds battle pass XP via `BattlePassManager.addXpToAll`
- `"cosmetic"` — unlocks cosmetic via `UnlockCosmeticProcessor(reward.value)`

Rewards are distributed automatically on unlock — no manual handling needed.

## Points

Each achievement has a `points: Int` value. Total points are tracked in `AchievementData.points` and persisted in SQL.

Difficulty-based defaults:
- Easy (maxProgress 1-10): 5 pts
- Medium (maxProgress 10-100): 15 pts
- Hard (maxProgress 100-1000): 50 pts
- Hidden/Challenge: 100 pts

## Rarity

`AchievementRarity` enum: `COMMON` (gray), `UNCOMMON` (green), `RARE` (blue), `EPIC` (purple), `LEGENDARY` (gold).

Assigned statically per achievement. Displayed in menu with color coding.

## Tier Groups

Achievements with the same `tierGroup` string are displayed together in the menu as a tiered progression (bronze/silver/gold). `tierLevel` determines display order.

```kotlin
achievement("warrior") {
    tierGroup = "kill_master"
    tierLevel = 2
}
```

## Milestones

Point thresholds that award bonus rewards when crossed:

| Threshold | Name | Rewards |
|---|---|---|
| 50 | Novice | 100 coins |
| 150 | Explorer | 250 coins |
| 300 | Achiever | 500 coins + title_achiever |
| 500 | Master | 1000 coins + aura_achievement |
| 750 | Grandmaster | 2000 coins + mount_achievement |

Claimed milestones tracked in `AchievementData.claimedMilestones`.

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
AchievementRegistry.canUnlock(playerUuid, "advanced-combat")
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

- `AchievementStore` in Gravity — SQL-backed, write-behind 15s
- `AchievementData` fields: `progress`, `completed`, `points`, `claimedMilestones`
- `AchievementRegistry.loadPlayer(uuid)` on join, `unloadPlayer(uuid)` on disconnect
- `progress()` and `complete()` atomically update via `IncrementAchievementProcessor` / `SetAchievementCompletedProcessor`
- Points are atomically incremented on completion

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
| `progress(player, id, amount)` | Increment + persist + auto-distribute rewards |
| `complete(player, id)` | Force complete + persist + auto-distribute rewards |
| `isCompleted(player/uuid, id)` | Check completion |
| `completedCount(player/uuid)` | Count completed |
| `completedInCategory(uuid, cat)` | Count in category |
| `totalInCategory(cat)` | Total in category |
| `points(player/uuid)` | Total achievement points |
| `pointsInCategory(uuid, cat)` | Points in category |
| `claimedMilestones(uuid)` | Set of claimed milestone thresholds |
| `tierGroupMembers(group)` | Get all achievements in a tier group |
| `totalNonHiddenCount()` | Count of non-hidden achievements |
| `completedNonHiddenCount(uuid)` | Count of completed non-hidden |
| `canUnlock(uuid, id)` | Check all prerequisites are completed |
| `onUnlock(handler)` | Custom unlock handler |

## Unlock Notification

Default: vanilla advancement toast + ENTITY_PLAYER_LEVELUP sound + chat message with name/description/points + reward listing + instance broadcast. Override with `onUnlock { }`.

## Progress Bar

Standalone helper for rendering text-based progress bars:

```kotlin
progressBar(7, 10)       // [##############......] 
progressBar(3, 10, 10)   // [###.......]
```
