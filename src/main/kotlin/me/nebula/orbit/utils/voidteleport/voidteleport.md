# VoidTeleport

Automatically teleport players when they fall below a Y threshold.

## Usage

```kotlin
voidTeleport {
    threshold = -64.0
    destination { player -> Pos(0.0, 64.0, 0.0) }
    onTeleport { player -> player.sendMessage("Rescued from the void!") }
}

VoidTeleportManager.uninstall()
```

## Key API

- `voidTeleport { }` — DSL to configure and install void teleport handling
- `threshold` — Y level that triggers teleport (default -64.0)
- `destination { }` — function returning the teleport destination per player
- `onTeleport { }` — callback after teleport
- `VoidTeleportManager.install(config)` — register the `PlayerMoveEvent` listener
- `VoidTeleportManager.uninstall()` — disable void teleport
