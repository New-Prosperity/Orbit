package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.combatlog.CombatTracker
import me.nebula.orbit.utils.combatlog.tagCombat
import me.nebula.orbit.utils.damage.DamageTracker
import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.instance.Instance

object DamageIntegrationModule : VanillaModule {

    override val id = "damage-integration"
    override val description = "Records all damage in DamageTracker and tags PvP combat in CombatTracker"
    override val configParams = listOf(
        ConfigParam.BoolParam("trackDamage", "Record damage events in DamageTracker", true),
        ConfigParam.BoolParam("tagCombat", "Tag players in PvP combat via CombatTracker", true),
        ConfigParam.IntParam("combatDurationMs", "Duration of combat tag in milliseconds", 15000, 1000, 60000),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val trackDamage = config.getBoolean("trackDamage", true)
        val tagCombatEnabled = config.getBoolean("tagCombat", true)
        val combatDurationMs = config.getInt("combatDurationMs", 15000).toLong()

        if (tagCombatEnabled) CombatTracker.setCombatDuration(combatDurationMs)

        val node = EventNode.all("vanilla-damage-integration")

        node.addListener(EntityDamageEvent::class.java) { event ->
            val victim = event.entity
            val attacker = event.damage.attacker

            if (trackDamage) {
                DamageTracker.record(victim, attacker, event.damage.amount, event.damage.type)
            }

            if (tagCombatEnabled) {
                val victimPlayer = victim as? Player
                val attackerPlayer = attacker as? Player
                if (victimPlayer != null) victimPlayer.tagCombat(attackerPlayer)
                if (attackerPlayer != null) attackerPlayer.tagCombat(victimPlayer)
            }
        }

        return node
    }
}
