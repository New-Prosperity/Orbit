# Tab List

Player extension functions for setting tab list header and footer with MiniMessage support, plus auto-managed `LiveTabList` for lifecycle-free usage.

## LiveTabList

Self-managing tab list with automatic show/refresh lifecycle. Auto-shows on first spawn, cleans up on disconnect, refreshes on a configurable interval.

```kotlin
val tab = liveTabList {
    refreshEvery(5.seconds)
    header("<gold>MY SERVER")
    footer { "Online: <white>${SessionStore.size} | ${Orbit.serverName}" }
}

tab.uninstall()
```

### LiveTabListBuilder API

| Method | Description |
|---|---|
| `header(String)` | Static MiniMessage header |
| `header((Player) -> String)` | Dynamic per-player header |
| `footer(String)` | Static MiniMessage footer |
| `footer((Player) -> String)` | Dynamic per-player footer |
| `refreshEvery(Duration)` | Refresh interval (default 5s) |

### LiveTabList API

| Method | Description |
|---|---|
| `show(player)` | Manually show to a player |
| `refreshAll()` | Re-evaluate header/footer for all viewers |
| `uninstall()` | Cancel task, remove event node, clear viewers |

---

## Key Functions

- **`Player.tabList { }`** — DSL-style header/footer setter
- **`Player.setTabList(header, footer)`** — direct header/footer setter

## Usage

```kotlin
player.tabList {
    header("<gold><bold>My Server")
    footer("<gray>Online: 42")
}

player.setTabList("<gold>Header", "<gray>Footer")
```

Both functions accept MiniMessage-formatted strings.
