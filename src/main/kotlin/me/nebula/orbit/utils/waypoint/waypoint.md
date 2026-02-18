# Waypoint

Named position markers with global and per-player scopes.

## Usage

```kotlin
WaypointManager.setGlobal("spawn", Pos(0.0, 64.0, 0.0), instance)

player.setWaypoint("home")
player.setWaypoint("base", Pos(100.0, 70.0, 200.0), icon = "flag")

val wp = player.getWaypoint("home")
player.removeWaypoint("home")
```

## Key API

- `WaypointManager.setGlobal(name, pos, instance, icon)` — register a global waypoint
- `WaypointManager.removeGlobal(name)` — remove a global waypoint
- `WaypointManager.getGlobal(name)` — get a global waypoint
- `WaypointManager.allGlobal()` — all global waypoints
- `Player.setWaypoint(name, position, icon)` — set a personal waypoint (defaults to current position)
- `Player.removeWaypoint(name)` — remove a personal waypoint
- `Player.getWaypoint(name)` — get a personal waypoint
- `Player.allWaypoints()` — all personal waypoints
- `WaypointManager.clearPlayer(player)` — remove all waypoints for a player
