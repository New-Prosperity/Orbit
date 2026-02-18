package me.nebula.orbit.utils.particle

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ParticleEffect @PublishedApi internal constructor(
    private val particle: Particle,
    private val count: Int,
    private val offsetX: Float,
    private val offsetY: Float,
    private val offsetZ: Float,
    private val speed: Float,
) {

    fun spawn(instance: Instance, position: Pos) {
        val packet = ParticlePacket(particle, position.x(), position.y(), position.z(), offsetX, offsetY, offsetZ, speed, count)
        instance.sendGroupedPacket(packet)
    }

    fun spawn(player: Player, position: Pos) {
        val packet = ParticlePacket(particle, position.x(), position.y(), position.z(), offsetX, offsetY, offsetZ, speed, count)
        player.sendPacket(packet)
    }

    fun circle(instance: Instance, center: Pos, radius: Double, points: Int = 20) {
        val step = 2 * PI / points
        for (i in 0 until points) {
            val x = center.x() + radius * cos(step * i)
            val z = center.z() + radius * sin(step * i)
            spawn(instance, Pos(x, center.y(), z))
        }
    }

    fun line(instance: Instance, from: Pos, to: Pos, density: Double = 0.5) {
        val dx = to.x() - from.x()
        val dy = to.y() - from.y()
        val dz = to.z() - from.z()
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        val steps = (dist / density).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            spawn(instance, Pos(from.x() + dx * t, from.y() + dy * t, from.z() + dz * t))
        }
    }
}

class ParticleBuilder @PublishedApi internal constructor(private val particle: Particle) {

    var count: Int = 1
    var offsetX: Float = 0f
    var offsetY: Float = 0f
    var offsetZ: Float = 0f
    var speed: Float = 0f

    fun offset(x: Float, y: Float, z: Float) {
        offsetX = x; offsetY = y; offsetZ = z
    }

    @PublishedApi internal fun build(): ParticleEffect =
        ParticleEffect(particle, count, offsetX, offsetY, offsetZ, speed)
}

inline fun particleEffect(particle: Particle, block: ParticleBuilder.() -> Unit = {}): ParticleEffect =
    ParticleBuilder(particle).apply(block).build()

fun Instance.spawnBlockBreakParticle(position: Point, count: Int = 10) {
    val packet = ParticlePacket(
        Particle.POOF, false, false,
        position.x(), position.y() + 0.5, position.z(),
        0.25f, 0.25f, 0.25f,
        0.05f, count,
    )
    sendGroupedPacket(packet)
}

fun Instance.spawnParticleAt(particle: Particle, position: Point, count: Int = 1, spread: Float = 0f, speed: Float = 0f) {
    val packet = ParticlePacket(
        particle, false, false,
        position.x(), position.y(), position.z(),
        spread, spread, spread,
        speed, count,
    )
    sendGroupedPacket(packet)
}

fun Player.spawnParticle(particle: Particle, position: Point, count: Int = 1, spread: Float = 0f, speed: Float = 0f) {
    val packet = ParticlePacket(
        particle, false, false,
        position.x(), position.y(), position.z(),
        spread, spread, spread,
        speed, count,
    )
    sendPacket(packet)
}

fun Instance.spawnParticleLine(particle: Particle, from: Point, to: Point, density: Double = 0.5, count: Int = 1) {
    val dx = to.x() - from.x()
    val dy = to.y() - from.y()
    val dz = to.z() - from.z()
    val dist = from.distance(to)
    val steps = (dist / density).toInt().coerceAtLeast(1)
    for (i in 0..steps) {
        val t = i.toDouble() / steps
        val pos = Pos(from.x() + dx * t, from.y() + dy * t, from.z() + dz * t)
        spawnParticleAt(particle, pos, count)
    }
}

fun Instance.spawnParticleCircle(particle: Particle, center: Point, radius: Double, points: Int = 20, count: Int = 1) {
    for (i in 0 until points) {
        val angle = 2.0 * PI * i / points
        val pos = Pos(
            center.x() + radius * cos(angle),
            center.y(),
            center.z() + radius * sin(angle),
        )
        spawnParticleAt(particle, pos, count)
    }
}

sealed interface ParticleShape {

    data class Circle(
        val center: Pos,
        val radius: Double,
        val points: Int,
        val particle: Particle,
    ) : ParticleShape

    data class Sphere(
        val center: Pos,
        val radius: Double,
        val density: Int,
        val particle: Particle,
    ) : ParticleShape

    data class Helix(
        val base: Pos,
        val radius: Double,
        val height: Double,
        val turns: Int,
        val particle: Particle,
    ) : ParticleShape

    data class Line(
        val from: Pos,
        val to: Pos,
        val density: Double,
        val particle: Particle,
    ) : ParticleShape

    data class Cuboid(
        val min: Pos,
        val max: Pos,
        val density: Double,
        val particle: Particle,
    ) : ParticleShape
}

object ParticleShapeRenderer {

    fun render(instance: Instance, shape: ParticleShape) {
        computePoints(shape).forEach { (pos, particle) ->
            instance.sendGroupedPacket(ParticlePacket(particle, pos.x(), pos.y(), pos.z(), 0f, 0f, 0f, 0f, 1))
        }
    }

    fun render(player: Player, shape: ParticleShape) {
        computePoints(shape).forEach { (pos, particle) ->
            player.sendPacket(ParticlePacket(particle, pos.x(), pos.y(), pos.z(), 0f, 0f, 0f, 0f, 1))
        }
    }

