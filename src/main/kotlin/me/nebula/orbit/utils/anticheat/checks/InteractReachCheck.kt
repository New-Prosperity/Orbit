package me.nebula.orbit.utils.anticheat.checks

import me.nebula.gravity.property.NetworkProperties
import me.nebula.gravity.property.PropertyStore
import me.nebula.orbit.utils.anticheat.AntiCheat
import me.nebula.orbit.utils.anticheat.AntiCheatCheck
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent

object InteractReachCheck : AntiCheatCheck {

    override val id: String = "interact_reach"

    private const val PLACE_REACH = 6.5
    private const val BREAK_REACH = 6.5
    private const val INTERACT_REACH = 6.5
    private const val WEIGHT = 2

    override fun install(node: EventNode<in Event>) {
        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            check(event.player, event.blockPosition.blockX(), event.blockPosition.blockY(), event.blockPosition.blockZ(), PLACE_REACH, "place_reach")
        }
        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            check(event.player, event.blockPosition.blockX(), event.blockPosition.blockY(), event.blockPosition.blockZ(), BREAK_REACH, "break_reach")
        }
        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            check(event.player, event.blockPosition.blockX(), event.blockPosition.blockY(), event.blockPosition.blockZ(), INTERACT_REACH, "interact_reach")
        }
    }

    private fun check(player: net.minestom.server.entity.Player, bx: Int, by: Int, bz: Int, threshold: Double, checkType: String) {
        if (player.gameMode == GameMode.CREATIVE) return
        if (!PropertyStore[NetworkProperties.AC_CHECK_INTERACT_REACH_ENABLED]) return

        val eye = player.position.add(0.0, player.eyeHeight, 0.0)
        val dx = (bx + 0.5) - eye.x()
        val dy = (by + 0.5) - eye.y()
        val dz = (bz + 0.5) - eye.z()
        val distSq = dx * dx + dy * dy + dz * dz
        if (distSq > threshold * threshold) {
            AntiCheat.flag(player.uuid, checkType, WEIGHT, AntiCheat.combatFlagThreshold, AntiCheat.combatKickThreshold)
        }
    }
}
