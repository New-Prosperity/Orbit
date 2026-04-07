package me.nebula.orbit.staff

import me.nebula.gravity.anticheat.FlaggedPlayerStore
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.resolvePlayer
import me.nebula.orbit.utils.commandbuilder.suggestPlayers
import net.minestom.server.command.builder.Command

fun inspectCommand(): Command = command("inspect") {
    permission("staff.inspect")
    playerArgument("player")

    tabComplete { player, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last(), player) else emptyList()
    }

    onPlayerExecute {
        val targetName = argOrNull("player")
        if (targetName == null) {
            reply("orbit.inspect.usage")
            return@onPlayerExecute
        }

        val resolved = resolvePlayer(targetName, player) ?: resolvePlayer(targetName)
        if (resolved == null) {
            reply("orbit.command.player_not_found", "name" to targetName)
            return@onPlayerExecute
        }

        val (targetUuid, resolvedName) = resolved
        val flagData = FlaggedPlayerStore.load(targetUuid)

        if (flagData != null && flagData.flags.isNotEmpty()) {
            replyMM("<yellow>--- Flags for <white>$resolvedName <yellow>---")
            replyMM("<yellow>Total flags: <white>${flagData.totalFlags}")
            for (flag in flagData.flags.takeLast(10)) {
                replyMM("<gray> - <white>${flag.checkType} <gray>| ${flag.violations} violations <gray>| ${flag.serverName} <gray>| ${flag.gameMode ?: "hub"}")
            }
            replyMM("<yellow>---")
        } else {
            replyMM("<gray>$resolvedName is not flagged.")
        }

        StaffSpectateManager.spectate(player, targetUuid)
        reply("orbit.spectate.started", "player" to resolvedName)
    }
}
