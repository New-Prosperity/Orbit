# PlayerData

Full player state snapshots (position, health, food, gamemode, inventory) with capture/restore history.

## Usage

```kotlin
val snapshot = player.captureData()

player.restoreData(snapshot)

player.restoreLatest()

val history = PlayerDataManager.getAll(player.uuid)
PlayerDataManager.clear(player.uuid)
```

## Key API

- `Player.captureData()` — capture a `PlayerSnapshot` of current state
- `Player.restoreData(snapshot)` — restore player to a snapshot state
- `Player.restoreLatest()` — restore most recent snapshot, returns `false` if none
- `PlayerDataManager.capture(player)` — capture and store a snapshot
- `PlayerDataManager.restore(player, snapshot)` — apply a snapshot
- `PlayerDataManager.getLatest(uuid)` — most recent snapshot for a player
- `PlayerDataManager.getAll(uuid)` — full snapshot history
- `PlayerDataManager.clear(uuid)` — remove all snapshots
