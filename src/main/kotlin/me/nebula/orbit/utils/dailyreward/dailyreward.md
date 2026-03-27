# dailyreward

Persistence-agnostic daily reward / login streak system with DSL configuration and GUI rendering.

## Data Classes

- `RewardItem(type, amount, extra)` — single reward entry (e.g., type="coins" amount=100)
- `DailyRewardEntry(day, rewards)` — rewards granted on a specific streak day
- `DailyRewardConfig(entries, resetAfterMiss, cycleDays)` — immutable config; cycleDays=0 means no cycling
- `DailyRewardState(streak, lastClaimEpochDay)` — player state (serialize/persist externally)
- `DailyRewardResult(rewards, newStreak, isNewDay)` — claim result

## DSL

```kotlin
val config = dailyReward {
    day(1) { reward("coins", 100); reward("xp", 50) }
    day(2) { reward("coins", 150); reward("xp", 75) }
    day(3) { reward("coins", 200); reward("xp", 100); reward("cosmetic", 1, "trail_flame") }
    day(7) { reward("coins", 500); reward("xp", 250) }
    day(30) { reward("coins", 2000); reward("xp", 1000) }
    resetAfterMiss(true)
    cycleDays(30)
}
```

## Claiming

```kotlin
val (result, newState) = config.claim(playerState)
if (result.isNewDay) {
    result.rewards.forEach { grantReward(player, it) }
    saveState(player.uuid, newState)
}
```

Sparse milestones: if day 4 is not defined but day 3 is, streak day 4 resolves to day 3's rewards (highest defined day <= streak).

## GUI

```kotlin
val items = config.buildGuiItems(currentStreak)
// Returns List<Pair<Int, ItemStack>>:
//   Green glass  = claimed days (day <= currentStreak)
//   Gold glass   = today's reward (day == currentStreak + 1)
//   Gray glass   = future days
```
