# RoundManager

Multi-round game management framework with automatic round/intermission transitions.

## Usage

```kotlin
val manager = roundManager("bedwars") {
    rounds(5)
    intermissionDuration(Duration.ofSeconds(5))
    roundDuration(Duration.ofSeconds(60))
    onRoundStart { round -> broadcastMM("<green>Round $round started!") }
    onRoundEnd { round -> broadcastMM("<yellow>Round $round ended!") }
    onIntermission { nextRound, remaining -> }
    onRoundTick { round, remaining -> }
    onGameEnd { broadcastMM("<gold>Game over!") }
}

manager.start()
```

## Operations

```kotlin
manager.start()                        // Start first round
manager.nextRound()                    // Force next round
manager.endRound()                     // End current round (starts intermission)
manager.endGame()                      // End entire game
manager.addScore(uuid, 1)             // Add score for player
manager.getScore(uuid)                // Global score
manager.getRoundScore(round, uuid)    // Score for specific round
manager.leaderboard(10)               // Top players
manager.roundDurationElapsed()        // Time elapsed in current round
manager.destroy()                     // Cleanup everything
```

## State Machine

`IDLE` -> `ROUND_ACTIVE` -> `INTERMISSION` -> `ROUND_ACTIVE` -> ... -> `FINISHED`

Automatic timer-based transitions between rounds and intermissions.
