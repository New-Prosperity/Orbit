package me.nebula.orbit.mode.game.battleroyale.zone

import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.instance.WorldBorder

interface ZoneBorderDriver {
    fun snapTo(centerX: Double, centerZ: Double, diameter: Double)
    fun pushLerpStep(centerX: Double, centerZ: Double, diameter: Double, stepDurationSeconds: Double)
    fun isOutside(x: Double, z: Double): Boolean
    val currentDiameter: Double
    val currentCenterX: Double
    val currentCenterZ: Double
}

class ManagedZoneBorderDriver(
    private val instance: Instance,
    private val warningBlocks: Int = 5,
    private val warningSeconds: Int = 15,
) : ZoneBorderDriver {

    override var currentDiameter: Double = 0.0
        private set
    override var currentCenterX: Double = 0.0
        private set
    override var currentCenterZ: Double = 0.0
        private set

    override fun snapTo(centerX: Double, centerZ: Double, diameter: Double) {
        currentCenterX = centerX
        currentCenterZ = centerZ
        currentDiameter = diameter
        instance.setWorldBorder(WorldBorder(diameter, centerX, centerZ, warningBlocks, warningSeconds))
    }

    override fun pushLerpStep(centerX: Double, centerZ: Double, diameter: Double, stepDurationSeconds: Double) {
        currentCenterX = centerX
        currentCenterZ = centerZ
        currentDiameter = diameter
        instance.setWorldBorder(
            WorldBorder(diameter, centerX, centerZ, warningBlocks, warningSeconds),
            stepDurationSeconds,
        )
    }

    override fun isOutside(x: Double, z: Double): Boolean {
        val half = currentDiameter / 2
        return x < currentCenterX - half || x > currentCenterX + half ||
            z < currentCenterZ - half || z > currentCenterZ + half
    }
}

fun ZoneBorderDriver.isOutside(pos: Pos): Boolean = isOutside(pos.x(), pos.z())
