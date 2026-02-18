package me.nebula.orbit.utils.protection

import me.nebula.orbit.utils.region.Region
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ProtectionFlag {
    BREAK,
    PLACE,
    INTERACT,
    PVP,
    MOB_DAMAGE,
}

sealed interface ProtectionZone {

    val flags: Set<ProtectionFlag>

    fun isProtected(point: Point, player: Player): Boolean

    data class RegionZone(
        val name: String,
        val region: Region,
        override val flags: Set<ProtectionFlag>,
        val whitelist: Set<UUID> = emptySet(),
        val priority: Int = 0,
    ) : ProtectionZone {
        override fun isProtected(point: Point, player: Player): Boolean =
            region.contains(point) && player.uuid !in whitelist
    }

    data class ChunkZone(
        val instance: Instance,
        val chunkX: Int,
        val chunkZ: Int,
        override val flags: Set<ProtectionFlag>,
        val whitelist: Set<UUID> = emptySet(),
    ) : ProtectionZone {
        override fun isProtected(point: Point, player: Player): Boolean {
            val pi = player.instance ?: return false
            return pi === instance &&
                point.blockX() shr 4 == chunkX &&
                point.blockZ() shr 4 == chunkZ &&
                player.uuid !in whitelist
        }
    }

    data class RadiusZone(
        val name: String,
        val center: Point,
        val radius: Double,
        val instance: Instance,
        override val flags: Set<ProtectionFlag>,
        val bypass: (Player) -> Boolean = { false },
    ) : ProtectionZone {
        override fun isProtected(point: Point, player: Player): Boolean {
            val pi = player.instance ?: return false
            return pi === instance &&
                point.distance(center) <= radius &&
                !bypass(player)
        }
    }
}

object ProtectionManager {

    private val zones = ConcurrentHashMap<String, ProtectionZone>()
    private var eventNode: EventNode<*>? = null

    fun install() {
        if (eventNode != null) return
        val node = EventNode.all("orbit-protection")

        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (isBlocked(event.player, event.blockPosition, ProtectionFlag.BREAK)) {
                event.isCancelled = true
            }
        }

        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (isBlocked(event.player, event.blockPosition, ProtectionFlag.PLACE)) {
                event.isCancelled = true
            }
        }

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (isBlocked(event.player, event.blockPosition, ProtectionFlag.INTERACT)) {
                event.isCancelled = true
            }
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun uninstall() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        zones.clear()
    }

    fun protect(key: String, zone: ProtectionZone) {
        zones[key] = zone
    }

    fun unprotect(key: String) {
        zones.remove(key)
    }

    fun isBlocked(player: Player, point: Point, flag: ProtectionFlag): Boolean =
        zones.values
            .filter { flag in it.flags }
            .sortedByDescending { if (it is ProtectionZone.RegionZone) it.priority else 0 }
            .any { it.isProtected(point, player) }

    fun all(): Map<String, ProtectionZone> = zones.toMap()

    fun clearInstance(instance: Instance) {
        zones.entries.removeIf { (_, zone) ->
            when (zone) {
                is ProtectionZone.ChunkZone -> zone.instance === instance
                is ProtectionZone.RadiusZone -> zone.instance === instance
                is ProtectionZone.RegionZone -> false
            }
        }
    }

    fun clear() = zones.clear()
}

class RegionProtectionBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var region: Region? = null
    @PublishedApi internal val flags = mutableSetOf<ProtectionFlag>()
    @PublishedApi internal val whitelist = mutableSetOf<UUID>()
    @PublishedApi internal var priority: Int = 0

    fun region(region: Region) { this.region = region }
    fun flags(vararg f: ProtectionFlag) { flags.addAll(f) }
    fun whitelist(uuid: UUID) { whitelist.add(uuid) }
    fun whitelist(uuids: Collection<UUID>) { whitelist.addAll(uuids) }
    fun priority(p: Int) { priority = p }

    @PublishedApi internal fun build(): ProtectionZone.RegionZone =
        ProtectionZone.RegionZone(
            name = name,
            region = requireNotNull(region) { "Region is required" },
            flags = flags.toSet(),
            whitelist = whitelist.toSet(),
            priority = priority,
        )
}

