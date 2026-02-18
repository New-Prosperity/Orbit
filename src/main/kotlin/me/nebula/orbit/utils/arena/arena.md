# Arena

DSL for creating game arenas with spawn points, team spawns, spectator support, and a global registry.

## Key Classes

- **`Arena`** -- manages players, spectators, and spawns within a region and instance
- **`SpawnPoint`** -- position with optional team name
- **`ArenaBuilder`** -- DSL builder
- **`ArenaRegistry`** -- global thread-safe arena registry

## Usage

### Create

```kotlin
val myArena = arena("duels-1") {
    instance(gameInstance)
    region(cuboidRegion("duels-1-region", min, max))
    spawn(Pos(10.0, 64.0, 0.0), team = "red")
    spawn(Pos(-10.0, 64.0, 0.0), team = "blue")
    spectatorSpawn(Pos(0.0, 70.0, 0.0))
}
ArenaRegistry.register(myArena)
```

### Player Management

```kotlin
myArena.addPlayer(player)
myArena.addPlayerToTeam(player, "red")
myArena.addSpectator(player)
myArena.removePlayer(player)
myArena.respawn(player)
myArena.respawnToTeam(player, "blue")
```

### Queries

```kotlin
myArena.playerCount
myArena.onlinePlayers()
myArena.isPlayer(uuid)
myArena.containsPosition(pos)
```

### Registry

```kotlin
ArenaRegistry["duels-1"]
ArenaRegistry.require("duels-1")
ArenaRegistry.all()
ArenaRegistry.unregister("duels-1")
```

Spawns rotate round-robin. Team spawns fall back to round-robin if no matching team spawn exists.
