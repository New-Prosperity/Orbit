package me.nebula.orbit.utils.entityformation

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

sealed interface FormationPattern {

    data class Circle(
        val radius: Double,
        val count: Int,
    ) : FormationPattern

    data class Line(
        val spacing: Double,
        val direction: Vec,
    ) : FormationPattern

    data class Grid(
        val rows: Int,
        val cols: Int,
        val spacing: Double,
    ) : FormationPattern

    data class Wedge(
        val angle: Double,
        val spacing: Double,
    ) : FormationPattern
}

class Formation internal constructor(
    private val pattern: FormationPattern,
    private val yawOffset: Float,
) {

    @Volatile private var animationTask: Task? = null

    fun apply(entities: List<Entity>, center: Pos) {
        val positions = computePositions(center, entities.size)
        entities.forEachIndexed { index, entity ->
            if (index < positions.size) {
                entity.teleport(positions[index])
            }
        }
    }

    fun animate(entities: List<Entity>, center: Pos, speed: Double): Task {
        animationTask?.cancel()
        var angle = 0.0
        val task = MinecraftServer.getSchedulerManager().buildTask {
            angle += speed
            val rotatedCenter = center.withYaw((center.yaw() + angle).toFloat())
            val positions = computePositions(rotatedCenter, entities.size)
            entities.forEachIndexed { index, entity ->
                if (index < positions.size && !entity.isRemoved) {
                    entity.teleport(positions[index])
                }
            }
        }.repeat(TaskSchedule.tick(1)).schedule()
        animationTask = task
        return task
    }

    fun stopAnimation() {
        animationTask?.cancel()
        animationTask = null
    }

    fun computePositions(center: Pos, entityCount: Int): List<Pos> = when (pattern) {
        is FormationPattern.Circle -> {
            val step = 2.0 * PI / entityCount.coerceAtLeast(1)
            (0 until entityCount).map { i ->
                val angle = step * i + Math.toRadians(yawOffset.toDouble())
                val x = center.x() + pattern.radius * cos(angle)
                val z = center.z() + pattern.radius * sin(angle)
                Pos(x, center.y(), z, center.yaw(), center.pitch())
            }
        }

        is FormationPattern.Line -> {
            val dir = pattern.direction.normalize()
            val startOffset = -(entityCount - 1) / 2.0
            (0 until entityCount).map { i ->
                val offset = (startOffset + i) * pattern.spacing
                Pos(
                    center.x() + dir.x() * offset,
                    center.y() + dir.y() * offset,
                    center.z() + dir.z() * offset,
                    center.yaw(),
                    center.pitch(),
                )
            }
        }

        is FormationPattern.Grid -> {
            val positions = mutableListOf<Pos>()
            val startRow = -(pattern.rows - 1) / 2.0
            val startCol = -(pattern.cols - 1) / 2.0
            val yawRad = Math.toRadians(yawOffset.toDouble())
            var count = 0
            for (row in 0 until pattern.rows) {
                for (col in 0 until pattern.cols) {
                    if (count >= entityCount) break
                    val localX = (startCol + col) * pattern.spacing
                    val localZ = (startRow + row) * pattern.spacing
                    val rotatedX = localX * cos(yawRad) - localZ * sin(yawRad)
                    val rotatedZ = localX * sin(yawRad) + localZ * cos(yawRad)
                    positions += Pos(
                        center.x() + rotatedX,
                        center.y(),
                        center.z() + rotatedZ,
                        center.yaw(),
                        center.pitch(),
                    )
                    count++
                }
            }
            positions
        }

        is FormationPattern.Wedge -> {
            val positions = mutableListOf<Pos>()
            val halfAngle = Math.toRadians(pattern.angle / 2.0)
            val baseYaw = Math.toRadians(yawOffset.toDouble())
            if (entityCount == 1) {
                positions += center
            } else {
                for (i in 0 until entityCount) {
                    val t = if (entityCount > 1) i.toDouble() / (entityCount - 1) else 0.5
                    val angle = baseYaw + halfAngle - (2.0 * halfAngle * t)
                    val distance = pattern.spacing * (i / 2 + 1)
                    val x = center.x() + distance * sin(angle)
                    val z = center.z() + distance * cos(angle)
                    positions += Pos(x, center.y(), z, center.yaw(), center.pitch())
                }
            }
            positions
        }
    }
}

class FormationBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var pattern: FormationPattern = FormationPattern.Circle(radius = 3.0, count = 8)
    @PublishedApi internal var yawOffset: Float = 0f

    fun circle(radius: Double, count: Int) { pattern = FormationPattern.Circle(radius, count) }
    fun line(spacing: Double, direction: Vec = Vec(0.0, 0.0, 1.0)) { pattern = FormationPattern.Line(spacing, direction) }
    fun grid(rows: Int, cols: Int, spacing: Double) { pattern = FormationPattern.Grid(rows, cols, spacing) }
    fun wedge(angle: Double, spacing: Double) { pattern = FormationPattern.Wedge(angle, spacing) }
    fun yawOffset(degrees: Float) { yawOffset = degrees }

    @PublishedApi internal fun build(): Formation = Formation(pattern, yawOffset)
}

inline fun entityFormation(block: FormationBuilder.() -> Unit): Formation =
    FormationBuilder().apply(block).build()
