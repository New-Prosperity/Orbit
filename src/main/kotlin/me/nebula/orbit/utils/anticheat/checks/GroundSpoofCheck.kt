package me.nebula.orbit.utils.anticheat.checks

import me.nebula.gravity.config.ConfigStore
import me.nebula.gravity.config.NetworkConfig
import me.nebula.orbit.utils.anticheat.AntiCheat
import me.nebula.orbit.utils.anticheat.AntiCheatCheck
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private data class GroundSpoofState(val falseClaimStreak: Int = 0)

object GroundSpoofCheck : AntiCheatCheck {

    override val id: String = "groundspoof"

    private const val MAX_FALSE_CLAIMS = 8
    private const val WEIGHT = 2

    private val states = ConcurrentHashMap<UUID, GroundSpoofState>()

    override fun install(node: EventNode<in Event>) {
        node.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return@addListener
            if (player.vehicle != null) return@addListener
            if (player.isFlying) return@addListener
            if (!ConfigStore.get(NetworkConfig.AC_CHECK_GROUNDSPOOF_ENABLED)) return@addListener

            val pos = event.newPosition
            val instance = player.instance ?: return@addListener
            val blockBelow = instance.getBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ())
            val airBelow = blockBelow == Block.AIR || blockBelow == Block.CAVE_AIR || blockBelow == Block.VOID_AIR
            val claimsGround = player.isOnGround

            val streak = if (claimsGround && airBelow) {
                val updated = states.compute(player.uuid) { _, existing ->
                    GroundSpoofState((existing?.falseClaimStreak ?: 0) + 1)
                }
                updated?.falseClaimStreak ?: 0
            } else {
                states.remove(player.uuid)
                0
            }

            if (streak >= MAX_FALSE_CLAIMS) {
                AntiCheat.flag(player.uuid, "groundspoof", WEIGHT, AntiCheat.movementFlagThreshold, AntiCheat.movementKickThreshold)
                states.remove(player.uuid)
            }
        }
    }

    override fun cleanup(uuid: UUID) {
        states.remove(uuid)
    }

    override fun clearAll() {
        states.clear()
    }
}
