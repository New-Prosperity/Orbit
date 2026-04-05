package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val BYPASSES_SHIELD = setOf(
    DamageType.STARVE,
    DamageType.DROWN,
    DamageType.OUT_OF_WORLD,
    DamageType.ON_FIRE,
    DamageType.MAGIC,
    DamageType.WITHER,
)

private val SHIELD_COOLDOWN_TAG = Tag.Long("vanilla:shield_cooldown")

object ShieldBlockingModule : VanillaModule {

    override val id = "shield-blocking"
    override val description = "Shield blocks melee damage when sneaking, axes disable shields"
    override val configParams = listOf(
        ConfigParam.IntParam("disableTicks", "Ticks shield is disabled after axe hit", 100, 0, 600),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val disableTicks = config.getInt("disableTicks", 100)

        val node = EventNode.all("vanilla-shield-blocking")

        node.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            if (event.damage.getType() in BYPASSES_SHIELD) return@addListener

            val shieldHand = findShieldHand(player) ?: return@addListener
            if (!player.isSneaking) return@addListener

            val cooldownEnd = player.getTag(SHIELD_COOLDOWN_TAG) ?: 0L
            if (player.aliveTicks < cooldownEnd) return@addListener

            val attacker = event.damage.attacker as? Player
            if (attacker != null) {
                val attackerItem = attacker.itemInMainHand
                val attackerMaterial = attackerItem.material()
                if (isAxe(attackerMaterial)) {
                    player.setTag(SHIELD_COOLDOWN_TAG, player.aliveTicks + disableTicks)
                    event.damage.setAmount(event.damage.amount * 0.5f)
                    damageShield(player, shieldHand)
                    return@addListener
                }
            }

            val attackDir = event.damage.attacker?.let { src ->
                val dx = player.position.x() - src.position.x()
                val dz = player.position.z() - src.position.z()
                val yaw = Math.toRadians(player.position.yaw().toDouble())
                val facingX = -sin(yaw)
                val facingZ = cos(yaw)
                dx * facingX + dz * facingZ
            } ?: 1.0

            if (attackDir > 0) {
                event.damage.setAmount(0f)
                damageShield(player, shieldHand)

                event.damage.attacker?.let { src ->
                    val dx = src.position.x() - player.position.x()
                    val dz = src.position.z() - player.position.z()
                    val len = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1)
                    src.velocity = Vec(dx / len * 5, 2.0, dz / len * 5)
                }
            }
        }

        return node
    }

    private fun findShieldHand(player: Player): PlayerHand? {
        if (player.itemInMainHand.material() == Material.SHIELD) return PlayerHand.MAIN
        if (player.itemInOffHand.material() == Material.SHIELD) return PlayerHand.OFF
        return null
    }

    private fun isAxe(material: Material): Boolean = material.key().value().endsWith("_axe")

    private fun damageShield(player: Player, hand: PlayerHand) {
        val item = if (hand == PlayerHand.MAIN) player.itemInMainHand else player.itemInOffHand
        val maxDamage = item.get(DataComponents.MAX_DAMAGE) ?: return
        val currentDamage = item.get(DataComponents.DAMAGE) ?: 0
        val newDamage = currentDamage + 1
        val newItem = if (newDamage >= maxDamage) ItemStack.AIR else item.with(DataComponents.DAMAGE, newDamage)
        if (hand == PlayerHand.MAIN) player.setItemInMainHand(newItem)
        else player.setItemInOffHand(newItem)
    }
}
