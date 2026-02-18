# MatchResult

Game result tracking, display, and history system.

## Building Results

```kotlin
val result = matchResult {
    winner(player)
    losers(otherPlayers)
    draw(false)
    mvp(mvpPlayer)
    duration(Duration.ofMinutes(12))
    metadata("map", "Castle")
    stat("kills") {
        player(player1, 15.0)
        player(player2, 8.0)
    }
    stat("deaths") {
        player(player1, 3.0)
        player(player2, 10.0)
    }
}
```

## Display

```kotlin
MatchResultDisplay.sendTo(player, result)        // Chat summary + title
MatchResultDisplay.broadcast(allPlayers, result)  // Send to all
MatchResultDisplay.render(result)                 // List<Component>
```

## History

```kotlin
MatchResultManager.store(result)
MatchResultManager.storeAndDisplay(result, players)
MatchResultManager.recentMatches(10)              // List<MatchResult>
MatchResultManager.playerMatches(uuid, 10)        // Player's matches
MatchResultManager.playerWins(uuid)               // Int
MatchResultManager.playerLosses(uuid)             // Int
MatchResultManager.playerDraws(uuid)              // Int
MatchResultManager.playerMvps(uuid)               // Int
```

## Display Output

Renders a formatted chat summary with:
- Winner announcement or DRAW
- MVP highlight
- Top 3 per stat (gold/silver/bronze)
- Duration
- Title screen (VICTORY/DEFEAT/DRAW)
