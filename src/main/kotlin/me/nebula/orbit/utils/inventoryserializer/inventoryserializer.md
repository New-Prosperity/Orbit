# InventorySerializer

Serialize/deserialize player inventories to/from byte arrays. Useful for persistence, transfer, or snapshot storage.

## Usage

```kotlin
val data = player.serializeInventory()
// store `data` somewhere (database, file, etc.)

// later, restore:
player.deserializeInventory(data)
```

Object API:

```kotlin
val bytes = InventorySerializer.serialize(player)
InventorySerializer.deserialize(player, bytes)
```

## API

- `InventorySerializer.serialize(player): ByteArray` -- captures all 46 inventory slots (including armor and offhand)
- `InventorySerializer.deserialize(player, data)` -- clears inventory and restores from byte array
- `Player.serializeInventory(): ByteArray` -- extension function
- `Player.deserializeInventory(data: ByteArray)` -- extension function

## Format

- Version-tagged binary format (version 1)
- Stores slot index, material key, and amount for each non-air slot
- Skips air slots to minimize payload size
- Uses `DataOutputStream`/`DataInputStream` for portable serialization
