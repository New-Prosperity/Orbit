# Freeze

Player movement freeze system using event cancellation. State is stored on the Player via `Tag.Boolean("nebula:frozen")`.

## FreezeManager

| Method | Description |
|---|---|
| `start()` | Register movement cancel event listener |
| `stop()` | Remove listener and unfreeze all |
| `freeze(player)` | Freeze a player (also resets velocity to `Vec.ZERO`) |
| `unfreeze(player)` | Unfreeze a player |
| `isFrozen(player)` | Check if player is frozen |
| `toggle(player)` | Toggle freeze, returns `true` if now frozen |
| `unfreezeAll()` | Unfreeze all online players |
| `frozenPlayers()` | `Set<Player>` of all frozen online players |

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
