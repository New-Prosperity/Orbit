package me.nebula.orbit.utils.playerdata

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PlayerSnapshot(
    val uuid: UUID,
    val position: Pos,
    val health: Float,
    val food: Int,
    val foodSaturation: Float,
    val gameMode: GameMode,
    val level: Int,
    val inventory: Map<Int, ItemStack>,
    val timestamp: Long = System.currentTimeMillis(),
)

object PlayerDataManager {

    private val snapshots = ConcurrentHashMap<UUID, MutableList<PlayerSnapshot>>()

    fun capture(player: Player): PlayerSnapshot {
        val items = buildMap {
            for (i in 0 until player.inventory.size) {
                val item = player.inventory.getItemStack(i)
                if (!item.isAir) put(i, item)
            }
        }
        val snapshot = PlayerSnapshot(
            uuid = player.uuid,
            position = player.position,
            health = player.health,
            food = player.food,
            foodSaturation = player.foodSaturation,
            gameMode = player.gameMode,
            level = player.level,
            inventory = items,
        )
        snapshots.getOrPut(player.uuid) { mutableListOf() }.add(snapshot)
        return snapshot
    }

    fun restore(player: Player, snapshot: PlayerSnapshot) {
        player.teleport(snapshot.position)
        player.health = snapshot.health
        player.food = snapshot.food
        player.foodSaturation = snapshot.foodSaturation
        player.gameMode = snapshot.gameMode
        player.level = snapshot.level
        player.inventory.clear()
        for ((slot, item) in snapshot.inventory) {
            player.inventory.setItemStack(slot, item)
        }
    }

    fun getLatest(uuid: UUID): PlayerSnapshot? =
        snapshots[uuid]?.lastOrNull()

    fun getAll(uuid: UUID): List<PlayerSnapshot> =
        snapshots[uuid]?.toList() ?: emptyList()

    fun clear(uuid: UUID) {
        snapshots.remove(uuid)
    }
}

fun Player.captureData(): PlayerSnapshot = PlayerDataManager.capture(this)

fun Player.restoreData(snapshot: PlayerSnapshot) = PlayerDataManager.restore(this, snapshot)

fun Player.restoreLatest(): Boolean {
    val snapshot = PlayerDataManager.getLatest(uuid) ?: return false
    PlayerDataManager.restore(this, snapshot)
    return true
}
