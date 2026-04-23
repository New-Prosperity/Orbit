package me.nebula.orbit.loadout

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.loadout.DeliveryEntry
import me.nebula.gravity.loadout.DeliveryEntryKind
import me.nebula.gravity.loadout.LoadoutCatalog
import me.nebula.gravity.loadout.LoadoutDeliveryEngine
import me.nebula.gravity.loadout.LoadoutDeliveryResolver
import me.nebula.gravity.loadout.LoadoutPreferenceManager
import me.nebula.gravity.loadout.LoadoutItemDefinition
import me.nebula.gravity.loadout.LoadoutBonusDefinition
import me.nebula.orbit.event.GameEvent
import me.nebula.orbit.event.GameEventBus
import me.nebula.orbit.mode.game.battleroyale.zone.ZoneState
import me.nebula.orbit.rules.Rules
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LoadoutDeliveryRuntime(
    private val modeId: String,
    private val events: GameEventBus,
    private val onDelivery: (Player, DeliveryEntry) -> Unit = ::defaultDeliver,
) {

    private val logger = logger("LoadoutDelivery")
    private val engine = LoadoutDeliveryEngine(
        deliver = { entry -> dispatch(entry) },
        conditional = { conditionId, uuid -> resolveCondition(conditionId, uuid) },
    )
    private val ruleSub: GameEventBus.Subscription
    private val zoneSub: GameEventBus.Subscription
    private var tickTask: Task? = null

    init {
        ruleSub = events.subscribe<GameEvent.RuleChanged<*>> { event ->
            if (event.key.id == Rules.PVP_ENABLED.id && event.old == false && event.new == true) {
                engine.onTruceLifted()
            }
        }
        zoneSub = events.subscribe<GameEvent.ZoneTransition> { event ->
            val to = event.to
            if (to is ZoneState.Shrinking) engine.onZonePhase(to.phaseIndex)
        }
    }

    fun start() {
        engine.markGameStart()
        tickTask = repeat(20) { engine.tick() }
    }

    fun scheduleForPlayer(player: Player) {
        val loadout = LoadoutPreferenceManager.activeLoadout(player.uuid, modeId) ?: return
        val entries = LoadoutDeliveryResolver.resolve(player.uuid, loadout)
        if (entries.isEmpty()) return
        engine.schedule(player.uuid, entries)
        engine.fireImmediate(player.uuid)
    }

    fun fireEvent(eventId: String) { engine.onEvent(eventId) }

    fun onPlayerLeft(uuid: UUID) { engine.removePlayer(uuid) }

    fun shutdown() {
        tickTask?.cancel()
        tickTask = null
        ruleSub.cancel()
        zoneSub.cancel()
        engine.reset()
    }

    private fun dispatch(entry: DeliveryEntry) {
        val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(entry.uuid) ?: return
        runCatching { onDelivery(player, entry) }
            .onFailure { logger.warn(it) { "delivery failed for ${entry.entryId} (${entry.kind}) to ${entry.uuid}" } }
    }

    private fun resolveCondition(conditionId: String, uuid: UUID): Boolean {
        val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: return false
        return LoadoutConditionRegistry.resolve(conditionId, player)
    }

    companion object {
        private val logger = logger("LoadoutDelivery")

        fun defaultDeliver(player: Player, entry: DeliveryEntry) {
            when (entry.kind) {
                DeliveryEntryKind.ITEM -> deliverItem(player, entry)
                DeliveryEntryKind.BONUS -> deliverBonus(player, entry)
            }
        }

        private fun deliverItem(player: Player, entry: DeliveryEntry) {
            val def: LoadoutItemDefinition = LoadoutCatalog.item(entry.entryId) ?: return
            logger.info { "deliver item ${def.id} → ${player.username} (policy=${entry.policy})" }
            LoadoutMaterializer.apply(player, def)
        }

        private fun deliverBonus(player: Player, entry: DeliveryEntry) {
            val def: LoadoutBonusDefinition = LoadoutCatalog.bonus(entry.entryId) ?: return
            logger.info { "deliver bonus ${def.id} → ${player.username} (policy=${entry.policy})" }
            LoadoutMaterializer.apply(player, def)
        }
    }
}

object LoadoutConditionRegistry {

    private val conditions = ConcurrentHashMap<String, (Player) -> Boolean>()

    fun register(id: String, predicate: (Player) -> Boolean) { conditions[id] = predicate }

    fun resolve(id: String, player: Player): Boolean =
        conditions[id]?.invoke(player) ?: false

    fun clear() { conditions.clear() }
}
