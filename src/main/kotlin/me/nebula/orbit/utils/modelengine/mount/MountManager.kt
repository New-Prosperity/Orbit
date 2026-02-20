package me.nebula.orbit.utils.modelengine.mount

import me.nebula.orbit.utils.modelengine.behavior.MountBehavior
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerPacketEvent
import net.minestom.server.network.packet.client.play.ClientInputPacket
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class MountSession(
    val player: Player,
    val modeledEntity: ModeledEntity,
    val mountBehavior: MountBehavior,
    var controller: MountController,
    var input: MountInput = MountInput(),
)

object MountManager {

    private val sessions = ConcurrentHashMap<UUID, MountSession>()
    private var tickTask: Task? = null
    private var eventNode: EventNode<*>? = null

    fun mount(player: Player, modeledEntity: ModeledEntity, mountBehavior: MountBehavior, controller: MountController) {
        dismount(player)
        mountBehavior.mount(player)
        sessions[player.uuid] = MountSession(player, modeledEntity, mountBehavior, controller)
        ensureInstalled()
    }

    fun dismount(player: Player) {
        val session = sessions.remove(player.uuid) ?: return
        session.mountBehavior.dismount()
    }

    fun session(player: Player): MountSession? = sessions[player.uuid]

    fun isMounted(player: Player): Boolean = sessions.containsKey(player.uuid)

    fun evictPlayer(uuid: UUID) {
        val session = sessions.remove(uuid) ?: return
        session.mountBehavior.dismount()
    }

    fun install() {
        if (tickTask != null) return

        val node = EventNode.all("model-mount-manager")
        node.addListener(PlayerPacketEvent::class.java) { event ->
            val packet = event.packet
            if (packet is ClientInputPacket) {
                val session = sessions[event.player.uuid] ?: return@addListener
                val forward = when {
                    packet.forward() -> 1f
                    packet.backward() -> -1f
                    else -> 0f
                }
                val sideways = when {
                    packet.left() -> 1f
                    packet.right() -> -1f
                    else -> 0f
                }
                session.input = MountInput(
                    forward = forward,
                    sideways = sideways,
                    jump = packet.jump(),
                    sneak = packet.shift(),
                )
                if (session.input.sneak) {
                    return@addListener
                }
            }
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(1))
            .schedule()
    }

    fun uninstall() {
        tickTask?.cancel()
        tickTask = null
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        sessions.values.forEach { it.mountBehavior.dismount() }
        sessions.clear()
    }

    private fun ensureInstalled() {
        if (tickTask == null) install()
    }

    private fun tick() {
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val (_, session) = iterator.next()
            if (session.player.isRemoved || session.modeledEntity.owner.isRemoved || session.input.sneak) {
                session.mountBehavior.dismount()
                iterator.remove()
                continue
            }
            session.controller.tick(session.modeledEntity, session.player, session.input)
        }
    }
}
