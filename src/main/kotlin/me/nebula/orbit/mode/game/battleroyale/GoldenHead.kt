package me.nebula.orbit.mode.game.battleroyale

import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.tag.Tag

object GoldenHeadManager {

    private val goldenHeadTag = Tag.Boolean("br_golden_head")
    private var config = GoldenHeadConfig()
    private var eventNode: EventNode<*>? = null

    fun configure(config: GoldenHeadConfig) {
        this.config = config
    }

    fun createStack(nameResolver: (String) -> String): ItemStack =
        itemStack(Material.GOLDEN_APPLE) {
            name(nameResolver("orbit.game.br.golden_head.name"))
            lore(nameResolver("orbit.game.br.golden_head.lore"))
            glowing()
        }.withTag(goldenHeadTag, true)

    fun isGoldenHead(stack: ItemStack): Boolean =
        stack.getTag(goldenHeadTag) ?: false

    fun install() {
        val node = EventNode.all("br-golden-head")

        node.addListener(PlayerUseItemEvent::class.java) { event ->
            val player = event.player
            val heldSlot = player.heldSlot.toInt()
            val stack = player.inventory.getItemStack(heldSlot)
            if (!isGoldenHead(stack)) return@addListener

            val newStack = if (stack.amount() > 1) stack.withAmount(stack.amount() - 1) else ItemStack.AIR
            player.inventory.setItemStack(heldSlot, newStack)

            consume(player)
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    private fun consume(player: Player) {
        player.health = (player.health + config.healAmount).coerceAtMost(player.getAttributeValue(Attribute.MAX_HEALTH).toFloat())
        player.addEffect(Potion(PotionEffect.ABSORPTION, config.absorptionHearts.toInt(), config.regenDurationTicks))
        player.addEffect(Potion(PotionEffect.REGENERATION, config.regenAmplifier, config.regenDurationTicks))
    }

    fun uninstall() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
    }
}
