# CombatArena

Pre-built 1v1/FFA combat arena management with kill/death/damage tracking, kit application, and timed rounds.

## Usage

```kotlin
val arena = combatArena("duel-1") {
    instance(gameInstance)
    spawnPoints(listOf(Pos(10.0, 65.0, 0.0), Pos(-10.0, 65.0, 0.0)))
    maxPlayers(2)
    kit(myKit)
    duration(120.seconds)
    onKill { killer, victim -> killer.sendMM("<green>You killed ${victim.username}!") }
    onEnd { result -> println("Winner: ${result.winner}") }
}

CombatArenaManager.register(arena)
CombatArenaManager.join("duel-1", player1)
CombatArenaManager.join("duel-1", player2)
CombatArenaManager.start("duel-1")
```

Track combat events:

```kotlin
arena.recordKill(killer, victim)
arena.recordDamage(attacker, 5.0)
```

## API

- `combatArena(name) { }` -- DSL to create a `CombatArena`
- `CombatArena.join(player)` / `leave(player)` -- player management
- `CombatArena.start()` / `end()` / `reset()` -- lifecycle
- `CombatArena.recordKill(killer, victim)` -- track kills, auto-ends 1v1 on last player
- `CombatArena.recordDamage(attacker, amount)` -- track damage dealt
- `CombatArena.statsOf(player): PlayerStats?` -- get player's kills/deaths/damage
- `CombatArenaManager.register(arena)` / `unregister(name)` -- registry
- `CombatArenaManager.join(name, player)` / `leave(player)` -- convenience methods
- `CombatArenaManager.active()` / `waiting()` -- filter arenas by state
- `CombatArenaManager.findArena(player)` -- find arena containing player

## ArenaResult

Returned via `onEnd` callback:
- `arenaName` -- arena identifier
- `winner` -- UUID of player with most kills
- `stats` -- map of UUID to `PlayerStats` (kills, deaths, damageDealt)
- `durationMs` -- total match duration in milliseconds
