package me.nebula.orbit.cosmetic

import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CosmeticContext(val listener: CosmeticListener) {

    val pets = PetManager()
    val companions = CompanionManager()
    val gadgets = GadgetManager()
    val gravestones = GravestoneManager()
    val mounts = CosmeticMountManager()
    val auras = AuraManager(listener)
    val trackedProjectiles = ConcurrentHashMap<Int, TrackedProjectile>()

    data class TrackedProjectile(val entity: Entity, val shooterUuid: UUID)

    fun trackProjectile(entity: Entity, shooter: Player) {
        trackedProjectiles[entity.entityId] = TrackedProjectile(entity, shooter.uuid)
    }

    fun untrackProjectile(entityId: Int) {
        trackedProjectiles.remove(entityId)
    }

    fun install() {
        auras.install()
        companions.install()
        pets.install()
        gadgets.install()
        gravestones.install()
        mounts.install()
    }

    fun uninstall() {
        auras.uninstall()
        companions.uninstall()
        pets.uninstall()
        gadgets.uninstall()
        gravestones.uninstall()
        mounts.uninstall()
        trackedProjectiles.clear()
    }

    fun despawnAll(playerId: UUID) {
        pets.despawn(playerId)
        companions.despawn(playerId)
        mounts.despawn(playerId)
    }

    fun despawnAll(player: Player) {
        despawnAll(player.uuid)
        gadgets.unequip(player)
    }
}
