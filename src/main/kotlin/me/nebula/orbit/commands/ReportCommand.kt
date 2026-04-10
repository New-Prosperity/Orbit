package me.nebula.orbit.commands

import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.PlayerReportMessage
import me.nebula.gravity.report.ReportStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.resolvePlayer
import net.minestom.server.command.builder.Command

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
        ))

        reply("orbit.report.submitted", "player" to targetDisplayName)
    }
}
