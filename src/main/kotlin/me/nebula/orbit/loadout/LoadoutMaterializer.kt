package me.nebula.orbit.loadout

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.loadout.BonusPayload
import me.nebula.gravity.loadout.LoadoutBonusDefinition
import me.nebula.gravity.loadout.LoadoutItemDefinition
import me.nebula.gravity.loadout.LoadoutPayload
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import java.util.concurrent.ConcurrentHashMap

object LoadoutMaterializer {

    private val logger = logger("LoadoutMaterializer")
    private val bonusHooks = ConcurrentHashMap<String, (Player, BonusPayload.Hook) -> Unit>()

    fun registerHook(id: String, handler: (Player, BonusPayload.Hook) -> Unit) { bonusHooks[id] = handler }

    fun clearHooks() { bonusHooks.clear() }

    fun apply(player: Player, def: LoadoutItemDefinition) { applyPayload(player, def.payload) }

    fun apply(player: Player, def: LoadoutBonusDefinition) { applyBonus(player, def.payload) }

    private fun applyPayload(player: Player, payload: LoadoutPayload) {
        when (payload) {
            is LoadoutPayload.Material -> applyMaterial(player, payload)
            is LoadoutPayload.ArmorSet -> applyArmor(player, payload)
            is LoadoutPayload.PotionEffect -> applyPotion(player, payload)
            is LoadoutPayload.Composite -> payload.payloads.forEach { applyPayload(player, it) }
            is LoadoutPayload.Custom -> logger.info { "custom payload '${payload.key}' — no materializer registered yet" }
        }
    }

    private fun applyMaterial(player: Player, material: LoadoutPayload.Material) {
        val mat = Material.fromKey(material.id)
        if (mat == null) {
            logger.warn { "unknown material '${material.id}' — skipping delivery" }
            return
        }
        val stack = ItemStack.of(mat, material.amount)
        val slot = material.slot
        if (slot != null && slot in 0..35) {
            player.inventory.setItemStack(slot, stack)
        } else {
            player.inventory.addItemStack(stack)
        }
    }

    private fun applyArmor(player: Player, armor: LoadoutPayload.ArmorSet) {
        armor.helmet?.let { Material.fromKey(it) }?.let { player.setEquipment(EquipmentSlot.HELMET, ItemStack.of(it)) }
        armor.chestplate?.let { Material.fromKey(it) }?.let { player.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.of(it)) }
        armor.leggings?.let { Material.fromKey(it) }?.let { player.setEquipment(EquipmentSlot.LEGGINGS, ItemStack.of(it)) }
        armor.boots?.let { Material.fromKey(it) }?.let { player.setEquipment(EquipmentSlot.BOOTS, ItemStack.of(it)) }
    }

    private fun applyPotion(player: Player, potion: LoadoutPayload.PotionEffect) {
        val effect = PotionEffect.fromKey(potion.effectId)
        if (effect == null) {
            logger.warn { "unknown potion effect '${potion.effectId}' — skipping" }
            return
        }
        player.addEffect(Potion(effect, potion.amplifier, potion.durationTicks))
    }

    private fun applyBonus(player: Player, payload: BonusPayload) {
        when (payload) {
            is BonusPayload.SpawnEffect -> {
                val effect = PotionEffect.fromKey(payload.effectId) ?: run {
                    logger.warn { "unknown bonus effect '${payload.effectId}' — skipping" }
                    return
                }
                player.addEffect(Potion(effect, payload.amplifier, payload.durationTicks))
            }
            is BonusPayload.AttributeBoost -> {
                logger.info { "attribute boost ${payload.attributeId} ${payload.modifier} — stub (wired in P3.6)" }
            }
            is BonusPayload.Hook -> {
                val handler = bonusHooks[payload.hookId]
                if (handler == null) {
                    logger.info { "no hook registered for '${payload.hookId}' — skipping" }
                    return
                }
                handler(player, payload)
            }
        }
    }
}
