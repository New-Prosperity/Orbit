package me.nebula.orbit.utils.gametest

import me.nebula.orbit.mode.game.GamePhase
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.potion.Potion
import java.util.UUID
import net.minestom.server.entity.GameMode as MinecraftGameMode

data class PlayerSnapshot(
    val uuid: UUID,
    val username: String,
    val position: Pos,
    val health: Float,
    val food: Int,
    val saturation: Float,
    val gameMode: MinecraftGameMode,
    val inventory: Map<Int, ItemStack>,
    val equipment: Map<EquipmentSlot, ItemStack>,
    val effects: List<Potion>,
)

data class TrackerSnapshot(
    val alive: Set<UUID>,
    val spectating: Set<UUID>,
    val disconnected: Set<UUID>,
    val kills: Map<UUID, Int>,
    val deaths: Map<UUID, Int>,
    val scores: Map<UUID, Double>,
    val teams: Map<UUID, String>,
)

data class GameStateSnapshot(
    val phase: GamePhase?,
    val tick: Long,
    val players: Map<UUID, PlayerSnapshot>,
    val trackerState: TrackerSnapshot?,
    val timestamp: Long = System.currentTimeMillis(),
)

data class SnapshotDiff(
    val phaseChanged: Boolean,
    val playersJoined: Set<UUID>,
    val playersLeft: Set<UUID>,
    val playersDied: Set<UUID>,
    val healthChanges: Map<UUID, Pair<Float, Float>>,
    val killChanges: Map<UUID, Pair<Int, Int>>,
    val scoreChanges: Map<UUID, Pair<Double, Double>>,
)

fun capturePlayerSnapshot(player: Player): PlayerSnapshot {
    val inv = buildMap {
        for (slot in 0 until player.inventory.size) {
            val stack = player.inventory.getItemStack(slot)
            if (!stack.isAir) put(slot, stack)
        }
    }
    val equipment = buildMap {
        for (slot in EquipmentSlot.entries) {
            val item = player.getEquipment(slot)
            if (!item.isAir) put(slot, item)
        }
    }
    val effects = player.activeEffects.map { it.potion() }
    return PlayerSnapshot(
        uuid = player.uuid,
        username = player.username,
        position = player.position,
        health = player.health,
        food = player.food,
        saturation = player.foodSaturation,
        gameMode = player.gameMode,
        inventory = inv,
        equipment = equipment,
        effects = effects,
    )
}

fun diffSnapshots(before: GameStateSnapshot, after: GameStateSnapshot): SnapshotDiff {
    val beforeAlive = before.trackerState?.alive ?: emptySet()
    val afterAlive = after.trackerState?.alive ?: emptySet()
    val beforeUuids = before.players.keys
    val afterUuids = after.players.keys

    val healthChanges = buildMap {
        for (uuid in beforeUuids.intersect(afterUuids)) {
            val bHealth = before.players.getValue(uuid).health
            val aHealth = after.players.getValue(uuid).health
            if (bHealth != aHealth) put(uuid, bHealth to aHealth)
        }
    }

    val beforeKills = before.trackerState?.kills ?: emptyMap()
    val afterKills = after.trackerState?.kills ?: emptyMap()
    val killChanges = buildMap {
        for (uuid in (beforeKills.keys + afterKills.keys)) {
            val bk = beforeKills[uuid] ?: 0
            val ak = afterKills[uuid] ?: 0
            if (bk != ak) put(uuid, bk to ak)
        }
    }

    val beforeScores = before.trackerState?.scores ?: emptyMap()
    val afterScores = after.trackerState?.scores ?: emptyMap()
    val scoreChanges = buildMap {
        for (uuid in (beforeScores.keys + afterScores.keys)) {
            val bs = beforeScores[uuid] ?: 0.0
            val as_ = afterScores[uuid] ?: 0.0
            if (bs != as_) put(uuid, bs to as_)
        }
    }

    return SnapshotDiff(
        phaseChanged = before.phase != after.phase,
        playersJoined = afterUuids - beforeUuids,
        playersLeft = beforeUuids - afterUuids,
        playersDied = beforeAlive - afterAlive,
        healthChanges = healthChanges,
        killChanges = killChanges,
        scoreChanges = scoreChanges,
    )
}
