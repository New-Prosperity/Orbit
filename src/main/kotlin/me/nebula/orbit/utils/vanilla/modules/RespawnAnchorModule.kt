package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.sound.playSound
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import kotlin.math.sqrt
import kotlin.random.Random

private val TAG_ANCHOR_X = Tag.Double("nebula:anchor_x")
private val TAG_ANCHOR_Y = Tag.Double("nebula:anchor_y")
private val TAG_ANCHOR_Z = Tag.Double("nebula:anchor_z")
private val TAG_ANCHOR_SET = Tag.Boolean("nebula:anchor_set")

object RespawnAnchorModule : VanillaModule {

    override val id = "respawn-anchor"
    override val description = "Charge with glowstone (max 4), set spawn in nether dimension, explodes in overworld"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-respawn-anchor")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:respawn_anchor") return@addListener

            val charges = event.block.getProperty("charges")?.toIntOrNull() ?: 0
            val item = event.player.itemInMainHand

            if (item.material() == Material.GLOWSTONE && charges < 4) {
                val newCharges = charges + 1
                event.instance.setBlock(event.blockPosition, event.block.withProperty("charges", newCharges.toString()))
                if (event.player.gameMode != GameMode.CREATIVE) {
                    event.player.setItemInMainHand(item.consume(1))
                }
                event.player.playSound(SoundEvent.BLOCK_RESPAWN_ANCHOR_CHARGE)
                return@addListener
            }

            if (charges <= 0) return@addListener

            val dimensionName = instance.dimensionName.toString()
            val isNether = dimensionName.contains("nether")

            if (!isNether) {
                event.instance.setBlock(event.blockPosition, Block.AIR)
                explode(event.instance, event.blockPosition.blockX(), event.blockPosition.blockY(), event.blockPosition.blockZ())
                return@addListener
            }

            event.player.setTag(TAG_ANCHOR_X, event.blockPosition.blockX() + 0.5)
            event.player.setTag(TAG_ANCHOR_Y, event.blockPosition.blockY() + 1.0)
            event.player.setTag(TAG_ANCHOR_Z, event.blockPosition.blockZ() + 0.5)
            event.player.setTag(TAG_ANCHOR_SET, true)
            event.player.playSound(SoundEvent.BLOCK_RESPAWN_ANCHOR_SET_SPAWN)
        }

        return node
    }

    private fun explode(instance: Instance, cx: Int, cy: Int, cz: Int) {
        val power = 5.0
        val radius = (power * 2).toInt()

        for (bx in -radius..radius) {
            for (by in -radius..radius) {
                for (bz in -radius..radius) {
                    val dist = sqrt((bx * bx + by * by + bz * bz).toDouble())
                    if (dist > power) continue
                    val x = cx + bx
                    val y = cy + by
                    val z = cz + bz
                    val block = instance.getBlock(x, y, z)
                    if (block.isAir) continue
                    val resistance = block.registry()?.explosionResistance() ?: 0f
                    if (resistance < 1200) {
                        instance.setBlock(x, y, z, Block.AIR)
                        if (Random.nextInt(4) == 0) {
                            instance.setBlock(x, y, z, Block.FIRE)
                        }
                    }
                }
            }
        }

        val damageRadius = power * 2
        val damageRadiusSq = damageRadius * damageRadius
        val center = Vec(cx + 0.5, cy + 0.5, cz + 0.5)
        instance.entityTracker.nearbyEntities(center, damageRadius, EntityTracker.Target.ENTITIES) { entity ->
            if (entity !is LivingEntity) return@nearbyEntities
            val dx = entity.position.x() - center.x()
            val dy = entity.position.y() - center.y()
            val dz = entity.position.z() - center.z()
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq > damageRadiusSq) return@nearbyEntities
            val dist = sqrt(distSq)
            val exposure = 1.0 - (dist / damageRadius)
            val damage = ((exposure * exposure + exposure) / 2.0 * 7.0 * power + 1.0).toFloat()
            entity.damage(DamageType.EXPLOSION, damage)
            if (dist > 0) {
                val knockback = exposure / dist
                entity.velocity = Vec(dx * knockback * 20, dy * knockback * 20, dz * knockback * 20)
            }
        }
    }
}
