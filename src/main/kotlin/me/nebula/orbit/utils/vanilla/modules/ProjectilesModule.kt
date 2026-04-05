package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.time.Duration
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val BOW_CHARGE_TAG = Tag.Long("vanilla:bow_charge")

object ProjectilesModule : VanillaModule {

    override val id = "projectiles"
    override val description = "Snowballs, ender pearls, eggs, fire charges, and bow/arrows with hit detection"
    override val configParams = listOf(
        ConfigParam.BoolParam("snowballs", "Enable snowball throwing", true),
        ConfigParam.BoolParam("enderPearls", "Enable ender pearl teleportation", true),
        ConfigParam.BoolParam("eggs", "Enable egg throwing", true),
        ConfigParam.BoolParam("fireCharges", "Enable fire charge throwing", true),
        ConfigParam.BoolParam("arrows", "Enable bow and arrow", true),
        ConfigParam.DoubleParam("pearlDamage", "Ender pearl landing damage to thrower", 5.0, 0.0, 20.0),
        ConfigParam.DoubleParam("arrowBaseDamage", "Arrow base damage at full charge", 6.0, 1.0, 20.0),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val snowballs = config.getBoolean("snowballs", true)
        val enderPearls = config.getBoolean("enderPearls", true)
        val eggs = config.getBoolean("eggs", true)
        val fireCharges = config.getBoolean("fireCharges", true)
        val arrows = config.getBoolean("arrows", true)
        val pearlDmg = config.getDouble("pearlDamage", 5.0).toFloat()
        val arrowBaseDamage = config.getDouble("arrowBaseDamage", 6.0).toFloat()

        val node = EventNode.all("vanilla-projectiles")

        node.addListener(PlayerUseItemEvent::class.java) { event ->
            val material = event.itemStack.material()
            when {
                snowballs && material == Material.SNOWBALL -> {
                    consumeItem(event.player)
                    launchProjectile(event.player, EntityType.SNOWBALL, 1.5) { projectile, hitEntity, _ ->
                        if (hitEntity is LivingEntity) {
                            hitEntity.velocity = projectile.velocity.mul(0.5)
                        }
                        projectile.remove()
                    }
                }
                eggs && material == Material.EGG -> {
                    consumeItem(event.player)
                    launchProjectile(event.player, EntityType.EGG, 1.5) { projectile, hitEntity, _ ->
                        if (hitEntity is LivingEntity) {
                            hitEntity.velocity = projectile.velocity.mul(0.3)
                        }
                        projectile.remove()
                    }
                }
                fireCharges && material == Material.FIRE_CHARGE -> {
                    consumeItem(event.player)
                    launchProjectile(event.player, EntityType.SMALL_FIREBALL, 1.0) { projectile, hitEntity, hitPos ->
                        if (hitEntity is LivingEntity) {
                            hitEntity.setFireTicks(100)
                        } else {
                            val bx = hitPos.x().toInt()
                            val by = hitPos.y().toInt()
                            val bz = hitPos.z().toInt()
                            val inst = projectile.instance ?: return@launchProjectile
                            if (inst.getBlock(bx, by, bz).isAir) {
                                inst.setBlock(bx, by, bz, Block.FIRE)
                            }
                        }
                        projectile.remove()
                    }
                }
                enderPearls && material == Material.ENDER_PEARL -> {
                    consumeItem(event.player)
                    val thrower = event.player
                    launchProjectile(event.player, EntityType.ENDER_PEARL, 1.5) { projectile, _, hitPos ->
                        thrower.teleport(Pos(hitPos.x(), hitPos.y() + 0.5, hitPos.z(), thrower.position.yaw(), thrower.position.pitch()))
                        thrower.damage(DamageType.FALL, pearlDmg)
                        projectile.remove()
                    }
                }
                arrows && material == Material.BOW -> {
                    if (hasArrows(event.player) || event.player.gameMode == GameMode.CREATIVE) {
                        event.player.setTag(BOW_CHARGE_TAG, event.player.aliveTicks)
                    }
                }
            }
        }

        if (arrows) {
            node.addListener(PlayerTickEvent::class.java) { event ->
                val startTick = event.player.getTag(BOW_CHARGE_TAG) ?: return@addListener
                val item = event.player.itemInMainHand

                if (item.material() != Material.BOW) {
                    val chargeTicks = (event.player.aliveTicks - startTick).toInt()
                    event.player.removeTag(BOW_CHARGE_TAG)
                    if (chargeTicks >= 3) {
                        fireBow(event.player, chargeTicks, arrowBaseDamage)
                    }
                    return@addListener
                }

                val maxCharge = 20
                val chargeTicks = (event.player.aliveTicks - startTick).toInt()
                if (chargeTicks >= maxCharge) {
                    event.player.removeTag(BOW_CHARGE_TAG)
                    fireBow(event.player, maxCharge, arrowBaseDamage)
                }
            }
        }

        return node
    }

