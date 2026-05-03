package me.nebula.orbit.utils.mapgen.planet

import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class OreClusterShape(
    val id: String,
    val offsets: List<Triple<Int, Int, Int>>,
)

object OreClusterShapeRegistry {

    private val shapes = ConcurrentHashMap<String, OreClusterShape>()

    fun register(shape: OreClusterShape) {
        shapes[shape.id.lowercase()] = shape
    }

    operator fun get(id: String): OreClusterShape? = shapes[id.lowercase()]

    fun all(): Collection<OreClusterShape> = shapes.values

    fun clear() = shapes.clear()

    fun registerBuiltins() {
        register(OreClusterShape("single", listOf(Triple(0, 0, 0))))
        register(OreClusterShape("pair", listOf(Triple(0, 0, 0), Triple(0, 1, 0))))
        register(OreClusterShape("vertical_vein", listOf(
            Triple(0, 0, 0), Triple(0, 1, 0), Triple(0, 2, 0), Triple(0, 3, 0),
            Triple(1, 1, 0), Triple(0, 1, 1),
        )))
        register(OreClusterShape("horizontal_vein", listOf(
            Triple(0, 0, 0), Triple(1, 0, 0), Triple(2, 0, 0), Triple(-1, 0, 0),
            Triple(0, 0, 1), Triple(1, 0, 1),
        )))
        register(OreClusterShape("cube_2", listOf(
            Triple(0, 0, 0), Triple(1, 0, 0), Triple(0, 0, 1), Triple(1, 0, 1),
            Triple(0, 1, 0), Triple(1, 1, 0), Triple(0, 1, 1), Triple(1, 1, 1),
        )))
        register(OreClusterShape("plus", listOf(
            Triple(0, 0, 0), Triple(1, 0, 0), Triple(-1, 0, 0),
            Triple(0, 0, 1), Triple(0, 0, -1), Triple(0, 1, 0), Triple(0, -1, 0),
        )))
        register(OreClusterShape("blob", listOf(
            Triple(0, 0, 0), Triple(1, 0, 0), Triple(-1, 0, 0),
            Triple(0, 1, 0), Triple(0, -1, 0),
            Triple(0, 0, 1), Triple(0, 0, -1),
            Triple(1, 1, 0), Triple(-1, -1, 0),
        )))
    }

    fun amorphous(rng: Random, size: Int): List<Triple<Int, Int, Int>> {
        val out = ArrayList<Triple<Int, Int, Int>>(size)
        for (i in 0 until size) {
            out += Triple(rng.nextInt(3) - 1, rng.nextInt(3) - 1, rng.nextInt(3) - 1)
        }
        return out
    }
}
