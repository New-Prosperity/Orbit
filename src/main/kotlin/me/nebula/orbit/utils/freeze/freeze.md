# Freeze

Player movement freeze system using event cancellation.

## FreezeManager

| Method | Description |
|---|---|
| `start()` | Register movement cancel event listener |
| `stop()` | Remove listener and unfreeze all |
| `freeze(player)` | Freeze a player |
| `unfreeze(player)` | Unfreeze a player |
| `isFrozen(player)` | Check by player |
| `isFrozen(uuid)` | Check by UUID |
| `toggle(player)` | Toggle freeze, returns `true` if now frozen |
| `unfreezeAll()` | Unfreeze everyone |
| `frozenPlayers()` | `Set<UUID>` of all frozen players |

## Extension Functions

| Function | Description |
|---|---|
| `player.freeze()` | Shortcut for `FreezeManager.freeze` |
| `player.unfreeze()` | Shortcut for `FreezeManager.unfreeze` |
| `player.isFrozen` | Property shortcut for `FreezeManager.isFrozen` |

## Example

```kotlin
FreezeManager.start()

player.freeze()

if (player.isFrozen) {
    player.sendMM("<red>You are frozen!")
}

player.unfreeze()

val nowFrozen = FreezeManager.toggle(player)
```
