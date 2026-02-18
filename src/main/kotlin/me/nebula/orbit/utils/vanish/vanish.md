# Vanish

Packet-level player hiding. Vanished players are invisible to others via `DestroyEntitiesPacket`, and newly joining players also cannot see them.

## VanishManager

| Method | Description |
|---|---|
| `start()` | Register spawn event listener for hiding vanished players from new joiners |
| `stop()` | Remove listener and clear vanish state |
| `vanish(player)` | Hide player from all non-vanished players |
| `unvanish(player)` | Reveal player to everyone |
| `isVanished(player)` | Check by player |
| `isVanished(uuid)` | Check by UUID |
| `toggle(player)` | Toggle vanish, returns `true` if now vanished |
| `vanishedPlayers()` | `Set<UUID>` of all vanished players |

## Extension Functions

| Function | Description |
|---|---|
| `player.vanish()` | Shortcut for `VanishManager.vanish` |
| `player.unvanish()` | Shortcut for `VanishManager.unvanish` |
| `player.isVanished` | Property shortcut for `VanishManager.isVanished` |

## Example

```kotlin
VanishManager.start()

player.vanish()

if (player.isVanished) {
    player.sendMM("<gray>You are vanished")
}

player.unvanish()

val nowVanished = VanishManager.toggle(player)
```
