package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.isCreativeOrSpectator
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag

private val FIRE_LAST_POS_TAG = Tag.Long("vanilla:fire_last_pos")
private val FIRE_CACHED_BLOCK_TAG = Tag.String("vanilla:fire_cached_block")

object FireDamageModule : VanillaModule {

    override val id = "fire-damage"
    override val description = "Damage from standing in fire, lava, or while burning"
    override val configParams = listOf(
        ConfigParam.DoubleParam("fireDamage", "Damage per fire tick", 1.0, 0.1, 20.0),
        ConfigParam.DoubleParam("lavaDamage", "Damage per lava tick", 4.0, 0.1, 20.0),
        ConfigParam.IntParam("fireTickRate", "Ticks between in-fire damage", 20, 1, 100),
        ConfigParam.IntParam("lavaTickRate", "Ticks between lava damage", 10, 1, 100),
        ConfigParam.IntParam("burnTickRate", "Ticks between burn damage (after leaving fire)", 20, 1, 100),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val fireDmg = config.getDouble("fireDamage", 1.0).toFloat()
        val lavaDmg = config.getDouble("lavaDamage", 4.0).toFloat()
        val fireRate = config.getInt("fireTickRate", 20)
        val lavaRate = config.getInt("lavaTickRate", 10)
        val burnRate = config.getInt("burnTickRate", 20)

        val node = EventNode.all("vanilla-fire-damage")

        node.addListener(PlayerTickEvent::class.java) { event ->
            val player = event.player
            if (player.isCreativeOrSpectator) return@addListener
            if (player.isDead) return@addListener

            val bx = player.position.blockX()
            val by = player.position.blockY()
            val bz = player.position.blockZ()
            val posKey = (bx.toLong() shl 40) or ((by.toLong() and 0xFFFFF) shl 20) or (bz.toLong() and 0xFFFFF)

            val blockName = if (player.getTag(FIRE_LAST_POS_TAG) == posKey) {
                player.getTag(FIRE_CACHED_BLOCK_TAG) ?: ""
            } else {
                val inst = player.instance ?: return@addListener
                val name = inst.getBlock(bx, by, bz).name()
                player.setTag(FIRE_LAST_POS_TAG, posKey)
                player.setTag(FIRE_CACHED_BLOCK_TAG, name)
                name
            }

            val tick = player.aliveTicks

            when (blockName) {
                "minecraft:lava" -> {
                    if (tick % lavaRate == 0L) player.damage(DamageType.LAVA, lavaDmg)
                    player.setFireTicks(300)
                }
                "minecraft:fire" -> {
                    if (tick % fireRate == 0L) player.damage(DamageType.IN_FIRE, fireDmg)
                    player.setFireTicks(160)
                }
                "minecraft:soul_fire" -> {
                    if (tick % fireRate == 0L) player.damage(DamageType.IN_FIRE, fireDmg * 2)
                    player.setFireTicks(160)
                }
                "minecraft:water" -> {
                    if (player.isOnFire) player.setFireTicks(0)
                }
                else -> {
                    if (player.isOnFire && tick % burnRate == 0L) {
                        player.damage(DamageType.ON_FIRE, fireDmg)
                    }
                }
            }
        }

        return node
    }
}
