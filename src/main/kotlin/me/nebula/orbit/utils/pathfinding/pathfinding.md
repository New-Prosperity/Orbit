# Pathfinding

A* pathfinding algorithm for entity navigation within instances.

## Overview

Implements A* pathfinding with heuristic-based path search. Accounts for block solidity and gravity (entities must stand on solid blocks). Includes iteration and distance limits to prevent excessive computation.

## Key API

- `Pathfinder.findPath(instance: Instance, start: Point, end: Point, maxIterations: Int = 1000, maxDistance: Double = 64.0): List<Vec>?` - Find path from start to end, returns null if unreachable or too far
  - Returns list of waypoints in order (inclusive of start, may not include exact endpoint)
  - Returns null if path exceeds maxDistance or iterations limit
- `PathNode` - Immutable node in path search with fCost = gCost + hCost

## Examples

```kotlin
val path = Pathfinder.findPath(instance, player.position, targetPos)
if (path != null) {
    path.forEach { waypoint ->
        println("Navigate to: $waypoint")
    }
} else {
    println("No path found")
}

val shortPath = Pathfinder.findPath(
    instance,
    start,
    end,
    maxIterations = 500,
    maxDistance = 32.0
)
```

## Notes

Uses Manhattan distance heuristic. Neighbors include up to 12 adjacent positions (cardinal and diagonal movements with Y-axis variation). Valid neighbors must have solid blocks below and non-solid blocks at and above.
