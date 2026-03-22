# Tab List

## Header/Footer — `LiveTabList`

Section-based header/footer with per-section conditions. Auto-shows on first spawn, refreshes on interval.

```kotlin
val tab = liveTabList {
    refreshEvery(5.seconds)
    header("<gold><bold>MY SERVER")
    header { "Online: <white>${onlineCount()}" }
    header(visibleWhen = { hasPermission(it, "admin") }) { "<red>TPS: ${tps()}" }
    footer("<gray>play.server.com")
    footer(visibleWhen = { it.isSprinting }) { "<green>Sprinting!" }
}

tab.uninstall()
```

## Player Entries — `TabListManager`

Full control over player display, ordering, custom entries, and skin icons.

### Format real players

```kotlin
player.formatTabEntry {
    displayNameMM { p -> "<gold>[VIP] <white>${p.username}" }
    listOrder(10)
}
```

### Global format (applies to all players on spawn)

```kotlin
TabListManager.setGlobalFormat { player ->
    val rank = getRank(player)
    PlayerFormatDef(
        displayName = { miniMessage.deserialize("<${rank.color}>[${rank.tag}] <white>${it.username}") },
        listOrder = rank.priority,
    )
}
```

### Custom (fake) entries

```kotlin
player.addTabEntry("header") {
    displayName("<dark_gray>═══════════")
    listOrder(0)
}

player.addTabEntry("server_info") {
    dynamicNameMM { "<gold>Players: <white>${onlineCount()}" }
    listOrder(1)
    ping(0)
}

player.addTabEntry("category_pvp") {
    displayName("<red><bold>PvP Players")
    listOrder(20)
    visibleWhen { isInPvpMode(it) }
}

player.addTabEntry("npc_display") {
    displayName("<gray>Server Bot")
    listOrder(99)
    skin(skinTextureValue, skinTextureSignature)
    ping(1)
}
```

### Update / Remove

```kotlin
player.updateTabEntry("server_info", "<gold>Players: <white>$newCount")
player.removeTabEntry("header")
player.clearTabEntries()
```

### Skin textures

The `skin(value, signature)` function takes a base64-encoded Mojang skin texture. Get these from the Mojang session API or sites like mineskin.org.

```kotlin
player.addTabEntry("custom_head") {
    displayName("<aqua>Custom NPC")
    skin("ewogICJ0aW1lc3Rhb...", "signatureBase64...")
    listOrder(50)
}
```

No skin = blank head icon. Player entries use the player's own skin automatically.

## Sort Order

`listOrder` controls position — lower values appear higher. MC sorts entries by `listOrder` first, then alphabetically by username within the same order.

| Range | Typical Use |
|-------|-------------|
| 0-9 | Header entries, separators |
| 10-39 | Staff, VIP players |
| 40-60 | Regular players (default: 50) |
| 61-89 | Secondary info |
| 90-99 | Footer entries |

## Lifecycle

- `TabListManager.install(eventNode)` — disconnect cleanup + global format on spawn
- `TabListManager.tick()` — refreshes dynamic entries and global format (every 20 ticks)
- Fake entries are per-viewer — each player has their own custom entries
- Player formats broadcast to all viewers via `sendPacketToViewersAndSelf`

## Conditional Entries

```kotlin
player.addTabEntry("sprint_indicator") {
    displayName("<green>Sprinting")
    listOrder(95)
    visibleWhen { it.isSprinting }
}
```

Conditions are evaluated every tick. Entry is added/removed from the tab list based on the predicate.
