package me.nebula.orbit.audit

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.audit.AuditAction
import me.nebula.gravity.audit.AuditStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.matchresult.MatchResult
import net.minestom.server.entity.Player
import java.util.UUID

object OrbitAudit {

    private val log = logger("OrbitAudit")
    private val SYSTEM_ID = UUID(0L, 0L)
    private const val SYSTEM_NAME = "system"

    private fun source(): String = "orbit:${Orbit.serverName}"

    fun missionComplete(player: Player, missionId: String, xpReward: Int, coinReward: Int) {
        emit(
            actorId = SYSTEM_ID,
            actorName = SYSTEM_NAME,
            action = AuditAction.MISSION_COMPLETE,
            targetId = player.uuid,
            targetName = player.username,
            details = "mission=$missionId xp=$xpReward coins=$coinReward",
        )
    }

    fun achievementGrant(player: Player, achievementId: String, points: Int) {
        emit(
            actorId = SYSTEM_ID,
            actorName = SYSTEM_NAME,
            action = AuditAction.ACHIEVEMENT_GRANT,
            targetId = player.uuid,
            targetName = player.username,
            details = "achievement=$achievementId points=$points",
        )
    }

    fun gameEnd(result: MatchResult, gameMode: String?, durationMs: Long, participants: Int) {
        emit(
            actorId = SYSTEM_ID,
            actorName = SYSTEM_NAME,
            action = AuditAction.GAME_END,
            targetId = null,
            targetName = null,
            details = "mode=${gameMode ?: "unknown"} duration=${durationMs}ms players=$participants result=${result.summary()}",
        )
    }

    fun battlePassPremiumGrant(player: Player, passId: String) {
        emit(
            actorId = SYSTEM_ID,
            actorName = SYSTEM_NAME,
            action = AuditAction.BATTLEPASS_PREMIUM_GRANT,
            targetId = player.uuid,
            targetName = player.username,
            details = "pass=$passId",
        )
    }

    fun battlePassTierGrant(player: Player, passId: String, tier: Int) {
        emit(
            actorId = SYSTEM_ID,
            actorName = SYSTEM_NAME,
            action = AuditAction.BATTLEPASS_TIER_GRANT,
            targetId = player.uuid,
            targetName = player.username,
            details = "pass=$passId tier=$tier",
        )
    }

    private fun emit(
        actorId: UUID,
        actorName: String,
        action: AuditAction,
        targetId: UUID?,
        targetName: String?,
        details: String,
    ) {
        runCatching {
            AuditStore.log(
                actorId = actorId,
                actorName = actorName,
                action = action,
                targetId = targetId,
                targetName = targetName,
                details = details,
                source = source(),
            )
        }.onFailure { log.warn(it) { "Failed to emit audit entry: $action" } }
    }

    private fun MatchResult.summary(): String =
        runCatching { toString().take(120) }.getOrDefault("?")
}
