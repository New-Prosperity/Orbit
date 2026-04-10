package me.nebula.orbit.cosmetic

import net.minestom.server.entity.Player
import java.util.UUID

class CosmeticContext(val listener: CosmeticListener) {

    val pets = PetManager()
    val companions = CompanionManager()
    val gadgets = GadgetManager()
    val gravestones = GravestoneManager()
    val mounts = CosmeticMountManager()
    val auras = AuraManager(listener)

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
