package me.nebula.orbit.mechanic.quickcharge

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.event.item.PlayerBeginItemUseEvent
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.item.Material
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.tag.Tag

private val CHARGE_START_TAG = Tag.Long("mechanic:quickcharge:start").defaultValue(0L)
private val QUICK_CHARGE_LEVEL_TAG = Tag.Integer("mechanic:quickcharge:level").defaultValue(0)
private val CROSSBOW_LOADED_TAG = Tag.Boolean("mechanic:crossbow:loaded")

private const val BASE_CHARGE_MS = 1250L
private const val REDUCTION_PER_LEVEL_MS = 250L

class QuickChargeModule : OrbitModule("quick-charge") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBeginItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.CROSSBOW) return@addListener

            val item = event.itemStack
            val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@addListener
            val level = enchantments.level(Enchantment.QUICK_CHARGE)
            if (level <= 0) return@addListener

            val player = event.player
            player.setTag(CHARGE_START_TAG, System.currentTimeMillis())
            player.setTag(QUICK_CHARGE_LEVEL_TAG, level)
        }

        eventNode.addListener(PlayerCancelItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.CROSSBOW) return@addListener

            val player = event.player
            val level = player.getTag(QUICK_CHARGE_LEVEL_TAG)
            if (level <= 0) return@addListener

            val chargeStart = player.getTag(CHARGE_START_TAG)
            if (chargeStart == 0L) return@addListener

            val chargeDuration = System.currentTimeMillis() - chargeStart
            val requiredCharge = (BASE_CHARGE_MS - REDUCTION_PER_LEVEL_MS * level).coerceAtLeast(0L)

            if (chargeDuration >= requiredCharge) {
                player.setTag(CROSSBOW_LOADED_TAG, true)
            }

            player.setTag(CHARGE_START_TAG, 0L)
            player.setTag(QUICK_CHARGE_LEVEL_TAG, 0)
        }
    }
}
