# Portal

Region-based portals that teleport players between instances or positions on entry, with cooldown support.

## DSL

```kotlin
val p = portal("hub-to-arena") {
    sourceInstance = hubInstance
    region(10.0, 60.0, 10.0, 15.0, 65.0, 15.0)
    destinationInstance = arenaInstance
    destinationPosition = Pos(0.5, 64.0, 0.5)
    cooldownMs = 3000
}
```

## PortalManager

| Method | Description |
|---|---|
| `register(portal)` | Register and auto-install if needed |
| `unregister(name)` | Remove by name |
| `[name]` | Operator get |
| `all()` | All registered portals |
| `install()` | Register move event listener |
| `uninstall()` | Remove event listener |
| `clear()` | Remove all portals and uninstall |

## Properties

| Property | Default | Description |
|---|---|---|
| `sourceInstance` | required | Instance containing the portal region |
| `region` | required | Bounding box (minX/Y/Z, maxX/Y/Z) |
| `destinationInstance` | required | Target instance |
| `destinationPosition` | `Pos(0, 64, 0)` | Target position |
| `cooldownMs` | `3000` | Per-player cooldown in milliseconds |

## Example

```kotlin
val portal = portal("spawn-to-pvp") {
    sourceInstance = spawnInstance
    region(50.0, 63.0, 50.0, 55.0, 67.0, 55.0)
    destinationInstance = pvpInstance
    destinationPosition = Pos(0.5, 65.0, 0.5)
    cooldownMs = 5000
}

PortalManager.register(portal)
```
