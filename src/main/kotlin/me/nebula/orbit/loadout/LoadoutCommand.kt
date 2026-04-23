package me.nebula.orbit.loadout

import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.gameModeArgument
import net.minestom.server.command.builder.Command

fun loadoutCommand(): Command = command("loadout") {
    gameModeArgument("mode")
    onPlayerExecute {
        val explicit = argOrNull("mode")
        val mode = explicit ?: Orbit.gameMode
        if (mode.isNullOrBlank()) {
            replyMM("<red>Not in a game mode — specify one: <white>/loadout <mode>")
            return@onPlayerExecute
        }
        LoadoutMenu.open(player, mode)
    }
}
