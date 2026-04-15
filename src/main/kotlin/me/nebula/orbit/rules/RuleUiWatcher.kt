package me.nebula.orbit.rules

import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.orbit.event.GameEvent
import me.nebula.orbit.event.GameEventBus
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.translation.translate
import net.minestom.server.entity.Player

data class RuleUiMessage(val key: TranslationKey)

internal fun describeRuleChange(ruleId: String, newValue: Any): RuleUiMessage? {
    val translationKey = when (ruleId) {
        Rules.PVP_ENABLED.id -> if (newValue == true) "orbit.rule.pvp_on" else "orbit.rule.pvp_off"
        Rules.DAMAGE_ENABLED.id -> if (newValue == true) "orbit.rule.damage_on" else "orbit.rule.damage_off"
        Rules.ZONE_SHRINKING.id -> if (newValue == true) "orbit.rule.zone_on" else "orbit.rule.zone_off"
        else -> return null
    }
    return RuleUiMessage(TranslationKey(translationKey))
}

class RuleUiWatcher(private val gameMode: GameMode) {

    private var subscription: GameEventBus.Subscription? = null

    fun install() {
        if (subscription != null) return
        subscription = gameMode.events.subscribe<GameEvent.RuleChanged<*>> { event ->
            val message = describeRuleChange(event.key.id, event.new) ?: return@subscribe
            val instance = gameMode.gameInstanceOrNull() ?: return@subscribe
            for (player in instance.players) {
                player.broadcast(message)
            }
        }
    }

    fun uninstall() {
        subscription?.cancel()
        subscription = null
    }

    private fun Player.broadcast(message: RuleUiMessage) {
        sendActionBar(translate(message.key))
    }
}
