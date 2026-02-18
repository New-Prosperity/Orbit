package me.nebula.orbit.utils.boundary

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Instance
import java.util.concurrent.ConcurrentHashMap

sealed class BoundaryShape {
    data class Cuboid(val minX: Double, val maxX: Double, val minZ: Double, val maxZ: Double) : BoundaryShape()
    data class Circle(val centerX: Double, val centerZ: Double, val radius: Double) : BoundaryShape()
}

data class Boundary(
    val name: String,
    val instance: Instance,
    val shape: BoundaryShape,
    val minY: Double,
    val maxY: Double,
    val onBlocked: (Player) -> Unit,
) {

    fun contains(point: Point): Boolean {
        if (point.y() < minY || point.y() > maxY) return false
        return when (shape) {
            is BoundaryShape.Cuboid -> point.x() in shape.minX..shape.maxX && point.z() in shape.minZ..shape.maxZ
            is BoundaryShape.Circle -> {
                val dx = point.x() - shape.centerX
                val dz = point.z() - shape.centerZ
                dx * dx + dz * dz <= shape.radius * shape.radius
            }
        }
    }
}

object BoundaryManager {

    private val boundaries = ConcurrentHashMap<String, Boundary>()
    private var eventNode: EventNode<*>? = null

    fun start() {
        val node = EventNode.all("boundary-manager")
        node.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val newPos = event.newPosition
            boundaries.values
                .filter { it.instance == instance }
                .forEach { boundary ->
                    if (!boundary.contains(newPos)) {
                        event.isCancelled = true
                        boundary.onBlocked(player)
                        return@addListener
                    }
                }
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun stop() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        boundaries.clear()
    }

    fun register(boundary: Boundary) {
        require(!boundaries.containsKey(boundary.name)) { "Boundary '${boundary.name}' already exists" }
        boundaries[boundary.name] = boundary
    }

    fun unregister(name: String) = boundaries.remove(name)

    fun get(name: String): Boundary? = boundaries[name]

    fun all(): Map<String, Boundary> = boundaries.toMap()

    fun clear() = boundaries.clear()
}

class BoundaryBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var instance: Instance? = null
    @PublishedApi internal var shape: BoundaryShape? = null
    @PublishedApi internal var minY: Double = -64.0
    @PublishedApi internal var maxY: Double = 320.0
    @PublishedApi internal var blockedHandler: (Player) -> Unit = {}

    fun instance(instance: Instance) { this.instance = instance }
    fun minY(y: Double) { minY = y }
    fun maxY(y: Double) { maxY = y }
    fun onBlocked(handler: (Player) -> Unit) { blockedHandler = handler }

    fun cuboid(minX: Double, maxX: Double, minZ: Double, maxZ: Double) {
        shape = BoundaryShape.Cuboid(
            minOf(minX, maxX), maxOf(minX, maxX),
            minOf(minZ, maxZ), maxOf(minZ, maxZ),
        )
    }

    fun circle(centerX: Double, centerZ: Double, radius: Double) {
        require(radius > 0) { "Radius must be positive" }
        shape = BoundaryShape.Circle(centerX, centerZ, radius)
    }

    fun center(x: Double, z: Double) {
        val existing = shape
        if (existing is BoundaryShape.Circle) {
            shape = existing.copy(centerX = x, centerZ = z)
        } else {
            shape = BoundaryShape.Circle(x, z, 50.0)
        }
    }

    fun radiusX(rx: Double) {
        val existing = shape
        when (existing) {
            is BoundaryShape.Cuboid -> shape = existing.copy(
                minX = (existing.minX + existing.maxX) / 2 - rx,
                maxX = (existing.minX + existing.maxX) / 2 + rx,
            )
            is BoundaryShape.Circle -> shape = existing.copy(radius = rx)
            null -> shape = BoundaryShape.Circle(0.0, 0.0, rx)
        }
    }

    fun radiusZ(rz: Double) {
        val existing = shape
        if (existing is BoundaryShape.Cuboid) {
            shape = existing.copy(
                minZ = (existing.minZ + existing.maxZ) / 2 - rz,
                maxZ = (existing.minZ + existing.maxZ) / 2 + rz,
            )
        }
    }

    fun radius(r: Double) {
        require(r > 0) { "Radius must be positive" }
        val existing = shape
        if (existing is BoundaryShape.Circle) {
            shape = existing.copy(radius = r)
        } else {
            shape = BoundaryShape.Circle(0.0, 0.0, r)
        }
    }

    @PublishedApi internal fun build(): Boundary {
        val inst = requireNotNull(instance) { "Boundary '$name' requires an instance" }
        val s = requireNotNull(shape) { "Boundary '$name' requires a shape (cuboid or circle)" }
        return Boundary(name, inst, s, minY, maxY, blockedHandler)
    }
}

inline fun boundary(name: String, block: BoundaryBuilder.() -> Unit): Boundary {
    val boundary = BoundaryBuilder(name).apply(block).build()
    BoundaryManager.register(boundary)
    return boundary
}
