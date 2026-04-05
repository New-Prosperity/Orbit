# Nameplate — Multi-Line Player Nametag System

Packet-based TextDisplay entities riding players as passengers. Supports multi-line text, per-viewer translations, dynamic content, and conditional lines. Zero tick cost for positioning.

## How It Works

Each player gets a virtual `TextDisplay` entity (negative ID, packet-only) mounted as a passenger. MC handles positioning automatically. Text is rendered per-viewer using MiniMessage, with `\n` for line breaks. Diff-checked — packets only sent when content changes.

## Quick Start — Global Layout

```kotlin
NameplateManager.setLayout(nameplateLayout("hub") {
    staticLine("<gold><bold>NEBULA")
    line { player -> "<${player.rankColor}>${player.rankPrefix}<white>${player.username}" }
    line { player -> "<gray>Level ${player.level}" }
})
NameplateManager.install(handler)
```

All players automatically get this nameplate on spawn.

## Per-Minigame Layout

```kotlin
val brLayout = nameplateLayout("battleroyale") {
    yOffset(0.3f)
    scale(0.8f)
    transparentBackground()

    line { player -> "<${getTeamColor(player)}>${player.username}" }
    conditionalLine({ isAlive(it) }) { player ->
        "<red>\u2764 ${player.health.toInt()}"
    }
    conditionalLine({ !isAlive(it) }) { _ -> "<gray>\u2620 Dead" }
    line { player -> "<yellow>${getKills(player)} kills" }
}

NameplateManager.setLayout(brLayout, refreshIntervalTicks = 5)
```

## Translated Lines

Rendered in the VIEWER's language, not the target's:

```kotlin
nameplateLayout("global") {
    line { player -> "<${player.rankColor}>${player.rankPrefix}<white>${player.username}" }
    translatedLine("nameplate.level") { player ->
        arrayOf("level" to player.level.toString())
    }
    translatedLine("nameplate.guild", "tag" to "[GUILD]")
}
```

Translation key `nameplate.level` with `{level}` placeholder, resolved in each viewer's locale.

## Per-Viewer Lines

Different viewers see different content:

```kotlin
nameplateLayout("game") {
    line { player -> "<white>${player.username}" }
    perViewerLine { target, viewer ->
        if (sameTeam(target, viewer)) "<green>Ally"
        else "<red>Enemy"
    }
    perViewerLine { target, viewer ->
        if (viewer.hasPermission("staff")) "<dark_gray>[${target.ping}ms]"
        else null
    }
}
```

Returning `null` from any line hides that line for that viewer.

## Conditional Lines

```kotlin
conditionalLine({ it.isSneaking }) { _ -> "<gray>Sneaking..." }
conditionalLine({ it.health < 10f }) { player -> "<red>Low HP: ${player.health.toInt()}" }
conditionalLine({ isInCombat(it) }) { _ -> "<red>\u2694 In Combat" }
```

## Visual Configuration

```kotlin
nameplateLayout("styled") {
    yOffset(0.3f)                    // height above head (default 0.3)
    scale(1.0f)                      // text scale (default 1.0)
    backgroundColor(0x40000000)      // ARGB background (default semi-transparent black)
    transparentBackground()          // no background
    shadow(true)                     // text shadow (default true)
    seeThrough(false)                // visible through blocks (default false)
    lineWidth(200)                   // max line width in pixels (default 200)
    viewRange(1.0f)                  // render distance multiplier (default 1.0)
}
```

## API

### NameplateManager

| Method | Description |
|---|---|
| `setLayout(layout, refreshTicks)` | Set global layout, auto-applied on spawn |
| `apply(player)` | Manually apply nameplate to player |
| `remove(player)` | Remove nameplate from player |
| `refresh(player)` | Force re-render for all viewers of this player |
| `refreshAll()` | Re-render all nameplates |
| `showTo(target, viewer)` | Show target's nameplate to specific viewer |
| `hideTo(target, viewer)` | Hide target's nameplate from specific viewer (also clears cached render state) |
| `install(eventNode)` | Register spawn/disconnect listeners + refresh task |
| `uninstall()` | Remove all nameplates and listeners |

### Player Extensions

```kotlin
player.applyNameplate()
player.removeNameplate()
player.refreshNameplate()
```

## Switching Layouts (Minigame Lifecycle)

```kotlin
fun onGameStart() {
    NameplateManager.setLayout(gameLayout, refreshIntervalTicks = 5)
    for (player in instance.players) NameplateManager.apply(player)
}

fun onGameEnd() {
    NameplateManager.setLayout(hubLayout, refreshIntervalTicks = 20)
    for (player in instance.players) NameplateManager.apply(player)
}
```

## Architecture

- Virtual `TextDisplay` entity with negative ID (no server-side entity)
- Mounted as passenger via `SetPassengersPacket` — MC handles positioning
- Billboard mode CENTER — always faces the viewer
- Per-viewer rendering: each viewer gets their own text (translations, team colors)
- Diff-checked: `EntityMetaDataPacket` only sent when rendered text changes
- Auto viewer management on spawn/disconnect
- Refresh task runs every N ticks (configurable, default 10)
