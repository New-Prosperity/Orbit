package me.nebula.orbit.cosmetic

import me.nebula.orbit.utils.customcontent.armor.CustomArmorRegistry
import me.nebula.orbit.utils.customcontent.armor.equipFullSet
import me.nebula.orbit.utils.particle.ParticleShapeRenderer
import me.nebula.orbit.utils.particle.spawnParticle
import me.nebula.orbit.utils.particle.ParticleShape
import me.nebula.orbit.utils.sound.playSound
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.minestom.server.color.Color
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.HitAnimationPacket
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

    fun playKillEffect(
        instance: Instance,
        position: Pos,
        cosmeticId: String,
        level: Int = 1,
        ownerUuid: UUID,
        killer: Player? = null,
        weaponMaterial: String? = null,
    ) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val particle = resolveParticle(resolved) ?: return
        val shape = resolved["shape"] ?: "sphere"
        val radius = resolved["radius"]?.toDoubleOrNull() ?: 1.5
        val density = resolved["density"]?.toIntOrNull() ?: 12
        val particleShape = buildShape(shape, position, radius, density, particle)
        forEachParticleViewer(instance, ownerUuid) { player ->
            ParticleShapeRenderer.render(player, particleShape)
        }
        playCosmeticSound(instance, position, resolved, ownerUuid)
        WeaponEffects.resolve(weaponMaterial)?.let { weaponParticle ->
            forEachParticleViewer(instance, ownerUuid) { player ->
                player.spawnParticle(weaponParticle, position.add(0.0, 1.0, 0.0), count = 12, spread = 0.4f, speed = 0.15f)
            }
        }
        killer?.let { sendScreenShake(it) }
    }

    private fun sendScreenShake(killer: Player) {
        repeat(2) { i ->
            val yaw = if (i == 0) 0f else 180f
            killer.sendPacket(HitAnimationPacket(killer.entityId, yaw))
        }
    }

    fun playWinEffect(instance: Instance, winner: Player, cosmeticId: String, level: Int = 1) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val particle = resolveParticle(resolved) ?: return
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
        val particle = resolveParticle(resolved) ?: return
        val count = resolved["count"]?.toIntOrNull() ?: 3
        val spread = resolved["spread"]?.toFloatOrNull() ?: 0.1f
        forEachParticleViewer(instance, ownerUuid) { player ->
            player.spawnParticle(particle, position, count = count, spread = spread, speed = 0.02f)
        }
    }

    fun spawnProjectileTrailParticle(instance: Instance, position: Pos, cosmeticId: String, level: Int = 1, ownerUuid: UUID) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val particle = resolveParticle(resolved) ?: return
        val count = resolved["count"]?.toIntOrNull() ?: 1
        val spread = resolved["spread"]?.toFloatOrNull() ?: 0.05f
        forEachParticleViewer(instance, ownerUuid) { player ->
            player.spawnParticle(particle, position, count = count, spread = spread, speed = 0.01f)
        }
    }

    fun playSpawnEffect(instance: Instance, position: Pos, cosmeticId: String, level: Int = 1, ownerUuid: UUID) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val particle = resolveParticle(resolved) ?: return
        val shape = resolved["shape"] ?: "helix"
        val radius = resolved["radius"]?.toDoubleOrNull() ?: 1.0
        val density = resolved["density"]?.toIntOrNull() ?: 10
        val particleShape = buildShape(shape, position, radius, density, particle, height = 2.5, turns = 3)
        forEachParticleViewer(instance, ownerUuid) { player ->
            ParticleShapeRenderer.render(player, particleShape)
        }
        playCosmeticSound(instance, position, resolved, ownerUuid)
    }

    fun playDeathEffect(instance: Instance, position: Pos, cosmeticId: String, level: Int = 1, ownerUuid: UUID) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val particle = resolveParticle(resolved) ?: return
        val shape = resolved["shape"] ?: "sphere"
        val radius = resolved["radius"]?.toDoubleOrNull() ?: 1.5
        val density = resolved["density"]?.toIntOrNull() ?: 12
        val particleShape = buildShape(shape, position, radius, density, particle)
        forEachParticleViewer(instance, ownerUuid) { player ->
            ParticleShapeRenderer.render(player, particleShape)
        }
        playCosmeticSound(instance, position, resolved, ownerUuid)
    }

    fun spawnAuraParticles(instance: Instance, position: Pos, cosmeticId: String, level: Int = 1, ownerUuid: UUID) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val particle = resolveParticle(resolved) ?: return
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

    private fun playCosmeticSound(instance: Instance, position: Pos, resolved: Map<String, String>, ownerUuid: UUID) {
        val soundId = resolved["sound"] ?: return
        val volume = resolved["soundVolume"]?.toFloatOrNull() ?: 1.0f
        val pitch = resolved["soundPitch"]?.toFloatOrNull() ?: 1.0f
        val sound = Sound.sound(Key.key(soundId), Sound.Source.PLAYER, volume, pitch)
        forEachParticleViewer(instance, ownerUuid) { player ->
            player.playSound(sound, position.x(), position.y(), position.z())
        }
    }

    private fun resolveParticle(resolved: Map<String, String>): Particle? {
        val name = resolved["particle"] ?: return null
        val base = runCatching { Particle.fromKey("minecraft:${name.lowercase()}") }.getOrNull() ?: return null
        val scale = resolved["scale"]?.toFloatOrNull() ?: 1f
        return when (base) {
            is Particle.Dust -> {
                val color = parseColor(resolved["color"]) ?: return base
                base.withProperties(color, scale)
            }
            is Particle.DustColorTransition -> {
                val from = parseColor(resolved["color"]) ?: return base
                val to = parseColor(resolved["toColor"]) ?: from
                base.withProperties(from, to, scale)
            }
            is Particle.SculkCharge -> {
                val roll = resolved["roll"]?.toFloatOrNull() ?: 0f
                base.withRoll(roll)
            }
            is Particle.Trail -> {
                val color = parseColor(resolved["color"]) ?: Color(255, 255, 255)
                val duration = resolved["duration"]?.toIntOrNull() ?: 20
                base.withProperties(base.target(), color, duration)
            }
            else -> base
        }
    }

    private fun parseColor(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        val cleaned = hex.removePrefix("#")
        if (cleaned.length != 6) return null
        val rgb = cleaned.toIntOrNull(16) ?: return null
        return Color(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
    }
}

private object WeaponEffects {

    private val byKeyword = listOf(
        "sword" to Particle.CRIT,
        "axe" to Particle.SWEEP_ATTACK,
        "bow" to Particle.ENCHANTED_HIT,
        "crossbow" to Particle.ENCHANTED_HIT,
        "trident" to Particle.BUBBLE,
        "mace" to Particle.EXPLOSION,
        "shovel" to Particle.CLOUD,
        "pickaxe" to Particle.CRIT,
    )

    fun resolve(material: String?): Particle? {
        if (material.isNullOrEmpty()) return null
        val key = material.lowercase()
        for ((keyword, particle) in byKeyword) {
            if (keyword in key) return particle
        }
        return null
    }
}
