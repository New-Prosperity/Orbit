# RewardDistributor

DSL-configured post-game reward system. Distributes currency rewards to players based on participation, kills, and configurable conditions (winner, top killer, custom rules). Integrates with Gravity's `EconomyStore`.

## API

### DSL

```kotlin
val rewards = rewardDistributor {
    announcement("orbit.reward.earned")
    participation("coins", 10.0)
    perKill("coins", 5.0)
    winnerRule {
        reward("coins", 50.0)
    }
    topKillerRule {
        reward("coins", 25.0)
    }
    rule("survivor", RewardCondition { uuid, result ->
        result.stats["Kills"]?.any { it.uuid == uuid } == true
    }) {
        reward("coins", 10.0, "survival_bonus")
    }
}
```

### Usage

```kotlin
rewards.distribute(matchResult, StatTracker.players())
```

Called in `persistGameStats()` or `onEndingStart()`.

### Reward Types

| Type | Description |
|------|-------------|
| `participation` | Flat reward for all participants |
| `perKill` | Reward multiplied by kill count |
| `winnerRule` | Rewards for the match winner |
| `topKillerRule` | Rewards for the top killer |
| Custom `rule` | Any `RewardCondition` predicate |

### Announcement

Set `announcement("orbit.reward.earned")` to notify each player of their total rewards. Translation key receives `<amount>` and `<currency>` placeholders.
