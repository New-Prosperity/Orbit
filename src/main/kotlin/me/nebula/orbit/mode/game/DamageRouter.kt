package me.nebula.orbit.mode.game

import me.nebula.ether.utils.logging.withTrace
import me.nebula.orbit.displayUsername
import me.nebula.orbit.progression.ProgressionEvent
import me.nebula.orbit.progression.ProgressionEventBus
import me.nebula.orbit.traceId
import me.nebula.orbit.utils.achievement.AchievementTriggerManager
import me.nebula.orbit.utils.deathrecap.DamageEntry
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import java.util.concurrent.atomic.AtomicLong

class DamageRouter(private val gameMode: GameMode) {

    fun install(parent: EventNode<in EntityDamageEvent>) {
        parent.addListener(EntityDamageEvent::class.java) { event ->
            val victim = event.entity as? Player ?: return@addListener
            withTrace(victim.traceId) { handle(event) }
        }
    }

    private fun handle(event: EntityDamageEvent) {
        val victim = event.entity as? Player ?: return
        if (gameMode.phase != GamePhase.PLAYING) return
        if (!gameMode.tracker.isAlive(victim.uuid)) return

        val damage = event.damage
        val attackerEntity = if (damage is EntityDamage) damage.source else null
        val attacker = attackerEntity as? Player

        if (gameMode.isTeamMode && !gameMode.isFriendlyFireEnabled &&
            attacker != null && gameMode.areTeammates(attacker.uuid, victim.uuid)
        ) {
            event.isCancelled = true
            return
        }

        if (!gameMode.onPlayerDamaged(victim, attacker, damage.amount, event)) {
            event.isCancelled = true
            return
        }

        if (event.isCancelled) return

        if (attacker != null && attacker.uuid != victim.uuid) {
            gameMode.tracker.recordDamage(attacker.uuid, victim.uuid)
            ProgressionEventBus.publish(ProgressionEvent.DamageDealt(attacker, damage.amount.toInt()))
        }

        runCatching {
            val counter = gameMode.damageTakenCountersInternal.computeIfAbsent(victim.uuid) { AtomicLong() }
            val totalDamage = counter.addAndGet(damage.amount.toLong())
            AchievementTriggerManager.evaluate(victim, "total_damage_taken", totalDamage)
        }

        gameMode.deathRecapTracker?.recordDamage(
            victim.uuid,
            DamageEntry(
                attackerUuid = attacker?.uuid,
                attackerName = attacker?.displayUsername ?: damage.type.key().value(),
                amount = damage.amount,
                source = if (attacker != null) "PLAYER" else damage.type.key().value(),
                weapon = attacker?.itemInMainHand?.material(),
                distance = attacker?.let { it.position.distance(victim.position) },
            ),
        )
    }
}
