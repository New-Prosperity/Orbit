package me.nebula.orbit.commands

import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.utils.commandbuilder.command
import net.minestom.server.command.builder.Command

internal fun orbitGameMode(): GameMode? = Orbit.mode as? GameMode

fun orbitCommand(): Command = command("orbit") {
    permission("orbit.admin")

    installTestSubcommands()
    installStatusSubcommands()
    installAdminSubcommands()
    installVariantSubcommands()
    installMapSubcommands()

    onPlayerExecute {
        replyMM("<gold><bold>Orbit</bold></gold> <dark_gray>- Admin command")
        replyMM("<white> /orbit status <dark_gray>- Server status")
        replyMM("<white> /orbit mode <dark_gray>- Game mode info")
        replyMM("<white> /orbit test <dark_gray>- Gametest runner")
        replyMM("<white> /orbit fill <dark_gray>- Lobby filler management")
        replyMM("<white> /orbit variant <dark_gray>- Inspect / force game variant")
        replyMM("<white> /orbit map <dark_gray>- Map list / scan / convert")
        replyMM("<white> /orbit reload <dark_gray>- Reload configuration")
    }
}
