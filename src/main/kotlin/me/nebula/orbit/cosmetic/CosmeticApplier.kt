package me.nebula.orbit.cosmetic

import me.nebula.orbit.utils.customcontent.armor.CustomArmorRegistry
import me.nebula.orbit.utils.customcontent.armor.equipFullSet
import me.nebula.orbit.utils.particle.ParticleShapeRenderer
import me.nebula.orbit.utils.particle.spawnParticle
import me.nebula.orbit.utils.particle.showParticleShape
import me.nebula.orbit.utils.particle.ParticleShape
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.particle.Particle
import java.util.UUID

object CosmeticApplier {

    fun applyArmorSkin(player: Player, cosmeticId: String, level: Int = 1) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val armorId = resolved["armorId"] ?: return
        CustomArmorRegistry[armorId]?.equipFullSet(player)
    }

    fun clearArmorSkin(player: Player) {
        player.setEquipment(net.minestom.server.entity.EquipmentSlot.HELMET, net.minestom.server.item.ItemStack.AIR)
        player.setEquipment(net.minestom.server.entity.EquipmentSlot.CHESTPLATE, net.minestom.server.item.ItemStack.AIR)
        player.setEquipment(net.minestom.server.entity.EquipmentSlot.LEGGINGS, net.minestom.server.item.ItemStack.AIR)
        player.setEquipment(net.minestom.server.entity.EquipmentSlot.BOOTS, net.minestom.server.item.ItemStack.AIR)
    }

    fun playKillEffect(instance: Instance, position: Pos, cosmeticId: String, level: Int = 1, ownerUuid: UUID) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val particle = resolveParticle(resolved["particle"]) ?: return
        val shape = resolved["shape"] ?: "sphere"
        val radius = resolved["radius"]?.toDoubleOrNull() ?: 1.5
        val density = resolved["density"]?.toIntOrNull() ?: 12
        val particleShape = buildShape(shape, position, radius, density, particle)
        forEachParticleViewer(instance, ownerUuid) { player ->
            ParticleShapeRenderer.render(player, particleShape)
        }
    }

    fun playWinEffect(instance: Instance, winner: Player, cosmeticId: String, level: Int = 1) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val particle = resolveParticle(resolved["particle"]) ?: return
        val shape = resolved["shape"] ?: "helix"
        val radius = resolved["radius"]?.toDoubleOrNull() ?: 1.0
        val density = resolved["density"]?.toIntOrNull() ?: 15
        val particleShape = buildShape(shape, winner.position, radius, density, particle, height = 3.0, turns = 4)
        forEachParticleViewer(instance, winner.uuid) { player ->
            ParticleShapeRenderer.render(player, particleShape)
        }
    }

    fun spawnTrailParticle(instance: Instance, position: Pos, cosmeticId: String, level: Int = 1, ownerUuid: UUID) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val particle = resolveParticle(resolved["particle"]) ?: return
        val count = resolved["count"]?.toIntOrNull() ?: 3
        val spread = resolved["spread"]?.toFloatOrNull() ?: 0.1f
        forEachParticleViewer(instance, ownerUuid) { player ->
            player.spawnParticle(particle, position, count = count, spread = spread, speed = 0.02f)
        }
    }

    fun spawnProjectileTrailParticle(instance: Instance, position: Pos, cosmeticId: String, level: Int = 1, ownerUuid: UUID) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val particle = resolveParticle(resolved["particle"]) ?: return
        val count = resolved["count"]?.toIntOrNull() ?: 1
        val spread = resolved["spread"]?.toFloatOrNull() ?: 0.05f
        forEachParticleViewer(instance, ownerUuid) { player ->
            player.spawnParticle(particle, position, count = count, spread = spread, speed = 0.01f)
        }
    }

    fun playSpawnEffect(instance: Instance, position: Pos, cosmeticId: String, level: Int = 1, ownerUuid: UUID) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val particle = resolveParticle(resolved["particle"]) ?: return
        val shape = resolved["shape"] ?: "helix"
        val radius = resolved["radius"]?.toDoubleOrNull() ?: 1.0
        val density = resolved["density"]?.toIntOrNull() ?: 10
        val particleShape = buildShape(shape, position, radius, density, particle, height = 2.5, turns = 3)
        forEachParticleViewer(instance, ownerUuid) { player ->
            ParticleShapeRenderer.render(player, particleShape)
        }
    }

    fun playDeathEffect(instance: Instance, position: Pos, cosmeticId: String, level: Int = 1, ownerUuid: UUID) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val particle = resolveParticle(resolved["particle"]) ?: return
        val shape = resolved["shape"] ?: "sphere"
        val radius = resolved["radius"]?.toDoubleOrNull() ?: 1.5
        val density = resolved["density"]?.toIntOrNull() ?: 12
        val particleShape = buildShape(shape, position, radius, density, particle)
        forEachParticleViewer(instance, ownerUuid) { player ->
            ParticleShapeRenderer.render(player, particleShape)
        }
    }

    fun spawnAuraParticles(instance: Instance, position: Pos, cosmeticId: String, level: Int = 1, ownerUuid: UUID) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val particle = resolveParticle(resolved["particle"]) ?: return
        val count = resolved["count"]?.toIntOrNull() ?: 2
        val spread = resolved["spread"]?.toFloatOrNull() ?: 0.4f
        forEachParticleViewer(instance, ownerUuid) { player ->
            player.spawnParticle(particle, position.add(0.0, 1.0, 0.0), count = count, spread = spread, speed = 0.01f)
        }
    }

    fun spawnGadgetParticle(instance: Instance, position: Pos, particle: Particle, ownerUuid: UUID, count: Int = 20, spread: Float = 0.5f, speed: Float = 0.1f) {
        forEachParticleViewer(instance, ownerUuid) { player ->
            player.spawnParticle(particle, position, count = count, spread = spread, speed = speed)
        }
    }

    fun spawnGadgetShape(instance: Instance, ownerUuid: UUID, shape: ParticleShape) {
        forEachParticleViewer(instance, ownerUuid) { player ->
            ParticleShapeRenderer.render(player, shape)
        }
    }

    private inline fun forEachParticleViewer(instance: Instance, ownerUuid: UUID, action: (Player) -> Unit) {
        for (player in instance.players) {
            if (CosmeticVisibility.shouldShowParticles(player, ownerUuid)) {
                action(player)
            }
        }
    }

    private fun buildShape(shape: String, position: Pos, radius: Double, density: Int, particle: Particle, height: Double = 2.0, turns: Int = 3): ParticleShape =
        when (shape) {
            "sphere" -> ParticleShape.Sphere(position, radius, density, particle)
            "helix" -> ParticleShape.Helix(position, radius, height, turns, particle)
            "circle" -> ParticleShape.Circle(position, radius, density * 2, particle)
            else -> ParticleShape.Sphere(position, radius, density, particle)
        }

    private fun resolveParticle(name: String?): Particle? {
        if (name == null) return null
        return runCatching { Particle.fromKey("minecraft:${name.lowercase()}") }.getOrNull()
    }
}
