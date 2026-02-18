package me.nebula.orbit.utils.worldborder

import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.instance.WorldBorder

class ManagedWorldBorder(
    private val instance: Instance,
    private var diameter: Double,
    private var centerX: Double = 0.0,
    private var centerZ: Double = 0.0,
    private var warningDistance: Int = 5,
    private var warningTime: Int = 15,
) {

    @Volatile
    var currentDiameter: Double = diameter
        private set

    fun apply() {
        instance.setWorldBorder(WorldBorder(currentDiameter, centerX, centerZ, warningDistance, warningTime))
    }

    fun setDiameter(diameter: Double) {
        this.diameter = diameter
        this.currentDiameter = diameter
        instance.setWorldBorder(WorldBorder(diameter, centerX, centerZ, warningDistance, warningTime))
    }

    fun setCenter(x: Double, z: Double) {
        centerX = x
        centerZ = z
        instance.setWorldBorder(WorldBorder(currentDiameter, centerX, centerZ, warningDistance, warningTime))
    }

    fun shrinkTo(targetDiameter: Double, transitionSeconds: Double) {
        currentDiameter = targetDiameter
        this.diameter = targetDiameter
        instance.setWorldBorder(WorldBorder(targetDiameter, centerX, centerZ, warningDistance, warningTime), transitionSeconds)
    }

    fun expandTo(targetDiameter: Double, transitionSeconds: Double) {
        currentDiameter = targetDiameter
        this.diameter = targetDiameter
        instance.setWorldBorder(WorldBorder(targetDiameter, centerX, centerZ, warningDistance, warningTime), transitionSeconds)
    }

    fun isOutside(pos: Pos): Boolean {
        val halfDiameter = currentDiameter / 2
        return pos.x() < centerX - halfDiameter || pos.x() > centerX + halfDiameter ||
            pos.z() < centerZ - halfDiameter || pos.z() > centerZ + halfDiameter
    }

    fun isInside(pos: Pos): Boolean = !isOutside(pos)
}

class WorldBorderBuilder @PublishedApi internal constructor(private val instance: Instance) {

    @PublishedApi internal var diameter = 1000.0
    @PublishedApi internal var centerX = 0.0
    @PublishedApi internal var centerZ = 0.0
    @PublishedApi internal var warningDistance = 5
    @PublishedApi internal var warningTime = 15

    fun diameter(diameter: Double) { this.diameter = diameter }
    fun center(x: Double, z: Double) { centerX = x; centerZ = z }
    fun warningDistance(blocks: Int) { warningDistance = blocks }
    fun warningTime(seconds: Int) { warningTime = seconds }

    @PublishedApi internal fun build(): ManagedWorldBorder = ManagedWorldBorder(
        instance, diameter, centerX, centerZ, warningDistance, warningTime,
    ).also { it.apply() }
}

inline fun Instance.managedWorldBorder(block: WorldBorderBuilder.() -> Unit): ManagedWorldBorder =
    WorldBorderBuilder(this).apply(block).build()
