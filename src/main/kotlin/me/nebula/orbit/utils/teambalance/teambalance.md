# Team Balance

Team balancing algorithms using snake-draft distribution and score-based variance minimization.

## Key Classes

- **`TeamBalance`** -- object with balance, swap suggestion, and auto-balance methods

## Usage

### Balance evenly

```kotlin
val teams = TeamBalance.balance(players, teamCount = 2)
```

### Balance by score

```kotlin
val teams = TeamBalance.balance(players, teamCount = 4) { player ->
    StatTracker.get(player, "kills").toDouble()
}
```

### Suggest swap to reduce imbalance

```kotlin
val swap = TeamBalance.suggestSwap(teams) { player -> scorer(player) }
if (swap != null) {
    val (playerA, playerB) = swap
    // swap playerA and playerB between their teams
}
```

### Auto-balance new player

```kotlin
val mutableTeams = mutableMapOf(0 to mutableListOf<Player>(), 1 to mutableListOf())
val assignedTeam = TeamBalance.autoBalance(mutableTeams, newPlayer)
```

## API

| Method | Description |
|--------|-------------|
| `balance(players, teamCount)` | Even distribution across teams |
| `balance(players, teamCount, scorer)` | Snake-draft by score descending |
| `suggestSwap(teams, scorer)` | Find swap that minimizes score variance |
| `autoBalance(teams, newPlayer)` | Add player to smallest team, returns team index |
