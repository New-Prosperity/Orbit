package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.VanillaModules
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.Instance
import net.minestom.server.potion.PotionEffect
import net.minestom.server.timer.Task
import java.time.Duration

object PotionEffectsModule : VanillaModule {

    override val id = "potion-effects"
    override val description = "Apply gameplay effects from active potion effects (poison, regen, wither, etc.)"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-potion-effects")

        node.addListener(PlayerTickEvent::class.java) { event ->
            val player = event.player
            val tick = player.aliveTicks

            for (effect in player.activeEffects) {
                val potion = effect.potion()
                val amplifier = potion.amplifier() + 1

                when (potion.effect()) {
                    PotionEffect.POISON -> {
                        val rate = (25 shr (amplifier - 1)).coerceAtLeast(1)
                        if (tick % rate == 0L && player.health > 1f) {
                            player.damage(DamageType.MAGIC, 1f)
                            if (player.health < 1f) player.health = 1f
                        }
                    }
                    PotionEffect.REGENERATION -> {
                        val rate = (50 shr (amplifier - 1)).coerceAtLeast(1)
                        val maxHp = player.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
                        if (tick % rate == 0L && player.health < maxHp) {
                            player.health = (player.health + 1f).coerceAtMost(maxHp)
                        }
                    }
                    PotionEffect.WITHER -> {
                        val rate = (40 shr (amplifier - 1)).coerceAtLeast(1)
                        if (tick % rate == 0L) {
                            player.damage(DamageType.WITHER, 1f)
                        }
                    }
                    PotionEffect.HUNGER -> {
                        if (tick % 20 == 0L) {
                            val exhaustion = 0.005f * amplifier
                            player.food = (player.food - 1).coerceAtLeast(0)
                        }
                    }
                    PotionEffect.INSTANT_DAMAGE -> {
                        if (tick == player.aliveTicks) {
                            player.damage(DamageType.MAGIC, (3.0f * amplifier * 2))
                        }
                    }
                    PotionEffect.INSTANT_HEALTH -> {
                        if (tick == player.aliveTicks) {
                            val maxHp = player.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
                            player.health = (player.health + 4f * amplifier).coerceAtMost(maxHp)
                        }
                    }
                    else -> {}
                }
            }
        }

        return node
    }
}
