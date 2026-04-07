package me.nebula.orbit.staff

import me.nebula.gravity.anticheat.unflag
import me.nebula.gravity.audit.AuditAction
import me.nebula.gravity.audit.AuditStore
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.resolvePlayer
import me.nebula.orbit.utils.commandbuilder.suggestPlayers
import net.minestom.server.command.builder.Command

fun unflagCommand(): Command = command("unflag") {
    permission("staff.unflag")
    playerArgument("player")

    tabComplete { player, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last(), player) else emptyList()
    }

    onPlayerExecute {
        val targetName = argOrNull("player")
        if (targetName == null) {
            reply("orbit.unflag.usage")
            return@onPlayerExecute
        }

        val resolved = resolvePlayer(targetName, player) ?: resolvePlayer(targetName)
        if (resolved == null) {
            reply("orbit.command.player_not_found", "name" to targetName)
            return@onPlayerExecute
        }

        val (targetId, resolvedName) = resolved
        val previous = unflag(targetId)

        if (previous == null) {
            reply("orbit.unflag.not_flagged", "player" to resolvedName)
            return@onPlayerExecute
        }

        AuditStore.log(
            actorId = player.uuid,
            actorName = player.username,
            action = AuditAction.ANTICHEAT_UNFLAG,
            targetId = targetId,
            targetName = resolvedName,
            details = "Cleared ${previous.totalFlags} flags (${previous.flags.map { it.checkType }.distinct().joinToString()})",
            source = "orbit",
        )

        reply("orbit.unflag.success", "player" to resolvedName, "count" to previous.totalFlags.toString())
    }
}
