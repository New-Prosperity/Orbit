# MatchResult

Game result tracking, display, and history system with team support.

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

## Team Support

```kotlin
val result = matchResult {
    winnerTeam("Red")
    loserTeam("Blue")
    loserTeam("Green")
    teamStat("Red", "total_kills", 45.0)
    teamStat("Red", "objectives", 3.0)
    teamStat("Blue", "total_kills", 30.0)
    teamStat("Blue", "objectives", 1.0)
    duration(Duration.ofMinutes(20))
}
```

| Builder Method | Description |
|---|---|
| `winnerTeam(name)` | Set winning team name |
| `loserTeam(name)` | Add a losing team |
| `teamStat(team, statName, value)` | Add a stat entry for a team |

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
- Team winner (translation key `orbit.util.match_result.team_winner`)
- MVP highlight
- Top 3 per stat (gold/silver/bronze)
- Team stats section (translation key `orbit.util.match_result.team_stats`)
- Duration
- Title screen (VICTORY/DEFEAT/DRAW)

## Translation Keys

| Key | Placeholders |
|---|---|
| `orbit.util.match_result.team_winner` | `{team}` |
| `orbit.util.match_result.team_stats` | none |
