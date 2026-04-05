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

private val SUFFOCATION_HEAD_POS_TAG = Tag.Long("vanilla:suffocation_head_pos")
private val CACHED_SOLID_TAG = Tag.Boolean("vanilla:cached_solid")

object SuffocationModule : VanillaModule {

    override val id = "suffocation"
    override val description = "Damage when head is inside a solid block"
    override val configParams = listOf(
        ConfigParam.DoubleParam("damage", "Damage per suffocation tick", 1.0, 0.1, 20.0),
        ConfigParam.IntParam("tickRate", "Ticks between suffocation damage", 20, 1, 100),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val dmg = config.getDouble("damage", 1.0).toFloat()
        val tickRate = config.getInt("tickRate", 20)

        val node = EventNode.all("vanilla-suffocation")

        node.addListener(PlayerTickEvent::class.java) { event ->
            val player = event.player
            if (player.isCreativeOrSpectator) return@addListener
            if (player.isDead) return@addListener
            if (player.aliveTicks % tickRate != 0L) return@addListener

            val headX = player.position.blockX()
            val headY = (player.position.y() + player.eyeHeight).toInt()
            val headZ = player.position.blockZ()
            val posKey = (headX.toLong() shl 40) or ((headY.toLong() and 0xFFFFF) shl 20) or (headZ.toLong() and 0xFFFFF)

            val isSolid = if (player.getTag(SUFFOCATION_HEAD_POS_TAG) == posKey) {
                player.getTag(CACHED_SOLID_TAG) ?: false
            } else {
                val inst = player.instance ?: return@addListener
                val block = inst.getBlock(headX, headY, headZ)
                val result = block.isSolid && !block.isAir
                player.setTag(SUFFOCATION_HEAD_POS_TAG, posKey)
                player.setTag(CACHED_SOLID_TAG, result)
                result
            }

            if (isSolid) {
                player.damage(DamageType.IN_WALL, dmg)
            }
        }

        return node
    }
}
