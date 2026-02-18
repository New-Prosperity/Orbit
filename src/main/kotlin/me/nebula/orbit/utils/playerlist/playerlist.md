# PlayerList

Per-player tab list header and footer with periodic refresh.

## Usage

```kotlin
playerList {
    header("<gradient:gold:yellow>Nebula Network")
    footer { player -> mm("<gray>Online: ${onlinePlayers.size}") }
    updateIntervalTicks = 40
}

PlayerListManager.uninstall()
```

## Key API

- `playerList { }` — DSL to configure and install the player list handler
- `header(text)` — static MiniMessage header
- `header(block)` — dynamic per-player header `(Player) -> Component`
- `footer(text)` — static MiniMessage footer
- `footer(block)` — dynamic per-player footer `(Player) -> Component`
- `updateIntervalTicks` — refresh rate (default 20)
- `PlayerListManager.install(config)` — start the update task
- `PlayerListManager.uninstall()` — stop updates and clear config
