package me.nebula.orbit.utils.entitybuilder

import me.nebula.orbit.utils.particle.spawnParticleXyz
import net.minestom.server.particle.Particle
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

sealed interface TelegraphShape {
    data class Ring(val radius: Double, val points: Int = 24) : TelegraphShape
    data class FilledCircle(val radius: Double, val rings: Int = 4, val pointsPerRing: Int = 16) : TelegraphShape
    data class Cone(val length: Double, val angleDegrees: Double, val rings: Int = 5, val arcPoints: Int = 12) : TelegraphShape
    data class LineForward(val length: Double, val density: Double = 0.4) : TelegraphShape
    data class Annulus(val innerRadius: Double, val outerRadius: Double, val rings: Int = 3, val points: Int = 24) : TelegraphShape
}

class TelegraphExecutor(
    private val particle: Particle,
    private val durationTicks: Int,
    private val shape: TelegraphShape,
    private val yOffset: Double = 0.1,
) : BehaviorExecutor {

    private var remaining = 0

    override fun onStart(entity: SmartEntity) {
        remaining = durationTicks
    }

    override fun execute(entity: SmartEntity): Boolean {
        if (--remaining < 0) return false
        val instance = entity.instance ?: return false
        val pos = entity.position
        val cx = pos.x()
        val cy = pos.y() + yOffset
        val cz = pos.z()

        when (val s = shape) {
            is TelegraphShape.Ring -> {
                val step = 2.0 * PI / s.points
                for (i in 0 until s.points) {
                    val a = step * i
                    instance.spawnParticleXyz(particle, cx + s.radius * cos(a), cy, cz + s.radius * sin(a))
                }
            }
            is TelegraphShape.FilledCircle -> {
                for (r in 1..s.rings) {
                    val radius = s.radius * (r.toDouble() / s.rings)
                    val step = 2.0 * PI / s.pointsPerRing
                    for (i in 0 until s.pointsPerRing) {
                        val a = step * i
                        instance.spawnParticleXyz(particle, cx + radius * cos(a), cy, cz + radius * sin(a))
                    }
                }
            }
            is TelegraphShape.Annulus -> {
                for (r in 0 until s.rings) {
                    val t = if (s.rings <= 1) 0.0 else r.toDouble() / (s.rings - 1)
                    val radius = s.innerRadius + (s.outerRadius - s.innerRadius) * t
                    val step = 2.0 * PI / s.points
                    for (i in 0 until s.points) {
                        val a = step * i
                        instance.spawnParticleXyz(particle, cx + radius * cos(a), cy, cz + radius * sin(a))
                    }
                }
            }
            is TelegraphShape.Cone -> {
                val facing = pos.direction()
                val baseAngle = atan2(facing.z(), facing.x())
                val halfAngle = Math.toRadians(s.angleDegrees / 2.0)
                val arcStep = (halfAngle * 2.0) / (s.arcPoints - 1).coerceAtLeast(1)
                for (r in 1..s.rings) {
                    val radius = s.length * (r.toDouble() / s.rings)
                    for (i in 0 until s.arcPoints) {
                        val a = baseAngle - halfAngle + arcStep * i
                        instance.spawnParticleXyz(particle, cx + radius * cos(a), cy, cz + radius * sin(a))
                    }
                }
            }
            is TelegraphShape.LineForward -> {
                val facing = pos.direction()
                val len = s.length
                val steps = (len / s.density).toInt().coerceAtLeast(1)
                for (i in 1..steps) {
                    val t = i.toDouble() / steps
                    instance.spawnParticleXyz(particle, cx + facing.x() * len * t, cy, cz + facing.z() * len * t)
                }
            }
        }
        return true
    }
}

fun telegraph(particle: Particle, durationTicks: Int, shape: TelegraphShape): BehaviorExecutor =
    TelegraphExecutor(particle, durationTicks, shape)