inline fun protectRegion(name: String, block: RegionProtectionBuilder.() -> Unit) {
    val zone = RegionProtectionBuilder(name).apply(block).build()
    ProtectionManager.install()
    ProtectionManager.protect(name, zone)
}

class ChunkProtectionRuleBuilder @PublishedApi internal constructor(
    private val chunkX: Int,
    private val chunkZ: Int,
) {
    @PublishedApi internal val flags = mutableSetOf<ProtectionFlag>()
    @PublishedApi internal val whitelist = mutableSetOf<UUID>()

    fun flags(vararg f: ProtectionFlag) { flags.addAll(f) }
    fun whitelist(uuid: UUID) { whitelist.add(uuid) }
    fun whitelist(uuids: Collection<UUID>) { whitelist.addAll(uuids) }

    @PublishedApi internal fun build(instance: Instance): ProtectionZone.ChunkZone =
        ProtectionZone.ChunkZone(instance, chunkX, chunkZ, flags.toSet(), whitelist.toSet())
}

class ChunkProtectionBuilder @PublishedApi internal constructor(
    @PublishedApi internal val instance: Instance,
) {

    @PublishedApi internal val zones = mutableListOf<ProtectionZone.ChunkZone>()

    inline fun chunk(chunkX: Int, chunkZ: Int, block: ChunkProtectionRuleBuilder.() -> Unit) {
        zones.add(ChunkProtectionRuleBuilder(chunkX, chunkZ).apply(block).build(instance))
    }

    fun area(fromX: Int, fromZ: Int, toX: Int, toZ: Int, block: ChunkProtectionRuleBuilder.() -> Unit) {
        val minX = minOf(fromX, toX)
        val maxX = maxOf(fromX, toX)
        val minZ = minOf(fromZ, toZ)
        val maxZ = maxOf(fromZ, toZ)
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                zones.add(ChunkProtectionRuleBuilder(x, z).apply(block).build(instance))
            }
        }
    }

    @PublishedApi internal fun apply() {
        ProtectionManager.install()
        zones.forEach { zone ->
            ProtectionManager.protect("chunk_${zone.chunkX}_${zone.chunkZ}_${System.identityHashCode(instance)}", zone)
        }
    }
}

inline fun protectChunk(instance: Instance, block: ChunkProtectionBuilder.() -> Unit) {
    ChunkProtectionBuilder(instance).apply(block).apply()
}

class SpawnProtectionBuilder @PublishedApi internal constructor(
    private val name: String,
    private val center: Point,
    private val radius: Double,
    private val instance: Instance,
) {

    @PublishedApi internal val flags = mutableSetOf(ProtectionFlag.BREAK, ProtectionFlag.PLACE)
    @PublishedApi internal var bypass: (Player) -> Boolean = { false }

    fun flags(vararg f: ProtectionFlag) { flags.clear(); flags.addAll(f) }
    fun allowBreak() { flags.remove(ProtectionFlag.BREAK) }
    fun allowPlace() { flags.remove(ProtectionFlag.PLACE) }
    fun bypass(check: (Player) -> Boolean) { bypass = check }

    @PublishedApi internal fun build(): ProtectionZone.RadiusZone =
        ProtectionZone.RadiusZone(name, center, radius, instance, flags.toSet(), bypass)
}

inline fun protectSpawn(name: String, center: Point, radius: Double, instance: Instance, block: SpawnProtectionBuilder.() -> Unit = {}) {
    val zone = SpawnProtectionBuilder(name, center, radius, instance).apply(block).build()
    ProtectionManager.install()
    ProtectionManager.protect(name, zone)
}
