package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.isCreativeOrSpectator
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import kotlin.math.ceil

private val FALL_START_Y_TAG = Tag.Double("vanilla:fall_start_y")
private val WAS_ON_GROUND_TAG = Tag.Boolean("vanilla:was_on_ground")

object FallDamageModule : VanillaModule {

    override val id = "fall-damage"
    override val description = "Damage from falling more than 3 blocks"
    override val configParams = listOf(
        ConfigParam.DoubleParam("multiplier", "Damage multiplier", 1.0, 0.0, 10.0),
        ConfigParam.DoubleParam("minimumDistance", "Minimum fall distance before damage", 3.0, 0.0, 100.0),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val multiplier = config.getDouble("multiplier", 1.0)
        val minimumDistance = config.getDouble("minimumDistance", 3.0)

        val node = EventNode.all("vanilla-fall-damage")

        node.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            if (player.isCreativeOrSpectator) return@addListener

            val onGround = player.isOnGround
            val previously = player.getTag(WAS_ON_GROUND_TAG) ?: true

            if (!previously && onGround) {
                val startY = player.getTag(FALL_START_Y_TAG)
                player.removeTag(FALL_START_Y_TAG)
                if (startY != null) {
                    val distance = startY - player.position.y()
                    val safeDist = player.getAttributeValue(Attribute.SAFE_FALL_DISTANCE)
                    val fallMultiplier = player.getAttributeValue(Attribute.FALL_DAMAGE_MULTIPLIER)
                    val effectiveMin = if (safeDist > 0) safeDist else minimumDistance

                    if (distance > effectiveMin) {
                        val inst = player.instance ?: return@addListener
                        val feetBlock = inst.getBlock(player.position.blockX(), player.position.blockY(), player.position.blockZ())
                        val landBlock = inst.getBlock(player.position.blockX(), player.position.blockY() - 1, player.position.blockZ())
                        val feetName = feetBlock.name()
                        val landName = landBlock.name()

                        val negatesFall = feetBlock.compare(Block.WATER) ||
                            feetName == "minecraft:sweet_berry_bush" ||
                            feetName == "minecraft:cobweb" ||
                            feetName == "minecraft:powder_snow" ||
                            landName == "minecraft:slime_block"
                        if (negatesFall) {
                            player.setTag(WAS_ON_GROUND_TAG, onGround)
                            return@addListener
                        }

                        val blockReduction = when (landName) {
                            "minecraft:hay_block" -> 0.2
                            "minecraft:honey_block" -> 0.2
                            "minecraft:pointed_dripstone" -> 2.0
                            else -> {
                                if (landName.endsWith("_bed")) 0.5 else 1.0
                            }
                        }

                        val damage = ceil((distance - effectiveMin) * fallMultiplier * multiplier * blockReduction).toFloat()
                        if (damage > 0) player.damage(DamageType.FALL, damage)
                    }
                }
            }

            if (previously && !onGround) {
                player.setTag(FALL_START_Y_TAG, player.position.y())
            }

            player.setTag(WAS_ON_GROUND_TAG, onGround)
        }

        return node
    }
}