    private fun fireBow(player: Player, chargeTicks: Int, baseDamage: Float) {
        if (!hasArrows(player) && player.gameMode != GameMode.CREATIVE) return

        if (player.gameMode != GameMode.CREATIVE) {
            val inv = player.inventory
            for (i in 0 until inv.size) {
                val stack = inv.getItemStack(i)
                if (stack.material() == Material.ARROW) {
                    inv.setItemStack(i, if (stack.amount() > 1) stack.withAmount(stack.amount() - 1) else ItemStack.AIR)
                    break
                }
            }
        }

        val chargeRatio = (chargeTicks.toFloat() / 20f).coerceIn(0.1f, 1f)
        val speed = 3.0 * chargeRatio
        val damage = baseDamage * chargeRatio
        val isCritical = chargeRatio >= 1f

        launchProjectile(player, EntityType.ARROW, speed) { projectile, hitEntity, _ ->
            if (hitEntity is LivingEntity) {
                val finalDamage = if (isCritical) damage * 1.5f else damage
                hitEntity.damage(DamageType.PLAYER_ATTACK, finalDamage)
                val knockback = projectile.velocity.normalize().mul(5.0)
                hitEntity.velocity = knockback
                hitEntity.arrowCount = hitEntity.arrowCount + 1
            }
            projectile.remove()
        }
    }

    private fun hasArrows(player: Player): Boolean {
        val inv = player.inventory
        for (i in 0 until inv.size) {
            if (inv.getItemStack(i).material() == Material.ARROW) return true
        }
        return false
    }

    private fun consumeItem(player: Player) {
        if (player.gameMode != GameMode.CREATIVE) {
            player.setItemInMainHand(player.itemInMainHand.consume(1))
        }
    }

    private fun launchProjectile(
        player: Player,
        type: EntityType,
        speed: Double,
        onHit: (Entity, Entity?, Vec) -> Unit,
    ) {
        val instance = player.instance ?: return
        val yaw = Math.toRadians(player.position.yaw().toDouble())
        val pitch = Math.toRadians(player.position.pitch().toDouble())

        val dx = -sin(yaw) * cos(pitch)
        val dy = -sin(pitch)
        val dz = cos(yaw) * cos(pitch)

        val spawnPos = player.position.add(0.0, player.eyeHeight, 0.0)
        val projectile = Entity(type)
        projectile.setNoGravity(false)
        projectile.setInstance(instance, spawnPos)
        projectile.velocity = Vec(dx * speed * 25, dy * speed * 25, dz * speed * 25)

        var ticksAlive = 0
        val hitRadius = 1.5
        val hitRadiusSq = hitRadius * hitRadius

        projectile.scheduler().buildTask {
            ticksAlive++
            if (ticksAlive > 200 || projectile.isRemoved) {
                if (!projectile.isRemoved) projectile.remove()
                return@buildTask
            }

            val pos = projectile.position
            if (pos.y() < -64) {
                projectile.remove()
                return@buildTask
            }

            val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
            if (block.isSolid) {
                onHit(projectile, null, Vec(pos.x(), pos.y(), pos.z()))
                return@buildTask
            }

            var hitTarget: LivingEntity? = null
            instance.entityTracker.nearbyEntities(pos, hitRadius, EntityTracker.Target.ENTITIES) { entity ->
                if (hitTarget != null) return@nearbyEntities
                if (entity === projectile || entity === player) return@nearbyEntities
                if (entity !is LivingEntity) return@nearbyEntities
                val edx = entity.position.x() - pos.x()
                val edy = entity.position.y() + entity.eyeHeight * 0.5 - pos.y()
                val edz = entity.position.z() - pos.z()
                if (edx * edx + edy * edy + edz * edz < hitRadiusSq) {
                    hitTarget = entity
                }
            }
            if (hitTarget != null) {
                onHit(projectile, hitTarget, Vec(pos.x(), pos.y(), pos.z()))
                return@buildTask
            }
        }.repeat(Duration.ofMillis(50)).schedule()
    }
}
