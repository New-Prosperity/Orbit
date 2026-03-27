package me.nebula.orbit.cosmetic

import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.model.StandaloneModelOwner
import me.nebula.orbit.utils.modelengine.model.standAloneModel
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class ActiveCompanion(
    val owner: StandaloneModelOwner,
    val cosmeticId: String,
    val level: Int,
)

object CompanionManager {

    private val companions = ConcurrentHashMap<UUID, ActiveCompanion>()
    private val tickCounter = AtomicLong(0L)
    private var task: Task? = null
    private var eventNode: EventNode<*>? = null

    fun install() {
        task = repeat(1) { tick() }
        val node = EventNode.all("companion-manager")
        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            despawn(event.player.uuid)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun uninstall() {
        task?.cancel()
        task = null
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        val iterator = companions.entries.iterator()
        while (iterator.hasNext()) {
            val (_, companion) = iterator.next()
            companion.owner.remove()
            iterator.remove()
        }
    }

    fun spawn(player: Player, cosmeticId: String, level: Int) {
        despawn(player.uuid)
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val modelId = resolved["modelId"] ?: return
        if (ModelEngine.blueprintOrNull(modelId) == null) return
        val scale = resolved["scale"]?.toFloatOrNull() ?: 1.0f
        val hasCustomIdle = resolved["idleAnimation"] != null

        val companionPos = companionPosition(player.position, tickCounter.get())
        val model = standAloneModel(companionPos) {
            model(modelId, autoPlayIdle = !hasCustomIdle) { scale(scale) }
        }

        val idleAnim = resolved["idleAnimation"]
        if (idleAnim != null) {
            val modeled = ModelEngine.modeledEntity(model)
            modeled?.models?.values?.firstOrNull()?.playAnimation(idleAnim, lerpIn = 0.2f, lerpOut = 0.2f, speed = 1.0f)
        }

        model.show(player)
        companions[player.uuid] = ActiveCompanion(model, cosmeticId, level)
    }

    fun despawn(playerId: UUID) {
        companions.remove(playerId)?.owner?.remove()
    }

    fun isActive(playerId: UUID): Boolean = companions.containsKey(playerId)

    private fun tick() {
        val tick = tickCounter.incrementAndGet()
        val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
        val playersByUuid = onlinePlayers.associateBy { it.uuid }

        val iterator = companions.entries.iterator()
        while (iterator.hasNext()) {
            val (uuid, companion) = iterator.next()
            val player = playersByUuid[uuid]
            if (player == null) {
                companion.owner.remove()
                iterator.remove()
                continue
            }

            companion.owner.position = companionPosition(player.position, tick)

            val modeled = ModelEngine.modeledEntity(companion.owner) ?: continue
            if (player.uuid !in modeled.viewers) {
                modeled.show(player)
            }
            for (nearby in player.instance?.players ?: emptyList()) {
                if (nearby.uuid == uuid) continue
                val inRange = nearby.position.distance(player.position) < 48.0
                val shouldShow = inRange && CosmeticVisibility.shouldShowModel(nearby, uuid)
                if (shouldShow && nearby.uuid !in modeled.viewers) {
                    modeled.show(nearby)
                } else if (!shouldShow && nearby.uuid in modeled.viewers) {
                    modeled.hide(nearby)
                }
            }
        }
    }

    private fun companionPosition(playerPos: Pos, tick: Long): Pos =
        playerPos.add(0.8, 2.0 + kotlin.math.sin(tick * 0.1) * 0.15, 0.0)
}
