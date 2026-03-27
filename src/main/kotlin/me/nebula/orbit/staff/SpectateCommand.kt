package me.nebula.orbit.staff

import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.resolvePlayer
import net.minestom.server.command.builder.Command

fun spectateCommand(): Command = command("spectate") {
    permission("staff.spectate")
    playerArgument("player")

    onPlayerExecute {
        val targetName = argOrNull("player")

        if (targetName == null) {
            if (StaffSpectateManager.isSpectating(player)) {
                StaffSpectateManager.unspectate(player)
                reply("orbit.spectate.stopped")
            } else {
                reply("orbit.spectate.usage")
            }
            return@onPlayerExecute
        }

        if (targetName.equals(player.username, ignoreCase = true)) {
            reply("orbit.spectate.self")
            return@onPlayerExecute
        }

        val resolved = resolvePlayer(targetName, player) ?: resolvePlayer(targetName)
        if (resolved == null) {
            reply("orbit.command.player_not_found", "name" to targetName)
            return@onPlayerExecute
        }

        if (resolved.first == player.uuid) {
            reply("orbit.spectate.self")
            return@onPlayerExecute
        }

        val currentTarget = StaffSpectateManager.getTarget(player)
        if (currentTarget == resolved.first) {
            StaffSpectateManager.unspectate(player)
            reply("orbit.spectate.stopped")
            return@onPlayerExecute
        }

        if (currentTarget != null) {
            StaffSpectateManager.switchTarget(player, resolved.first)
            reply("orbit.spectate.switched", "player" to resolved.second)
        } else {
            StaffSpectateManager.spectate(player, resolved.first)
            reply("orbit.spectate.started", "player" to resolved.second)
        }
    }
}
