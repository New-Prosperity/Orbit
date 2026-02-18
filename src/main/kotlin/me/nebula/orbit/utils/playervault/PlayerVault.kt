package me.nebula.orbit.utils.playervault

import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.entity.Player
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerVaultManager @PublishedApi internal constructor(
    private val maxVaults: Int,
    private val rows: Int,
    private val titleFormat: String,
) {

    private val vaults = ConcurrentHashMap<Long, Inventory>()
    private val miniMessage = MiniMessage.miniMessage()

    fun open(player: Player, vaultId: Int = 0) {
        require(vaultId in 0 until maxVaults) { "Vault ID must be in 0..<$maxVaults" }
        val inventory = getVault(player.uuid, vaultId)
        player.openInventory(inventory)
    }

    fun getVault(uuid: UUID, vaultId: Int): Inventory {
        require(vaultId in 0 until maxVaults) { "Vault ID must be in 0..<$maxVaults" }
        val key = packKey(uuid, vaultId)
        return vaults.computeIfAbsent(key) {
            val title = titleFormat
                .replace("{id}", (vaultId + 1).toString())
                .replace("{player}", uuid.toString())
            Inventory(rowsToType(rows), miniMessage.deserialize(title))
        }
    }

    fun clearVault(uuid: UUID, vaultId: Int) {
        vaults.remove(packKey(uuid, vaultId))?.clear()
    }

    fun clearAll(uuid: UUID) {
        for (id in 0 until maxVaults) {
            vaults.remove(packKey(uuid, id))?.clear()
        }
    }

    private fun packKey(uuid: UUID, vaultId: Int): Long =
        uuid.mostSignificantBits xor uuid.leastSignificantBits xor vaultId.toLong()
}

private fun rowsToType(rows: Int): InventoryType = when (rows) {
    1 -> InventoryType.CHEST_1_ROW
    2 -> InventoryType.CHEST_2_ROW
    3 -> InventoryType.CHEST_3_ROW
    4 -> InventoryType.CHEST_4_ROW
    5 -> InventoryType.CHEST_5_ROW
    6 -> InventoryType.CHEST_6_ROW
    else -> error("Invalid row count: $rows (must be 1-6)")
}

class PlayerVaultBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var maxVaults: Int = 3
    @PublishedApi internal var rows: Int = 3
    @PublishedApi internal var titleFormat: String = "<gray>Vault #{id}"

    fun maxVaults(count: Int) {
        require(count > 0) { "Max vaults must be positive" }
        maxVaults = count
    }

    fun rows(count: Int) {
        require(count in 1..6) { "Rows must be 1-6" }
        rows = count
    }

    fun titleFormat(format: String) { titleFormat = format }

    @PublishedApi internal fun build(): PlayerVaultManager =
        PlayerVaultManager(maxVaults, rows, titleFormat)
}

inline fun playerVault(builder: PlayerVaultBuilder.() -> Unit): PlayerVaultManager =
    PlayerVaultBuilder().apply(builder).build()
