package me.nebula.orbit.utils.entitymount

import net.minestom.server.entity.Entity
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class MountConfig(
    val speedMultiplier: Double = 1.0,
    val jumpBoost: Double = 0.0,
    val steeringOverride: Boolean = false,
)

data class MountEntry(
    val riderUuid: UUID,
    val vehicleId: Int,
    val config: MountConfig,
)

object EntityMountManager {

    private val mounts = ConcurrentHashMap<UUID, MountEntry>()
    private val vehicleRiders = ConcurrentHashMap<Int, MutableList<UUID>>()

    fun mount(rider: Entity, vehicle: Entity, config: MountConfig = MountConfig()): Boolean {
        if (rider.entityId == vehicle.entityId) return false
        val riderUuid = when (rider) {
            is Player -> rider.uuid
            else -> UUID(0, rider.entityId.toLong())
        }

        dismount(rider)

        vehicle.addPassenger(rider)
        val entry = MountEntry(riderUuid, vehicle.entityId, config)
        mounts[riderUuid] = entry
        vehicleRiders.getOrPut(vehicle.entityId) { mutableListOf() }.add(riderUuid)

        if (config.speedMultiplier != 1.0 && vehicle is LivingEntity) {
            val baseSpeed = vehicle.getAttribute(Attribute.MOVEMENT_SPEED).baseValue
            vehicle.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = baseSpeed * config.speedMultiplier
        }

        return true
    }

    fun dismount(rider: Entity): Boolean {
        val riderUuid = when (rider) {
            is Player -> rider.uuid
            else -> UUID(0, rider.entityId.toLong())
        }
        val entry = mounts.remove(riderUuid) ?: return false
        vehicleRiders[entry.vehicleId]?.remove(riderUuid)
        if (vehicleRiders[entry.vehicleId]?.isEmpty() == true) {
            vehicleRiders.remove(entry.vehicleId)
        }
        rider.vehicle?.removePassenger(rider)
        return true
    }

    fun getMountedVehicle(rider: Entity): Entity? {
        val riderUuid = when (rider) {
            is Player -> rider.uuid
            else -> UUID(0, rider.entityId.toLong())
        }
        return mounts[riderUuid]?.let { rider.vehicle }
    }

    fun getRiders(vehicle: Entity): List<UUID> =
        vehicleRiders[vehicle.entityId]?.toList() ?: emptyList()

    fun isMounted(rider: Entity): Boolean {
        val riderUuid = when (rider) {
            is Player -> rider.uuid
            else -> UUID(0, rider.entityId.toLong())
        }
        return mounts.containsKey(riderUuid)
    }

    fun getMountConfig(rider: Entity): MountConfig? {
        val riderUuid = when (rider) {
            is Player -> rider.uuid
            else -> UUID(0, rider.entityId.toLong())
        }
        return mounts[riderUuid]?.config
    }

    fun mountStack(entities: List<Entity>, config: MountConfig = MountConfig()): Boolean {
        require(entities.size >= 2) { "Need at least 2 entities to create a mount stack" }
        for (i in 0 until entities.size - 1) {
            val rider = entities[i + 1]
            val vehicle = entities[i]
            if (!mount(rider, vehicle, config)) return false
        }
        return true
    }

    fun dismountAll() {
        mounts.keys.toList().forEach { uuid ->
            val entry = mounts[uuid] ?: return@forEach
            mounts.remove(uuid)
        }
        vehicleRiders.clear()
    }

    fun cleanup(uuid: UUID) {
        mounts.remove(uuid)?.let { entry ->
            vehicleRiders[entry.vehicleId]?.remove(uuid)
            if (vehicleRiders[entry.vehicleId]?.isEmpty() == true) {
                vehicleRiders.remove(entry.vehicleId)
            }
        }
    }
}

class MountConfigBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var speedMultiplier: Double = 1.0
    @PublishedApi internal var jumpBoost: Double = 0.0
    @PublishedApi internal var steeringOverride: Boolean = false

    fun speedMultiplier(multiplier: Double) { speedMultiplier = multiplier }
    fun jumpBoost(boost: Double) { jumpBoost = boost }
    fun steeringOverride(enabled: Boolean) { steeringOverride = enabled }

    @PublishedApi internal fun build(): MountConfig = MountConfig(speedMultiplier, jumpBoost, steeringOverride)
}

inline fun mountConfig(block: MountConfigBuilder.() -> Unit): MountConfig =
    MountConfigBuilder().apply(block).build()

fun Player.mountEntity(entity: Entity, config: MountConfig = MountConfig()): Boolean =
    EntityMountManager.mount(this, entity, config)

fun Player.dismountEntity(): Boolean =
    EntityMountManager.dismount(this)

fun Player.getMountedEntity(): Entity? =
    EntityMountManager.getMountedVehicle(this)
