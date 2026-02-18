package me.nebula.orbit.utils.portal

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import java.util.concurrent.ConcurrentHashMap

data class PortalRegion(
    val minX: Double, val minY: Double, val minZ: Double,
    val maxX: Double, val maxY: Double, val maxZ: Double,
) {
    fun contains(pos: Pos): Boolean =
        pos.x() in minX..maxX && pos.y() in minY..maxY && pos.z() in minZ..maxZ
}

data class PortalDestination(
    val instance: Instance,
    val position: Pos,
)

data class Portal(
    val name: String,
    val sourceInstance: Instance,
    val region: PortalRegion,
    val destination: PortalDestination,
    val cooldownMs: Long = 3000,
)

private val PORTAL_COOLDOWN_TAG = Tag.Long("utils:portal:cooldown").defaultValue(0L)

object PortalManager {

    private val portals = ConcurrentHashMap<String, Portal>()
    private val eventNode = EventNode.all("portal-manager")
    private var installed = false

    fun register(portal: Portal) {
        portals[portal.name] = portal
        ensureInstalled()
    }

    fun unregister(name: String) = portals.remove(name)

    operator fun get(name: String): Portal? = portals[name]

    fun all(): Map<String, Portal> = portals.toMap()

    fun clear() {
        portals.clear()
        uninstall()
    }

    fun install() {
        if (installed) return
        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val now = System.currentTimeMillis()

            val lastUse = player.getTag(PORTAL_COOLDOWN_TAG)
            if (now - lastUse < 1000) return@addListener

            portals.values.forEach { portal ->
                if (portal.sourceInstance != instance) return@forEach
                if (!portal.region.contains(event.newPosition)) return@forEach

                if (now - lastUse < portal.cooldownMs) return@forEach
                player.setTag(PORTAL_COOLDOWN_TAG, now)

                val dest = portal.destination
                if (dest.instance == instance) {
                    player.teleport(dest.position)
                } else {
                    player.setInstance(dest.instance, dest.position)
                }
                return@addListener
            }
        }

        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        installed = true
    }

    fun uninstall() {
        if (!installed) return
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
        installed = false
    }

    private fun ensureInstalled() {
        if (!installed) install()
    }
}

class PortalBuilder(val name: String) {
    lateinit var sourceInstance: Instance
    var region: PortalRegion? = null
    lateinit var destinationInstance: Instance
    var destinationPosition: Pos = Pos(0.0, 64.0, 0.0)
    var cooldownMs: Long = 3000

    fun region(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) {
        region = PortalRegion(minX, minY, minZ, maxX, maxY, maxZ)
    }

    fun build(): Portal = Portal(
        name = name,
        sourceInstance = sourceInstance,
        region = requireNotNull(region) { "Portal region must be set" },
        destination = PortalDestination(destinationInstance, destinationPosition),
        cooldownMs = cooldownMs,
    )
}

inline fun portal(name: String, block: PortalBuilder.() -> Unit): Portal =
    PortalBuilder(name).apply(block).build()
