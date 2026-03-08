# Ceremony

DSL-configured post-game ceremony system. Displays titles, plays sounds, builds a podium, launches repeating fireworks, shows personal stats, and auto-spectates the winner.

## API

### DSL

```kotlin
val c = ceremony(instance, result) {
    podiumPosition(1, Pos(0.0, 64.0, 0.0))
    podiumPosition(2, Pos(3.0, 64.0, 0.0))
    podiumPosition(3, Pos(-3.0, 64.0, 0.0))
    fireworks(interval = 15, max = 20)
    personalStats("kills", "deaths", "damage_dealt")
    spectateWinner()
    winnerTitle("orbit.ceremony.title.winner", "orbit.ceremony.subtitle.winner")
    loserTitle("orbit.ceremony.title.loser", "orbit.ceremony.subtitle.loser")
    drawTitle("orbit.ceremony.title.draw")
}
```

### Usage

```kotlin
override fun onEndingStart(result: MatchResult) {
    val c = ceremony(gameInstance, result) { ... }
    c.start(gameInstance.players)
}
```

Call `c.stop()` on cleanup (enterWaiting / onEndingComplete).

### Features

| Feature | Description |
|---|---|
| Titles | Per-player VICTORY/DEFEAT/DRAW titles with translation keys |
| Sounds | Winner/loser sound events |
| Podium | Uses existing `PodiumDisplay` — gold/iron/copper blocks, teleport |
| Fireworks | Repeating firework rockets around winner position |
| Personal Stats | Delayed chat summary of each player's game stats |
| Spectate Winner | All non-winners auto-spectate the winner |

### Translation Keys

- `orbit.ceremony.title.winner` / `orbit.ceremony.subtitle.winner`
- `orbit.ceremony.title.loser` / `orbit.ceremony.subtitle.loser`
- `orbit.ceremony.title.draw`
- `orbit.ceremony.stats_header`
- `orbit.ceremony.stat_entry` — `<stat>`, `<value>`
- `orbit.ceremony.placement` — `<place>`
