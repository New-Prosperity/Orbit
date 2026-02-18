# Scoreboard

Unified scoreboard system: basic `ManagedScoreboard`, `PerPlayerScoreboard` with placeholders, `AnimatedScoreboard` with frame cycling, `TeamScoreboard` with auto-assign, and `ObjectiveTracker` for score tracking with multiple display modes.

## ManagedScoreboard

Static sidebar with updatable title and lines. MiniMessage format throughout.

```kotlin
val board = scoreboard("<gold>My Server") {
    line("<white>Online: <green>0")
    line("")
    dynamicLine { "<yellow>${onlineCount()}" }
    animatedLine(listOf("<red>Frame 1", "<blue>Frame 2", "<green>Frame 3"))
}

board.show(player)
board.updateTitle("<red>Game Over")
board.updateLine(0, "<white>Online: <green>42")
board.hide(player)
```

### Player Extensions

```kotlin
player.showScoreboard(board)
player.hideScoreboard(board)
player.updateScoreboard(board, 0, "<white>Online: <green>42")
```

Lines indexed top-to-bottom starting at 0.

---

## PerPlayerScoreboard

Per-player sidebars with `{placeholder}` resolution.

```kotlin
val pps = perPlayerScoreboard("<gold>Welcome, {player}!") {
    line("<white>Rank: <green>{rank}")
    line("<white>Coins: <yellow>{coins}")
    line("")
    line("<gray>play.example.com")
}

pps.show(player, mapOf("player" to player.username, "rank" to "VIP", "coins" to "1500"))
pps.update(player, mapOf("player" to player.username, "rank" to "VIP", "coins" to "2000"))
pps.hide(player)
pps.hideAll()
```

---

## AnimatedScoreboard

Frame-based line animation with configurable tick interval.

```kotlin
val anim = animatedScoreboard("<gold>Server", intervalTicks = 10) {
    line(0, "<white>Static line")
    animatedLine(1, listOf("<red>Fire", "<yellow>Fire", "<gold>Fire"))
    line(2, "<gray>play.example.com")
}

anim.show(player)
anim.hide(player)
anim.destroy()
```

### AnimatedScoreboardBuilder API

| Method | Description |
|---|---|
| `line(index, text)` | Static line at index |
| `animatedLine(index, frames)` | Cycling frames at index |

---

## TeamScoreboard

Team-scoped sidebars with dynamic line providers and auto-assignment via `TeamScoreboardManager`.

```kotlin
val tsb = teamScoreboard("red_team") {
    title("<red>Red Team")
    staticLine("<gray>-----------")
    line { "<white>Score: <red>${getTeamScore("red")}" }
    line { "<white>Players: <red>${getTeamSize("red")}" }
    staticLine("<gray>-----------")
}

TeamScoreboardManager.register(tsb)
TeamScoreboardManager.assignPlayer(player, "red_team")
TeamScoreboardManager.updateAll()
TeamScoreboardManager.removePlayer(player)
```

### TeamScoreboardManager API

| Method | Description |
|---|---|
| `register(TeamScoreboard)` | Register a team scoreboard |
| `unregister(teamName)` | Unregister and hide all |
| `[teamName]` | Lookup by name |
| `require(teamName)` | Lookup or throw |
| `assignPlayer(player, teamName)` | Show team board, auto-hide previous |
| `removePlayer(player)` | Remove from current team |
| `playerTeam(player)` | Get player's team name |
| `updateAll()` | Refresh all team boards for online viewers |
| `clear()` | Hide all and clear registry |

---

## ObjectiveTracker

Score tracking with sidebar, below-name, and tab-list display modes.

### Register Objective

```kotlin
val config = objective("kills") {
    displayName("<red>Kills")
    sidebar()
    belowName()
    tabList()
}
```

### ObjectiveBuilder API

| Method | Description |
|---|---|
| `displayName(String)` | MiniMessage display name |
| `sidebar()` | Enable sidebar display |
| `belowName()` | Enable below-name display |
| `tabList()` | Enable tab-list footer display |

### Score Operations

```kotlin
ObjectiveTracker.addScore(player, "kills", 1)
ObjectiveTracker.setScore(player, "kills", 50)
ObjectiveTracker.getScore(player, "kills")
ObjectiveTracker.getScore(uuid, "kills")
ObjectiveTracker.resetScore(player, "kills")
ObjectiveTracker.resetAll("kills")
```

### Leaderboard and Ranking

```kotlin
val top: List<Pair<UUID, Int>> = ObjectiveTracker.leaderboard("kills", limit = 10)
val all: Map<UUID, Int> = ObjectiveTracker.allScores("kills")
val rank: Int = ObjectiveTracker.rank(uuid, "kills")
```

### Sidebar Display

```kotlin
ObjectiveTracker.showSidebar(player, "kills")
ObjectiveTracker.hideSidebar(player, "kills")
```

Display updates automatically when scores change. Below-name sets `customName`, tab-list sets footer.
