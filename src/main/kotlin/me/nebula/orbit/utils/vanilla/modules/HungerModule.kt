package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.isCreativeOrSpectator
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import kotlin.math.sqrt

private val EXHAUSTION_TAG = Tag.Double("vanilla:exhaustion")
private val HUNGER_LAST_Y_TAG = Tag.Double("vanilla:hunger_last_y")

object HungerModule : VanillaModule {

    override val id = "hunger"
    override val description = "Food depletion from sprinting, swimming, jumping, attacking. Starvation at 0 food. Sprint disabled at food <= 6."
    override val configParams = listOf(
        ConfigParam.DoubleParam("depletionRate", "Exhaustion multiplier", 1.0, 0.0, 10.0),
        ConfigParam.BoolParam("starvation", "Damage at 0 food", true),
        ConfigParam.DoubleParam("starvationDamage", "Damage per starvation tick", 1.0, 0.1, 20.0),
        ConfigParam.IntParam("starvationTickRate", "Ticks between starvation damage", 80, 1, 200),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val depletionRate = config.getDouble("depletionRate", 1.0)
        val starvation = config.getBoolean("starvation", true)
        val starvationDmg = config.getDouble("starvationDamage", 1.0).toFloat()
        val starvationRate = config.getInt("starvationTickRate", 80)

        val node = EventNode.all("vanilla-hunger")

        node.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            if (player.isFlying || player.isCreativeOrSpectator) return@addListener

            if (player.food <= 6) {
                player.isSprinting = false
            }

            val dx = event.newPosition.x() - player.position.x()
            val dy = event.newPosition.y() - player.position.y()
            val dz = event.newPosition.z() - player.position.z()
            val horizontalDist = sqrt(dx * dx + dz * dz)

            var cost = 0.0

            if (horizontalDist > 0.01) {
                val inst = player.instance ?: return@addListener
                val inWater = inst.getBlock(player.position.blockX(), player.position.blockY(), player.position.blockZ()).compare(Block.WATER)

                cost += when {
                    inWater -> horizontalDist * 0.01 * depletionRate
                    player.isSprinting -> horizontalDist * 0.1 * depletionRate
                    else -> 0.0
                }
            }

            val prevY = player.getTag(HUNGER_LAST_Y_TAG) ?: player.position.y()
            if (dy > 0.1 && !player.isOnGround) {
                cost += if (player.isSprinting) 0.2 * depletionRate else 0.05 * depletionRate
            }
            player.setTag(HUNGER_LAST_Y_TAG, event.newPosition.y())

            if (cost > 0) addExhaustion(player, cost)
        }

        node.addListener(EntityDamageEvent::class.java) { event ->
            val attacker = event.damage.attacker
            if (attacker is Player) {
                if (attacker.isCreativeOrSpectator) return@addListener
                addExhaustion(attacker, 0.1 * depletionRate)
            }
        }

        node.addListener(PlayerTickEvent::class.java) { event ->
            val player = event.player
            if (player.isCreativeOrSpectator) return@addListener

            if (starvation && player.food <= 0 && player.health > 1f) {
                if (player.aliveTicks % starvationRate == 0L) {
                    player.damage(DamageType.STARVE, starvationDmg)
                }
            }
        }

        return node
    }

    private fun addExhaustion(player: Player, amount: Double) {
        val ex = (player.getTag(EXHAUSTION_TAG) ?: 0.0) + amount
        if (ex >= 4.0) {
            player.setTag(EXHAUSTION_TAG, ex - 4.0)
            if (player.foodSaturation > 0) {
                player.foodSaturation = (player.foodSaturation - 1f).coerceAtLeast(0f)
            } else if (player.food > 0) {
                player.food = player.food - 1
            }
        } else {
            player.setTag(EXHAUSTION_TAG, ex)
        }
    }
}
