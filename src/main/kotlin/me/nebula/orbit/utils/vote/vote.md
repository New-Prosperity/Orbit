# Vote

Timed poll system with typed options, tally, and completion callbacks.

## DSL

```kotlin
val mapPoll = poll<String>("Choose a map") {
    option("Desert")
    option("Forest")
    option("Snow")
    durationTicks(600)
    durationSeconds(30)
    displayName { it.uppercase() }
    onComplete { result ->
        val winner = result.winner ?: "None"
        broadcastAllMM("<gold>Map selected: <yellow>$winner")
    }
}
```

## API

| Method | Description |
|---|---|
| `start()` | Begin the poll countdown |
| `vote(player, optionIndex)` | Cast a vote (returns `Boolean`) |
| `vote(uuid, optionIndex)` | Cast by UUID |
| `removeVote(uuid)` | Remove a vote |
| `hasVoted(uuid)` | Check if already voted |
| `tally()` | `Map<Int, Int>` of option index to vote count |
| `tallyNamed()` | `Map<String, Int>` using display names |
| `end()` | End early and trigger `onComplete` |
| `cancel()` | Cancel without triggering `onComplete` |
| `optionDisplay(index)` | Display name for an option |

## Properties

| Property | Description |
|---|---|
| `isActive` | Whether the poll is currently running |
| `ticksRemaining` | Ticks until auto-end |
| `totalVotes` | Number of votes cast |

## PollResult

| Field | Description |
|---|---|
| `winner` | Winning option (or `null` if no votes) |
| `tally` | `Map<Int, Int>` of votes per option |
| `totalVotes` | Total vote count |
| `options` | Original option list |

## Example

```kotlin
val poll = poll<String>("Next gamemode?") {
    options("FFA", "Teams", "Duels")
    durationSeconds(30)
    onComplete { result ->
        println("Winner: ${result.winner}, Votes: ${result.totalVotes}")
    }
}

poll.start()
poll.vote(player, 0)
```
