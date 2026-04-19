package me.nebula.orbit.mode.game.battleroyale

import me.nebula.orbit.progression.BattlePassManager
import me.nebula.orbit.progression.mission.MissionTracker
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.achievement.AchievementRegistry
import me.nebula.orbit.utils.deathrecap.DeathRecapTracker
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.stattracker.StatTracker
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.tag.Tag
import java.util.UUID
import me.nebula.ether.utils.translation.asTranslationKey

class BattleRoyaleKillPipeline(
    private val mode: BattleRoyaleMode,
    private val deathRecapTracker: DeathRecapTracker,
    private val lastAttackerTag: Tag<UUID>,
    private val lastAttackerTimeTag: Tag<Long>,
) {

    fun handleLethal(victim: Player, attacker: Player?, amount: Float, event: EntityDamageEvent): Boolean {
        event.isCancelled = true
        victim.health = victim.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
        if (attacker != null && attacker.uuid != victim.uuid) {
            MissionTracker.onDamageDealt(attacker, amount.toInt())
        }

        val killer = resolveKillerPlayer(victim)
        if (killer != null && mode.isKillerAlive(killer.uuid)) {
            applyKillRewards(killer, victim)
        }

        deathRecapTracker.sendRecap(victim)
        val recap = deathRecapTracker.buildRecap(victim)

        mode.eliminatePlayer(victim)

        if (recap?.killerUuid != null) {
            spectateKiller(victim, recap.killerUuid)
        }

        deathRecapTracker.clearPlayer(victim.uuid)
        victim.removeTag(lastAttackerTag)
        victim.removeTag(lastAttackerTimeTag)
        return false
    }

    private fun resolveKillerPlayer(victim: Player): Player? {
        val killerUuid = mode.resolveKillerForVictim(victim) ?: return null
        return MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(killerUuid)
    }

    private fun applyKillRewards(killer: Player, victim: Player) {
        StatTracker.increment(killer, "kills")
        BattleRoyaleKitManager.awardXp(killer, "kill")
        LegendaryListener.notifyKill(killer, victim)
        MissionTracker.onKill(killer)
        BattlePassManager.addXpToAll(killer, mode.applyPartyBonusToKiller(killer.uuid, 10L), mode.activeBattlePasses())

        val killStreak = StatTracker.get(killer, "kills").toInt()
        when (killStreak) {
            2 -> AchievementRegistry.complete(killer, "double_trouble")
            5 -> AchievementRegistry.complete(killer, "unstoppable")
            10 -> AchievementRegistry.complete(killer, "rampage")
        }

        if (mode.goldenHeadEnabled()) {
            killer.inventory.addItemStack(GoldenHeadManager.createStack { key -> killer.translateRaw(key.asTranslationKey()) })
        }
    }

    private fun spectateKiller(victim: Player, killerUuid: UUID) {
        val killerTarget = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(killerUuid) ?: return
        victim.spectate(killerTarget)
        delay(60) {
            if (victim.gameMode == GameMode.SPECTATOR) victim.stopSpectating()
        }
    }
}
