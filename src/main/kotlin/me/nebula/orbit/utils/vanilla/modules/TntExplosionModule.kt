package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.packBlockPos
import me.nebula.orbit.utils.vanilla.unpackBlockX
import me.nebula.orbit.utils.vanilla.unpackBlockY
import me.nebula.orbit.utils.vanilla.unpackBlockZ
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ItemEntity
import net.minestom.server.instance.EntityTracker
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Explosion
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.time.Duration
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

object TntExplosionModule : VanillaModule {

    override val id = "tnt-explosions"
    override val description = "TNT ignition with flint & steel, explosion with block damage and entity knockback"
    override val configParams = listOf(
        ConfigParam.BoolParam("blockDamage", "Destroy blocks on explosion", true),
        ConfigParam.BoolParam("entityDamage", "Damage entities on explosion", true),
        ConfigParam.IntParam("fuseTicks", "TNT fuse time in ticks", 40, 10, 200),
        ConfigParam.DoubleParam("power", "Explosion power/radius", 4.0, 1.0, 20.0),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val blockDamage = config.getBoolean("blockDamage", true)
        val entityDamage = config.getBoolean("entityDamage", true)
        val fuseTicks = config.getInt("fuseTicks", 80)
        val power = config.getDouble("power", 4.0)

        val node = EventNode.all("vanilla-tnt-explosions")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            if (block != Block.TNT) return@addListener
            val heldItem = event.player.itemInMainHand
            if (heldItem.material() != Material.FLINT_AND_STEEL) return@addListener

            val pos = event.blockPosition
            event.instance.setBlock(pos, Block.AIR)
            igniteTnt(event.instance, Pos(pos.blockX() + 0.5, pos.blockY().toDouble(), pos.blockZ() + 0.5), fuseTicks, power, blockDamage, entityDamage)
        }

        return node
    }

    private fun igniteTnt(instance: Instance, pos: Pos, fuseTicks: Int, power: Double, blockDamage: Boolean, entityDamage: Boolean) {
        val tnt = Entity(EntityType.TNT)
        tnt.setNoGravity(false)
        tnt.velocity = Vec(
            (Random.nextDouble() - 0.5) * 0.4,
            4.0,
            (Random.nextDouble() - 0.5) * 0.4,
        )
        tnt.setInstance(instance, pos)

        tnt.scheduler().buildTask {
            if (tnt.isRemoved) return@buildTask
            val explodePos = tnt.position
            tnt.remove()
            VanillaExplosion(
                explodePos.x().toFloat(),
                explodePos.y().toFloat(),
                explodePos.z().toFloat(),
                power.toFloat(),
                blockDamage,
                entityDamage,
            ) { inst, chainPos -> igniteTnt(inst, chainPos, 10 + Random.nextInt(10), power, blockDamage, entityDamage) }
                .apply(instance)
        }.delay(Duration.ofMillis(fuseTicks * 50L)).schedule()
    }
}

