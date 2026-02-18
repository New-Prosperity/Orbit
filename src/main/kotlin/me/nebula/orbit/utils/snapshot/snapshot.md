# Inventory Snapshot

Captures and restores a player's full inventory state including health, food, experience, and level.

## Key Classes

- **`InventorySnapshot`** -- immutable data class holding inventory contents, food, health, experience, and level

## Usage

### Capture

```kotlin
val snapshot = InventorySnapshot.capture(player)

val snapshot = player.captureSnapshot()
```

### Restore

```kotlin
snapshot.restore(player)

player.restoreSnapshot(snapshot)
```

## Captured Data

| Field | Type |
|-------|------|
| `contents` | `Map<Int, ItemStack>` (non-air slots only) |
| `food` | `Int` |
| `health` | `Float` |
| `experience` | `Float` |
| `level` | `Int` |

Restore clears the inventory first, then sets all slots, food, health, experience, and level.
