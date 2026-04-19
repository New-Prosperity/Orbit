package me.nebula.orbit.config

import me.nebula.orbit.utils.commandbuilder.command
import net.minestom.server.command.builder.Command

fun configCommand(): Command = command("config") {
    permission("nebula.admin.config")
    onPlayerExecute {
        ConfigMainMenu.open(player)
    }
}

fun preferencesCommand(): Command = command("preferences") {
    aliases("prefs")
    onPlayerExecute {
        PreferencesMenu.open(player)
    }
}
