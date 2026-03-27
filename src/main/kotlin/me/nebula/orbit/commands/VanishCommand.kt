package me.nebula.orbit.commands

import me.nebula.gravity.rank.RankManager
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.vanish.VanishManager
import net.minestom.server.command.builder.Command

fun vanishCommand(): Command = command("vanish") {
    permission("staff.vanish")
    playerArgument("player")

    onPlayerExecute {
        val targetName = argOrNull("player")
        if (targetName != null) {
            if (!RankManager.hasPermission(player.uuid, "staff.vanish.others")) {
                reply("orbit.vanish.no_permission")
                return@onPlayerExecute
            }
            val target = targetPlayer() ?: run {
                reply("orbit.command.player_not_found", "name" to targetName)
                return@onPlayerExecute
            }
            val state = VanishManager.toggle(target)
            if (state) reply("orbit.vanish.other_enabled", "player" to target.username)
            else reply("orbit.vanish.other_disabled", "player" to target.username)
            return@onPlayerExecute
        }

        if (VanishManager.gameParticipantCheck(player)) {
            reply("orbit.vanish.in_game")
            return@onPlayerExecute
        }

        val state = VanishManager.toggle(player)
        if (state) reply("orbit.vanish.enabled")
        else reply("orbit.vanish.disabled")
    }
}
