# Motd

Server list MOTD configuration via DSL with MiniMessage support.

## Usage

```kotlin
motd {
    line1 = "<gradient:gold:yellow>Nebula Network</gradient>"
    line2 = "<gray>Season 3 is live!"
    maxPlayers = 200
}
```

## Key API

- `motd { }` — DSL to configure and install the MOTD handler
- `line1` — first line (MiniMessage string)
- `line2` — second line (MiniMessage string)
- `maxPlayers` — displayed max player count
- `MotdManager.setConfig(config)` — update config at runtime
- `MotdManager.getConfig()` — get current config
- `MotdManager.install()` — register the `ServerListPingEvent` listener (idempotent)
