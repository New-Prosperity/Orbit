# Join Leave Message

Configurable join/leave broadcast messages using MiniMessage format. Returns an uninstallable handle.

## DSL

```kotlin
val handle = joinLeaveMessages {
    joinFormat("<green>+ <white>{player}")
    leaveFormat("<red>- <white>{player}")
    onJoin { player ->
        player.sendMM("<gray>Welcome back!")
    }
    onLeave { player -> }
}
```

## Placeholders

| Placeholder | Value |
|---|---|
| `{player}` | Player username |

## Uninstall

```kotlin
handle.uninstall()
```

## Notes

- Join messages trigger on `PlayerSpawnEvent` with `isFirstSpawn = true`
- Leave messages trigger on `PlayerDisconnectEvent`
- Messages broadcast to all online players via `MinecraftServer.getConnectionManager().onlinePlayers`
- All format strings support full MiniMessage syntax
