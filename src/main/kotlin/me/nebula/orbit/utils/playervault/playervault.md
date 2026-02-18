# Player Vault

Per-player vault storage with configurable vault count, size, and title.

## Key Classes

- **`PlayerVaultManager`** -- manages vault inventories per player
- **`PlayerVaultBuilder`** -- DSL builder

## Usage

### Create

```kotlin
val vaults = playerVault {
    maxVaults(5)
    rows(6)
    titleFormat("<gold>Vault #{id}")
}
```

### Open/access

```kotlin
vaults.open(player, vaultId = 0)

val inventory = vaults.getVault(player.uuid, vaultId = 2)
```

### Clear

```kotlin
vaults.clearVault(player.uuid, vaultId = 0)
vaults.clearAll(player.uuid)
```

## API

| Method | Description |
|--------|-------------|
| `open(player, vaultId)` | Open vault inventory GUI for player |
| `getVault(uuid, vaultId)` | Get or create vault inventory |
| `clearVault(uuid, vaultId)` | Clear and remove a specific vault |
| `clearAll(uuid)` | Clear all vaults for a player |
