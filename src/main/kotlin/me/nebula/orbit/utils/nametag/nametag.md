# NameTag

Per-player custom name tags with prefix, suffix, and display name via DSL.

## Usage

```kotlin
player.setNameTag {
    prefix("<red>[Admin] ")
    suffix(" <gray>[VIP]")
    displayName("<gold>${player.username}")
}

player.clearNameTag()
```

## Key API

- `Player.setNameTag { }` — DSL to configure and apply a name tag
- `Player.clearNameTag()` — remove custom name tag
- `prefix(text)` — MiniMessage prefix before the name
- `suffix(text)` — MiniMessage suffix after the name
- `displayName(text)` — override displayed name (defaults to username)
- `NameTagManager.set(player, config)` — set and apply a config
- `NameTagManager.get(player)` — get current config
- `NameTagManager.apply(player)` — re-apply the stored config
- `NameTagManager.clear(player)` — remove tag and hide custom name
