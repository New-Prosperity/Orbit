package me.nebula.orbit.utils.customcontent.event

import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import me.nebula.orbit.utils.scheduler.repeat
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerCancelDiggingEvent
import net.minestom.server.event.player.PlayerFinishDiggingEvent
import net.minestom.server.event.player.PlayerStartDiggingEvent
import net.minestom.server.network.packet.server.play.StopSoundPacket
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CustomBlockSoundTicker {

    private const val FLAGS_SOURCE_AND_SOUND: Byte = 0x03

    private val miningCustom: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    @Volatile private var task: Task? = null

    private val STEP_SOUNDS = listOf(
        "block.wood.step",
        "block.bamboo_wood.step",
        "block.stone.step",
        "block.tripwire.attach",
    )
    private val HIT_SOUNDS = listOf(
        "block.wood.hit",
        "block.bamboo_wood.hit",
        "block.stone.hit",
    )
    private val FALL_SOUNDS = listOf(
        "block.wood.fall",
        "block.bamboo_wood.fall",
        "block.stone.fall",
    )

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerStartDiggingEvent::class.java) { event ->
            if (CustomBlockRegistry.fromVanillaBlock(event.block) != null) {
                miningCustom.add(event.player.uuid)
            }
        }
        eventNode.addListener(PlayerCancelDiggingEvent::class.java) { event ->
            miningCustom.remove(event.player.uuid)
        }
        eventNode.addListener(PlayerFinishDiggingEvent::class.java) { event ->
            miningCustom.remove(event.player.uuid)
        }

        if (task == null) {
            task = repeat(4) { tick() }
        }
    }

    fun uninstall() {
        task?.cancel()
        task = null
        miningCustom.clear()
    }

    private fun tick() {
        val online = MinecraftServer.getConnectionManager().onlinePlayers
        for (player in online) {
            if (player.isRemoved) continue
            handlePlayer(player)
        }
    }

    private fun handlePlayer(player: Player) {
        val instance = player.instance ?: return
        val pos = player.position
        val below = instance.getBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ())
        val standingOnCustom = CustomBlockRegistry.fromVanillaBlock(below) != null
        val mining = miningCustom.contains(player.uuid)

        if (standingOnCustom) {
            sendStop(player, STEP_SOUNDS)
            sendStop(player, FALL_SOUNDS)
        }
        if (mining) {
            sendStop(player, HIT_SOUNDS)
        }
    }

    private fun sendStop(player: Player, sounds: List<String>) {
        for (name in sounds) {
            player.sendPacket(StopSoundPacket(FLAGS_SOURCE_AND_SOUND, Sound.Source.BLOCK, name))
        }
    }
}