private class VanillaExplosion(
    centerX: Float,
    centerY: Float,
    centerZ: Float,
    strength: Float,
    private val blockDamage: Boolean,
    private val entityDamage: Boolean,
    private val chainIgniter: (Instance, Pos) -> Unit,
) : Explosion(centerX, centerY, centerZ, strength) {

    private val pendingDrops = mutableListOf<PendingDrop>()

    private data class PendingDrop(val x: Int, val y: Int, val z: Int, val material: Material)

    override fun prepare(instance: Instance): List<Point> {
        val cx = centerX.toDouble()
        val cy = centerY.toDouble()
        val cz = centerZ.toDouble()
        val power = strength.toDouble()

        val blocksToDestroy = mutableListOf<Point>()

        if (blockDamage) {
            val toDestroy = mutableSetOf<Long>()
            val blockCache = HashMap<Long, Float>()

            for (rx in 0 until 16) {
                for (ry in 0 until 16) {
                    for (rz in 0 until 16) {
                        if (rx != 0 && rx != 15 && ry != 0 && ry != 15 && rz != 0 && rz != 15) continue

                        var dx = rx.toDouble() / 15.0 * 2.0 - 1.0
                        var dy = ry.toDouble() / 15.0 * 2.0 - 1.0
                        var dz = rz.toDouble() / 15.0 * 2.0 - 1.0
                        val len = sqrt(dx * dx + dy * dy + dz * dz)
                        if (len == 0.0) continue
                        dx /= len; dy /= len; dz /= len

                        var intensity = power * (0.7 + Random.nextDouble() * 0.6)
                        var x = cx
                        var y = cy
                        var z = cz

                        while (intensity > 0) {
                            val bx = floor(x).toInt()
                            val by = floor(y).toInt()
                            val bz = floor(z).toInt()
                            val packed = packBlockPos(bx, by, bz)

                            val resistance = blockCache.getOrPut(packed) {
                                val block = instance.getBlock(bx, by, bz)
                                if (block.isAir) -1f
                                else block.registry()?.explosionResistance() ?: 0f
                            }

                            if (resistance >= 0f) {
                                intensity -= (resistance.toDouble() / 5.0 + 0.3) * 0.3
                                if (intensity > 0) toDestroy.add(packed)
                            }

                            x += dx * 0.3
                            y += dy * 0.3
                            z += dz * 0.3
                            intensity -= 0.225
                        }
                    }
                }
            }

            for (packed in toDestroy) {
                val bx = unpackBlockX(packed)
                val by = unpackBlockY(packed)
                val bz = unpackBlockZ(packed)
                val block = instance.getBlock(bx, by, bz)

                if (block.compare(Block.TNT)) {
                    instance.setBlock(bx, by, bz, Block.AIR)
                    chainIgniter(instance, Pos(bx + 0.5, by.toDouble(), bz + 0.5))
                } else {
                    if (Random.nextDouble() < 1.0 / power) {
                        val dropMaterial = Material.fromKey(block.name())
                        if (dropMaterial != null) {
                            pendingDrops += PendingDrop(bx, by, bz, dropMaterial)
                        }
                    }
                    blocksToDestroy += Vec(bx.toDouble(), by.toDouble(), bz.toDouble())
                }
            }
        }

        if (entityDamage) {
            applyEntityDamage(instance, cx, cy, cz, power)
        }

        return blocksToDestroy
    }

    override fun postSend(instance: Instance, blocks: List<Point>) {
        for (drop in pendingDrops) {
            val itemEntity = ItemEntity(ItemStack.of(drop.material))
            itemEntity.setPickupDelay(Duration.ofMillis(500))
            itemEntity.velocity = Vec(
                (Random.nextDouble() - 0.5) * 4,
                Random.nextDouble() * 4 + 2,
                (Random.nextDouble() - 0.5) * 4,
            )
            itemEntity.setInstance(instance, Pos(drop.x + 0.5, drop.y + 0.5, drop.z + 0.5))
        }
    }

    private fun applyEntityDamage(instance: Instance, cx: Double, cy: Double, cz: Double, power: Double) {
        val damageRadius = power * 2
        val damageRadiusSq = damageRadius * damageRadius

        instance.entityTracker.nearbyEntities(Pos(cx, cy, cz), damageRadius, EntityTracker.Target.ENTITIES) { entity ->
            if (entity !is LivingEntity) return@nearbyEntities
            val edx = entity.position.x() - cx
            val edy = entity.position.y() + entity.getEyeHeight() - cy
            val edz = entity.position.z() - cz
            val distSq = edx * edx + edy * edy + edz * edz
            if (distSq > damageRadiusSq) return@nearbyEntities

            val dist = sqrt(distSq)
            val exposure = 1.0 - (dist / damageRadius)
            val damage = ((exposure * exposure + exposure) / 2.0 * 7.0 * power + 1.0).toFloat()
            val sourcePos = Vec(cx, cy, cz)
            entity.damage(Damage.fromPosition(DamageType.EXPLOSION, sourcePos, damage))

            if (dist > 0) {
                val knockback = exposure / dist
                entity.velocity = Vec(edx * knockback * 20, edy * knockback * 20, edz * knockback * 20)
            }
        }
    }
}
