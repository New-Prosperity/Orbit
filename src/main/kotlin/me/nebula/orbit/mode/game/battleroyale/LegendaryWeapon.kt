package me.nebula.orbit.mode.game.battleroyale

import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.orbit.utils.cooldown.CooldownIndicator
import me.nebula.orbit.utils.cooldown.SkillCooldown
import me.nebula.orbit.utils.cooldown.skillCooldown
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

interface LegendaryAbility {
    fun onActivate(player: Player) {}
    fun onPassiveHit(player: Player, target: Player, damage: Float) {}
    fun onPassiveHurt(player: Player, attacker: Player, damage: Float) {}
    fun onKill(player: Player, victim: Player) {}
}

data class LegendaryDefinition(
    val id: String,
    val nameKey: TranslationKey,
    val baseMaterial: Material,
    val ability: LegendaryAbility,
    val cooldown: Duration,
    val cooldownIndicator: CooldownIndicator = CooldownIndicator.ACTION_BAR,
)

object LegendaryRegistry {

    private val definitions = ConcurrentHashMap<String, LegendaryDefinition>()
    private val legendaryTag = Tag.String("br_legendary_id")

    fun register(definition: LegendaryDefinition) {
        require(!definitions.containsKey(definition.id)) { "Legendary '${definition.id}' already registered" }
        definitions[definition.id] = definition
        skillCooldown("legendary_${definition.id}") {
            duration(definition.cooldown)
            indicator(definition.cooldownIndicator)
        }
    }

    fun unregister(id: String) {
        definitions.remove(id)
        SkillCooldown.unregister("legendary_$id")
    }

    fun definitionOf(id: String): LegendaryDefinition? = definitions[id]

    fun createStack(definition: LegendaryDefinition, nameResolver: (String) -> String): ItemStack =
        itemStack(definition.baseMaterial) {
            name(nameResolver(definition.nameKey.value))
            glowing()
        }.withTag(legendaryTag, definition.id)

    fun identifyLegendary(stack: ItemStack): LegendaryDefinition? {
        val id = stack.getTag(legendaryTag) ?: return null
        return definitions[id]
    }

    fun all(): Collection<LegendaryDefinition> = definitions.values

    fun clear() {
        definitions.keys.forEach { SkillCooldown.unregister("legendary_$it") }
        definitions.clear()
    }
}

object LegendaryListener {

    private val legendaryTag = Tag.String("br_legendary_id")
    private var eventNode: EventNode<*>? = null

    fun install() {
        val node = EventNode.all("br-legendary")

        node.addListener(PlayerUseItemEvent::class.java) { event ->
            val player = event.player
            val stack = player.inventory.getItemStack(player.heldSlot.toInt())
            val definition = LegendaryRegistry.identifyLegendary(stack) ?: return@addListener
            val skillName = "legendary_${definition.id}"
            if (!SkillCooldown.use(player, skillName)) return@addListener
            definition.ability.onActivate(player)
        }

        node.addListener(EntityDamageEvent::class.java) { event ->
            val target = event.entity as? Player ?: return@addListener
            val damage = event.damage
            if (damage !is EntityDamage) return@addListener
            val attacker = damage.source as? Player ?: return@addListener

            val attackerItem = attacker.inventory.getItemStack(attacker.heldSlot.toInt())
            LegendaryRegistry.identifyLegendary(attackerItem)?.ability?.onPassiveHit(attacker, target, damage.amount)

            for (slot in 0 until target.inventory.size) {
                val stack = target.inventory.getItemStack(slot)
                val def = LegendaryRegistry.identifyLegendary(stack) ?: continue
                def.ability.onPassiveHurt(target, attacker, damage.amount)
            }
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun notifyKill(killer: Player, victim: Player) {
        val stack = killer.inventory.getItemStack(killer.heldSlot.toInt())
        LegendaryRegistry.identifyLegendary(stack)?.ability?.onKill(killer, victim)
    }

    fun uninstall() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
    }
}
