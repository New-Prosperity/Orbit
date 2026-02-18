# Spectate

Player spectating system with automatic game mode save/restore.

## SpectateManager

| Method | Description |
|---|---|
| `startSpectating(spectator, target)` | Save game mode, switch to SPECTATOR, and spectate target |
| `stopSpectating(spectator)` | Stop spectating and restore previous game mode |
| `isSpectating(player)` | Check if player is spectating |
| `getTarget(spectator)` | Get target UUID |
| `spectatorsOf(target)` | List of UUIDs spectating a target |
| `clear()` | Clear all spectating state |

## Extension Functions

| Function | Description |
|---|---|
| `player.spectatePlayer(target)` | Shortcut for `SpectateManager.startSpectating` |
| `player.stopSpectatingPlayer()` | Shortcut for `SpectateManager.stopSpectating` |
| `player.isSpectatingPlayer` | Property shortcut for `SpectateManager.isSpectating` |

## Example

```kotlin
player.spectatePlayer(targetPlayer)

if (player.isSpectatingPlayer) {
    player.sendMM("<gray>You are spectating")
}

player.stopSpectatingPlayer()

val viewers = SpectateManager.spectatorsOf(targetPlayer)
```
