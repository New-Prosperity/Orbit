package me.nebula.orbit.mechanic.suspiciousstew

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import kotlin.random.Random

private val STEW_EFFECTS = listOf(
    PotionEffect.SPEED,
    PotionEffect.JUMP_BOOST,
    PotionEffect.REGENERATION,
    PotionEffect.POISON,
    PotionEffect.BLINDNESS,
    PotionEffect.SATURATION,
    PotionEffect.WEAKNESS,
    PotionEffect.NIGHT_VISION,
)

class SuspiciousStewModule : OrbitModule("suspicious-stew") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.itemStack.material() != Material.SUSPICIOUS_STEW) return@addListener

            val player = event.player
            val effect = STEW_EFFECTS[Random.nextInt(STEW_EFFECTS.size)]
            val durationTicks = Random.nextInt(100, 301)

            player.addEffect(Potion(effect, 0, durationTicks))

            val slot = player.heldSlot.toInt()
            player.inventory.setItemStack(slot, ItemStack.of(Material.BOWL))
        }
    }
}
