# Hologram

Multi-line floating text holograms using TextDisplay entities. Supports global (instance-wide) and per-player (packet-based virtual) holograms.

## Key Classes

- **`HologramBuilder`** -- DSL builder for hologram configuration (lines, spacing, billboard, background, scale)
- **`Hologram`** -- global hologram using real TextDisplay entities visible to all players in the instance
- **`PlayerHologram`** -- per-player hologram using virtual entities via packets, never exists on the server

## Global Hologram

Real TextDisplay entities added to the instance. Visible to all players.

```kotlin
val holo = instance.hologram(Pos(0.0, 65.0, 0.0)) {
    line("<gold>Welcome")
    line("<gray>to the server")
    lineSpacing = 0.3
    billboard = AbstractDisplayMeta.BillboardConstraints.CENTER
    backgroundColor = 0x40000000
    seeThrough = false
    scale = 1.5f
}

holo.updateLine(0, Component.text("Updated Title"))
holo.addLine(Component.text("New Line"))
holo.removeLine(1)
holo.teleport(Pos(10.0, 65.0, 10.0))
holo.remove()
```

## Per-Player Hologram

Packet-based virtual entities. Each line is a separate fake TextDisplay entity tracked by a negative entity ID. Never touches the server entity list.

```kotlin
val holo = player.hologram(Pos(0.0, 65.0, 0.0)) {
    line("<red>Only you can see this")
    line("<gray>Secret info")
    scale = 1.2f
}

holo.updateLine(0, Component.text("Updated"))
holo.hide(player)
holo.show(player)
holo.remove()
```

## Extension Functions

```kotlin
player.showHologram(hologram)
player.hideHologram(hologram)
```

## Details

- Each line is a separate TextDisplay entity positioned with vertical offset based on `lineSpacing`
- Global holograms use `AbstractDisplayMeta.BillboardConstraints` for automatic facing (VERTICAL, CENTER, HORIZONTAL, FIXED)
- Per-player holograms use `SpawnEntityPacket`, `EntityMetaDataPacket`, and `DestroyEntitiesPacket`
- Virtual entity IDs are negative (starting at -1,000,000) to avoid collision with real entities
- All text supports MiniMessage format via `line(String)`
- `backgroundColor` uses ARGB format (e.g., `0x40000000` = semi-transparent black)
- `scale` applies uniformly to all axes