    fun computePoints(shape: ParticleShape): List<Pair<Pos, Particle>> = when (shape) {
        is ParticleShape.Circle -> {
            val step = 2.0 * PI / shape.points
            (0 until shape.points).map { i ->
                val angle = step * i
                Pos(
                    shape.center.x() + shape.radius * cos(angle),
                    shape.center.y(),
                    shape.center.z() + shape.radius * sin(angle),
                ) to shape.particle
            }
        }

        is ParticleShape.Sphere -> {
            val goldenAngle = PI * (3.0 - sqrt(5.0))
            val totalPoints = shape.density * shape.density
            (0 until totalPoints).map { i ->
                val y = 1.0 - (i.toDouble() / (totalPoints - 1)) * 2.0
                val radiusAtY = sqrt(1.0 - y * y)
                val theta = goldenAngle * i
                Pos(
                    shape.center.x() + cos(theta) * radiusAtY * shape.radius,
                    shape.center.y() + y * shape.radius,
                    shape.center.z() + sin(theta) * radiusAtY * shape.radius,
                ) to shape.particle
            }
        }

        is ParticleShape.Helix -> {
            val totalPoints = shape.turns * 20
            (0 until totalPoints).map { i ->
                val t = i.toDouble() / totalPoints
                val angle = 2.0 * PI * shape.turns * t
                Pos(
                    shape.base.x() + shape.radius * cos(angle),
                    shape.base.y() + shape.height * t,
                    shape.base.z() + shape.radius * sin(angle),
                ) to shape.particle
            }
        }

        is ParticleShape.Line -> {
            val dx = shape.to.x() - shape.from.x()
            val dy = shape.to.y() - shape.from.y()
            val dz = shape.to.z() - shape.from.z()
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            val steps = (dist / shape.density).toInt().coerceAtLeast(1)
            (0..steps).map { i ->
                val t = i.toDouble() / steps
                Pos(
                    shape.from.x() + dx * t,
                    shape.from.y() + dy * t,
                    shape.from.z() + dz * t,
                ) to shape.particle
            }
        }

        is ParticleShape.Cuboid -> {
            val minX = minOf(shape.min.x(), shape.max.x())
            val minY = minOf(shape.min.y(), shape.max.y())
            val minZ = minOf(shape.min.z(), shape.max.z())
            val maxX = maxOf(shape.min.x(), shape.max.x())
            val maxY = maxOf(shape.min.y(), shape.max.y())
            val maxZ = maxOf(shape.min.z(), shape.max.z())
            val step = shape.density.coerceAtLeast(0.1)
            generateEdgePoints(minX, minY, minZ, maxX, maxY, maxZ, step).map { it to shape.particle }
        }
    }

    private fun generateEdgePoints(
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
        step: Double,
    ): List<Pos> {
        val points = mutableListOf<Pos>()
        fun line(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double) {
            val dx = x2 - x1; val dy = y2 - y1; val dz = z2 - z1
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            val steps = (dist / step).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i.toDouble() / steps
                points += Pos(x1 + dx * t, y1 + dy * t, z1 + dz * t)
            }
        }
        line(minX, minY, minZ, maxX, minY, minZ)
        line(minX, minY, minZ, minX, maxY, minZ)
        line(minX, minY, minZ, minX, minY, maxZ)
        line(maxX, maxY, maxZ, minX, maxY, maxZ)
        line(maxX, maxY, maxZ, maxX, minY, maxZ)
        line(maxX, maxY, maxZ, maxX, maxY, minZ)
        line(minX, maxY, minZ, maxX, maxY, minZ)
        line(minX, maxY, minZ, minX, maxY, maxZ)
        line(maxX, minY, minZ, maxX, maxY, minZ)
        line(maxX, minY, minZ, maxX, minY, maxZ)
        line(minX, minY, maxZ, maxX, minY, maxZ)
        line(minX, minY, maxZ, minX, maxY, maxZ)
        return points
    }
}

class ParticleShapeBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val shapes = mutableListOf<ParticleShape>()

    fun circle(center: Pos, radius: Double, points: Int = 20, particle: Particle = Particle.FLAME) {
        shapes += ParticleShape.Circle(center, radius, points, particle)
    }

    fun sphere(center: Pos, radius: Double, density: Int = 10, particle: Particle = Particle.FLAME) {
        shapes += ParticleShape.Sphere(center, radius, density, particle)
    }

    fun helix(base: Pos, radius: Double, height: Double, turns: Int = 3, particle: Particle = Particle.FLAME) {
        shapes += ParticleShape.Helix(base, radius, height, turns, particle)
    }

    fun line(from: Pos, to: Pos, density: Double = 0.5, particle: Particle = Particle.FLAME) {
        shapes += ParticleShape.Line(from, to, density, particle)
    }

    fun cuboid(min: Pos, max: Pos, density: Double = 0.5, particle: Particle = Particle.FLAME) {
        shapes += ParticleShape.Cuboid(min, max, density, particle)
    }
}

inline fun Player.showParticleShape(block: ParticleShapeBuilder.() -> Unit) {
    val builder = ParticleShapeBuilder().apply(block)
    builder.shapes.forEach { ParticleShapeRenderer.render(this, it) }
}

inline fun Instance.showParticleShape(block: ParticleShapeBuilder.() -> Unit) {
    val builder = ParticleShapeBuilder().apply(block)
    builder.shapes.forEach { ParticleShapeRenderer.render(this, it) }
}
