# Lobby

Configurable lobby system with spawn management, block protection, damage/hunger control, void teleport, and hotbar items.

## DSL

```kotlin
val myLobby = lobby {
    instance = lobbyInstance
    spawnPoint = Pos(0.5, 65.0, 0.5)
    gameMode = GameMode.ADVENTURE
    protectBlocks = true
    disableDamage = true
    disableHunger = true
    voidTeleportY = -64.0
    hotbarItem(0, compassItem) { player -> player.sendMM("<green>Opening navigator...") }
    hotbarItem(4, infoItem)
}
```

## API

| Method | Description |
|---|---|
| `install()` | Registers event listeners on the global event handler |
| `uninstall()` | Removes event listeners |
| `teleportPlayer(player)` | Sends a player to the lobby instance at the spawn point with configured hotbar |

## Properties

| Property | Default | Description |
|---|---|---|
| `instance` | required | Target instance |
| `spawnPoint` | `Pos(0, 64, 0)` | Spawn position |
| `gameMode` | `ADVENTURE` | Game mode set on spawn |
| `protectBlocks` | `true` | Cancel break/place events |
| `disableDamage` | `true` | Cancel damage for players |
| `disableHunger` | `true` | Keep food at 20 |
| `voidTeleportY` | `-64.0` | Y level that triggers respawn at spawn point |

## Example

```kotlin
val lobby = lobby {
    instance = hubInstance
    spawnPoint = Pos(0.5, 100.0, 0.5)
    hotbarItem(0, ItemStack.of(Material.COMPASS)) { player ->
        player.sendMM("<yellow>Server selector")
    }
    hotbarItem(8, ItemStack.of(Material.NETHER_STAR)) { player ->
        player.sendMM("<aqua>Profile")
    }
}

lobby.install()

lobby.teleportPlayer(somePlayer)
```
