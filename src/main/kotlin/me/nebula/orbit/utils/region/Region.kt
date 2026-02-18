package me.nebula.orbit.utils.region

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import java.util.concurrent.ConcurrentHashMap

sealed interface Region {
    val name: String
    fun contains(point: Point): Boolean
    fun contains(x: Double, y: Double, z: Double): Boolean
}

data class CuboidRegion(
    override val name: String,
    val min: Pos,
    val max: Pos,
) : Region {

    val sizeX: Double get() = max.x() - min.x()
    val sizeY: Double get() = max.y() - min.y()
    val sizeZ: Double get() = max.z() - min.z()
    val center: Pos get() = Pos((min.x() + max.x()) / 2, (min.y() + max.y()) / 2, (min.z() + max.z()) / 2)
    val volume: Double get() = sizeX * sizeY * sizeZ

    override fun contains(point: Point): Boolean =
        contains(point.x(), point.y(), point.z())

    override fun contains(x: Double, y: Double, z: Double): Boolean =
        x in min.x()..max.x() && y in min.y()..max.y() && z in min.z()..max.z()
}

data class SphereRegion(
    override val name: String,
    val center: Pos,
    val radius: Double,
) : Region {

    val radiusSquared: Double = radius * radius
    val volume: Double get() = (4.0 / 3.0) * Math.PI * radius * radius * radius

    override fun contains(point: Point): Boolean =
        contains(point.x(), point.y(), point.z())

    override fun contains(x: Double, y: Double, z: Double): Boolean {
        val dx = x - center.x()
        val dy = y - center.y()
        val dz = z - center.z()
        return dx * dx + dy * dy + dz * dz <= radiusSquared
    }
}

data class CylinderRegion(
    override val name: String,
    val center: Pos,
    val radius: Double,
    val height: Double,
) : Region {

    val radiusSquared: Double = radius * radius

    override fun contains(point: Point): Boolean =
        contains(point.x(), point.y(), point.z())

    override fun contains(x: Double, y: Double, z: Double): Boolean {
        if (y < center.y() || y > center.y() + height) return false
        val dx = x - center.x()
        val dz = z - center.z()
        return dx * dx + dz * dz <= radiusSquared
    }
}

object RegionManager {

    private val regions = ConcurrentHashMap<String, Region>()

    fun register(region: Region) {
        require(!regions.containsKey(region.name)) { "Region '${region.name}' already exists" }
        regions[region.name] = region
    }

    fun unregister(name: String) = regions.remove(name)

    fun get(name: String): Region? = regions[name]
    fun require(name: String): Region = requireNotNull(regions[name]) { "Region '$name' not found" }

    fun all(): Map<String, Region> = regions.toMap()

    fun regionsAt(point: Point): List<Region> =
        regions.values.filter { it.contains(point) }

    fun isInAnyRegion(point: Point): Boolean =
        regions.values.any { it.contains(point) }

    fun playersInRegion(region: Region, instance: Instance): List<Player> =
        instance.players.filter { region.contains(it.position) }

    fun entitiesInRegion(region: Region, instance: Instance): List<Entity> =
        instance.entities.filter { region.contains(it.position) }
}

fun cuboidRegion(name: String, min: Pos, max: Pos): CuboidRegion {
    val actualMin = Pos(
        minOf(min.x(), max.x()), minOf(min.y(), max.y()), minOf(min.z(), max.z()),
    )
    val actualMax = Pos(
        maxOf(min.x(), max.x()), maxOf(min.y(), max.y()), maxOf(min.z(), max.z()),
    )
    return CuboidRegion(name, actualMin, actualMax)
}

fun sphereRegion(name: String, center: Pos, radius: Double): SphereRegion =
    SphereRegion(name, center, radius)

fun cylinderRegion(name: String, center: Pos, radius: Double, height: Double): CylinderRegion =
    CylinderRegion(name, center, radius, height)
