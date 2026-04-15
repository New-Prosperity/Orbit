package me.nebula.orbit.script

import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.orbit.rules.RuleKey

interface ScriptAction {

    fun execute(ctx: GameContext)

    data class SetRule<T : Any>(val key: RuleKey<T>, val value: T) : ScriptAction {
        override fun execute(ctx: GameContext) { ctx.rules[key] = value }
    }

    data class SetMany(val values: Map<RuleKey<*>, Any>) : ScriptAction {
        override fun execute(ctx: GameContext) { ctx.rules.setAll(values) }
    }

    data class Announce(val key: TranslationKey, val sound: String? = null) : ScriptAction {
        override fun execute(ctx: GameContext) {
            ctx.broadcast(key.value, sound)
        }
    }

    data class PlaySound(val sound: String, val volume: Float = 1f, val pitch: Float = 1f) : ScriptAction {
        override fun execute(ctx: GameContext) {
            val event = net.minestom.server.sound.SoundEvent.fromKey(sound) ?: return
            ctx.broadcastPlayers { player ->
                player.playSound(
                    net.kyori.adventure.sound.Sound.sound(event.key(), net.kyori.adventure.sound.Sound.Source.MASTER, volume, pitch)
                )
            }
        }
    }

    data class Compose(val actions: List<ScriptAction>) : ScriptAction {
        override fun execute(ctx: GameContext) { actions.forEach { it.execute(ctx) } }
    }
}
