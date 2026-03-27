package me.nebula.orbit.utils.particle

import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.timer.Task
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AnimatedParticleShape(
    val particle: Particle,
    val count: Int,
    val basePoints: List<Vec>,
    val rotationSpeed: Double = 0.0,
    val expansionSpeed: Double = 0.0,
    val riseSpeed: Double = 0.0,
) {

    private var currentRotation = 0.0
    private var currentScale = 1.0
    private var currentYOffset = 0.0

    fun tick() {
        currentRotation += rotationSpeed
        currentScale += expansionSpeed
        currentYOffset += riseSpeed
    }

    fun reset() {
        currentRotation = 0.0
        currentScale = 1.0
        currentYOffset = 0.0
    }

    fun render(instance: Instance, origin: Pos) {
        val packets = transformedPackets(origin)
        packets.forEach { instance.sendGroupedPacket(it) }
    }

    fun render(player: Player, origin: Pos) {
        val packets = transformedPackets(origin)
        packets.forEach { player.sendPacket(it) }
    }

    private fun transformedPackets(origin: Pos): List<ParticlePacket> {
        val cosR = cos(currentRotation)
        val sinR = sin(currentRotation)
        return basePoints.map { point ->
            val scaledX = point.x() * currentScale
            val scaledZ = point.z() * currentScale
            val rotatedX = scaledX * cosR - scaledZ * sinR
            val rotatedZ = scaledX * sinR + scaledZ * cosR
            val scaledY = point.y() * currentScale
            ParticlePacket(
                particle,
                origin.x() + rotatedX,
                origin.y() + scaledY + currentYOffset,
                origin.z() + rotatedZ,
                0f, 0f, 0f, 0f, count,
            )
        }
    }
}

class AnimatedCircleBuilder @PublishedApi internal constructor() {

    private var particle: Particle = Particle.FLAME
    private var radius: Double = 1.0
    private var points: Int = 20
    private var count: Int = 1
    private var rotateSpeed: Double = 0.0
    private var expansionSpeed: Double = 0.0
    private var riseSpeed: Double = 0.0

    fun particle(particle: Particle) { this.particle = particle }
    fun radius(radius: Double) { this.radius = radius }
    fun points(points: Int) { this.points = points }
    fun count(count: Int) { this.count = count }
    fun rotateSpeed(speed: Double) { this.rotateSpeed = speed }
    fun expansionSpeed(speed: Double) { this.expansionSpeed = speed }
    fun riseSpeed(speed: Double) { this.riseSpeed = speed }

    @PublishedApi internal fun build(): AnimatedParticleShape {
        val step = 2.0 * PI / points
        val basePoints = (0 until points).map { i ->
            val angle = step * i
            Vec(radius * cos(angle), 0.0, radius * sin(angle))
        }
        return AnimatedParticleShape(
            particle = particle,
            count = count,
            basePoints = basePoints,
            rotationSpeed = rotateSpeed,
            expansionSpeed = expansionSpeed,
            riseSpeed = riseSpeed,
        )
    }
}

class AnimatedHelixBuilder @PublishedApi internal constructor() {

    private var particle: Particle = Particle.FLAME
    private var radius: Double = 1.0
    private var height: Double = 3.0
    private var turns: Int = 3
    private var count: Int = 1
    private var rotateSpeed: Double = 0.0
    private var expansionSpeed: Double = 0.0
    private var riseSpeed: Double = 0.0

    fun particle(particle: Particle) { this.particle = particle }
    fun radius(radius: Double) { this.radius = radius }
    fun height(height: Double) { this.height = height }
    fun turns(turns: Int) { this.turns = turns }
    fun count(count: Int) { this.count = count }
    fun rotateSpeed(speed: Double) { this.rotateSpeed = speed }
    fun expansionSpeed(speed: Double) { this.expansionSpeed = speed }
    fun riseSpeed(speed: Double) { this.riseSpeed = speed }

    @PublishedApi internal fun build(): AnimatedParticleShape {
        val totalPoints = turns * 20
        val basePoints = (0 until totalPoints).map { i ->
            val t = i.toDouble() / totalPoints
            val angle = 2.0 * PI * turns * t
            Vec(radius * cos(angle), height * t, radius * sin(angle))
        }
        return AnimatedParticleShape(
            particle = particle,
            count = count,
            basePoints = basePoints,
            rotationSpeed = rotateSpeed,
            expansionSpeed = expansionSpeed,
            riseSpeed = riseSpeed,
        )
    }
}

class AnimatedSphereBuilder @PublishedApi internal constructor() {

    private var particle: Particle = Particle.FLAME
    private var radius: Double = 1.0
    private var density: Int = 10
    private var count: Int = 1
    private var rotateSpeed: Double = 0.0
    private var expansionSpeed: Double = 0.0
    private var riseSpeed: Double = 0.0

    fun particle(particle: Particle) { this.particle = particle }
    fun radius(radius: Double) { this.radius = radius }
    fun density(density: Int) { this.density = density }
    fun count(count: Int) { this.count = count }
    fun rotateSpeed(speed: Double) { this.rotateSpeed = speed }
    fun expansionSpeed(speed: Double) { this.expansionSpeed = speed }
    fun riseSpeed(speed: Double) { this.riseSpeed = speed }

    @PublishedApi internal fun build(): AnimatedParticleShape {
        val goldenAngle = PI * (3.0 - sqrt(5.0))
        val totalPoints = density * density
        val basePoints = (0 until totalPoints).map { i ->
            val phi = if (totalPoints <= 1) 0.5 else i.toDouble() / (totalPoints - 1)
            val y = 1.0 - phi * 2.0
            val radiusAtY = sqrt(1.0 - y * y)
            val theta = goldenAngle * i
            Vec(cos(theta) * radiusAtY * radius, y * radius, sin(theta) * radiusAtY * radius)
        }
        return AnimatedParticleShape(
            particle = particle,
            count = count,
            basePoints = basePoints,
            rotationSpeed = rotateSpeed,
            expansionSpeed = expansionSpeed,
            riseSpeed = riseSpeed,
        )
    }
}

class ParticleAttachment private constructor(
    private val entity: Entity,
    private val shape: AnimatedParticleShape,
    private val task: Task,
) {

    fun detach() {
        task.cancel()
        shape.reset()
    }

    companion object {
        fun attach(entity: Entity, shape: AnimatedParticleShape): ParticleAttachment {
            val task = repeat(1) {
                val instance = entity.instance ?: return@repeat
                shape.tick()
                shape.render(instance, entity.position)
            }
            return ParticleAttachment(entity, shape, task)
        }
    }
}

fun Entity.attachParticle(shape: AnimatedParticleShape): ParticleAttachment =
    ParticleAttachment.attach(this, shape)

inline fun animatedCircle(block: AnimatedCircleBuilder.() -> Unit): AnimatedParticleShape =
    AnimatedCircleBuilder().apply(block).build()

inline fun animatedHelix(block: AnimatedHelixBuilder.() -> Unit): AnimatedParticleShape =
    AnimatedHelixBuilder().apply(block).build()

inline fun animatedSphere(block: AnimatedSphereBuilder.() -> Unit): AnimatedParticleShape =
    AnimatedSphereBuilder().apply(block).build()
