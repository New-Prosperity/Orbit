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

private val AIR_SUPPLY_TAG = Tag.Integer("vanilla:air_supply")
private val DROWNING_HEAD_POS_TAG = Tag.Long("vanilla:drowning_head_pos")
private val CACHED_UNDERWATER_TAG = Tag.Boolean("vanilla:cached_underwater")

object DrowningModule : VanillaModule {

    override val id = "drowning"
    override val description = "Damage when air supply runs out underwater"
    override val configParams = listOf(
        ConfigParam.IntParam("maxAirTicks", "Maximum air supply in ticks (300 = 15s)", 300, 20, 1200),
        ConfigParam.DoubleParam("damage", "Damage per drowning tick", 2.0, 0.1, 20.0),
        ConfigParam.IntParam("damageRate", "Ticks between drowning damage", 20, 1, 100),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val maxAir = config.getInt("maxAirTicks", 300)
        val dmg = config.getDouble("damage", 2.0).toFloat()
        val dmgRate = config.getInt("damageRate", 20)

        val node = EventNode.all("vanilla-drowning")

        node.addListener(PlayerTickEvent::class.java) { event ->
            val player = event.player
            if (player.isCreativeOrSpectator) return@addListener
            if (player.isDead) return@addListener

            val headX = player.position.blockX()
            val headY = (player.position.y() + player.eyeHeight).toInt()
            val headZ = player.position.blockZ()
            val posKey = (headX.toLong() shl 40) or ((headY.toLong() and 0xFFFFF) shl 20) or (headZ.toLong() and 0xFFFFF)

            val underwater = if (player.getTag(DROWNING_HEAD_POS_TAG) == posKey) {
                player.getTag(CACHED_UNDERWATER_TAG) ?: false
            } else {
                val inst = player.instance ?: return@addListener
                val result = inst.getBlock(headX, headY, headZ).name() == "minecraft:water"
                player.setTag(DROWNING_HEAD_POS_TAG, posKey)
                player.setTag(CACHED_UNDERWATER_TAG, result)
                result
            }

            val air = player.getTag(AIR_SUPPLY_TAG) ?: maxAir

            if (underwater) {
                val newAir = (air - 1).coerceAtLeast(0)
                player.setTag(AIR_SUPPLY_TAG, newAir)
                if (newAir <= 0 && player.aliveTicks % dmgRate == 0L) {
                    player.damage(DamageType.DROWN, dmg)
                }
            } else if (air < maxAir) {
                player.setTag(AIR_SUPPLY_TAG, (air + 30).coerceAtMost(maxAir))
            }
        }

        return node
    }
}
