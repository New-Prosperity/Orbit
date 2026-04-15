package me.nebula.orbit.commands

import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.PlayerReportMessage
import me.nebula.gravity.property.NetworkProperties
import me.nebula.gravity.property.PropertyStore
import me.nebula.gravity.report.ReportStatus
import me.nebula.gravity.report.ReportStore
import me.nebula.gravity.report.reportByReporterSincePredicate
import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.resolvePlayer
import net.minestom.server.command.builder.Command
import java.time.Instant
import java.time.ZoneOffset

private const val REPORT_DUPLICATE_WINDOW_MS = 3_600_000L

fun reportCommand(): Command = command("report") {
    usage("orbit.report.usage")
    stringArgument("player")
    stringArgument("reason")
    cooldown(30_000)

    onPlayerExecute {
        val targetName = argOrNull("player")
        val reason = argOrNull("reason")
        if (targetName == null || reason == null || reason.length < 3) {
            reply("orbit.report.usage")
            return@onPlayerExecute
        }

        val resolved = resolvePlayer(targetName, player)
        if (resolved == null) {
            reply("orbit.command.player_not_found", "name" to targetName)
            return@onPlayerExecute
        }

        val (targetUuid, targetDisplayName) = resolved

        if (targetUuid == player.uuid) {
            reply("orbit.report.self")
            return@onPlayerExecute
        }

        val maxReports = PropertyStore[NetworkProperties.MAX_REPORTS_PER_DAY]
        val todayStart = Instant.now().atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val todayReports = ReportStore.query(reportByReporterSincePredicate(player.uuid, todayStart))
        if (todayReports.size >= maxReports) {
            reply("orbit.report.limit")
            return@onPlayerExecute
        }

        val now = System.currentTimeMillis()
        val recentDuplicate = todayReports.any {
            it.reportedId == targetUuid &&
                it.status == ReportStatus.PENDING &&
                (now - it.timestamp) < REPORT_DUPLICATE_WINDOW_MS
        }
        if (recentDuplicate) {
            reply("orbit.report.duplicate", "player" to targetDisplayName)
            return@onPlayerExecute
        }

        val gameStartTime = (Orbit.mode as? GameMode)?.gameStartTime?.takeIf { it > 0L }
        val replayRef = gameStartTime?.let { "${Orbit.serverName}-$it" }

        val report = ReportStore.record(
            reporterId = player.uuid,
            reporterName = player.username,
            reportedId = targetUuid,
            reportedName = targetDisplayName,
            reason = reason.take(256),
            serverName = Orbit.serverName,
        )

        NetworkMessenger.publish(PlayerReportMessage(
            reportId = report.id,
            reporterId = player.uuid,
            reporterName = player.username,
            reportedId = targetUuid,
            reportedName = targetDisplayName,
            reason = reason.take(256),
            serverName = Orbit.serverName,
            gameMode = Orbit.gameMode,
            replayRef = replayRef,
        ))

        reply("orbit.report.submitted", "player" to targetDisplayName)
    }
}
