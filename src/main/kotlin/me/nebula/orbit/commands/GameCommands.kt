package me.nebula.orbit.commands

import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.suggestPlayers
import me.nebula.orbit.utils.matchresult.matchResult
import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandManager
import net.minestom.server.entity.Player
import me.nebula.gravity.translation.Keys

private fun gameMode(): GameMode? = Orbit.mode as? GameMode

private fun resolveOnline(name: String): Player? =
    MinecraftServer.getConnectionManager().findOnlinePlayer(name)

fun installGameCommands(commandManager: CommandManager) {
    listOf(
        eliminateCommand(),
        reviveCommand(),
        reconnectCommand(),
        forceStartCommand(),
        forceEndCommand(),
        phaseCommand(),
        aliveCommand(),
        lastDeathCommand(),
    ).forEach(commandManager::register)
}

private fun eliminateCommand() = command("eliminate") {
    permission("orbit.command.eliminate")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last()) else emptyList()
    }
    onPlayerExecute {
        val gm = gameMode() ?: run {
            player.sendMessage(player.translate(Keys.Orbit.Command.Game.NotInGameMode))
            return@onPlayerExecute
        }
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMessage(player.translate(Keys.Orbit.Command.Eliminate.Usage))
            return@onPlayerExecute
        }
        val target = resolveOnline(cmdArgs[0]) ?: run {
            player.sendMessage(player.translate(Keys.Orbit.Command.PlayerNotFound, "name" to cmdArgs[0]))
            return@onPlayerExecute
        }
        if (gm.phase != GamePhase.PLAYING) {
            player.sendMessage(player.translate(Keys.Orbit.Command.Game.NotPlaying))
            return@onPlayerExecute
        }
        if (!gm.tracker.isAlive(target.uuid)) {
            player.sendMessage(player.translate(Keys.Orbit.Command.Eliminate.NotAlive, "name" to target.username))
            return@onPlayerExecute
        }
        gm.eliminate(target)
        player.sendMessage(player.translate(Keys.Orbit.Command.Eliminate.Success, "name" to target.username))
    }
}

private fun reviveCommand() = command("revive") {
    permission("orbit.command.revive")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last()) else emptyList()
    }
    onPlayerExecute {
        val gm = gameMode() ?: run {
            player.sendMessage(player.translate(Keys.Orbit.Command.Game.NotInGameMode))
            return@onPlayerExecute
        }
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMessage(player.translate(Keys.Orbit.Command.Revive.Usage))
            return@onPlayerExecute
        }
        val target = resolveOnline(cmdArgs[0]) ?: run {
            player.sendMessage(player.translate(Keys.Orbit.Command.PlayerNotFound, "name" to cmdArgs[0]))
            return@onPlayerExecute
        }
        if (gm.phase != GamePhase.PLAYING) {
            player.sendMessage(player.translate(Keys.Orbit.Command.Game.NotPlaying))
            return@onPlayerExecute
        }
        if (!gm.tracker.isSpectating(target.uuid)) {
            player.sendMessage(player.translate(Keys.Orbit.Command.Revive.NotSpectating, "name" to target.username))
            return@onPlayerExecute
        }
        gm.revive(target)
        player.sendMessage(player.translate(Keys.Orbit.Command.Revive.Success, "name" to target.username))
    }
}

private fun reconnectCommand() = command("reconnect") {
    permission("orbit.command.reconnect")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last()) else emptyList()
    }
    onPlayerExecute {
        val gm = gameMode() ?: run {
            player.sendMessage(player.translate(Keys.Orbit.Command.Game.NotInGameMode))
            return@onPlayerExecute
        }
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMessage(player.translate(Keys.Orbit.Command.Reconnect.Usage))
            return@onPlayerExecute
        }
        val target = resolveOnline(cmdArgs[0]) ?: run {
            player.sendMessage(player.translate(Keys.Orbit.Command.PlayerNotFound, "name" to cmdArgs[0]))
            return@onPlayerExecute
        }
        if (gm.phase != GamePhase.PLAYING) {
            player.sendMessage(player.translate(Keys.Orbit.Command.Game.NotPlaying))
            return@onPlayerExecute
        }
        if (!gm.tracker.isDisconnected(target.uuid)) {
            player.sendMessage(player.translate(Keys.Orbit.Command.Reconnect.NotDisconnected, "name" to target.username))
            return@onPlayerExecute
        }
        gm.forceReconnect(target)
        player.sendMessage(player.translate(Keys.Orbit.Command.Reconnect.Success, "name" to target.username))
    }
}

private fun forceStartCommand() = command("forcestart") {
    permission("orbit.command.forcestart")
    onPlayerExecute {
        val gm = gameMode() ?: run {
            player.sendMessage(player.translate(Keys.Orbit.Command.Game.NotInGameMode))
            return@onPlayerExecute
        }
        if (gm.phase != GamePhase.WAITING && gm.phase != GamePhase.STARTING) {
            player.sendMessage(player.translate(Keys.Orbit.Command.Forcestart.WrongPhase, "phase" to gm.phase.name))
            return@onPlayerExecute
        }
        gm.forceStart()
        player.sendMessage(player.translate(Keys.Orbit.Command.Forcestart.Success))
    }
}

private fun forceEndCommand() = command("forceend") {
    permission("orbit.command.forceend")
    onPlayerExecute {
        val gm = gameMode() ?: run {
            player.sendMessage(player.translate(Keys.Orbit.Command.Game.NotInGameMode))
            return@onPlayerExecute
        }
        if (gm.phase != GamePhase.PLAYING) {
            player.sendMessage(player.translate(Keys.Orbit.Command.Game.NotPlaying))
            return@onPlayerExecute
        }
        gm.forceEnd(matchResult { draw() })
        player.sendMessage(player.translate(Keys.Orbit.Command.Forceend.Success))
    }
}

private fun phaseCommand() = command("phase") {
    permission("orbit.command.phase")
    onPlayerExecute {
        val gm = gameMode() ?: run {
            player.sendMessage(player.translate(Keys.Orbit.Command.Game.NotInGameMode))
            return@onPlayerExecute
        }
        player.sendMessage(player.translate(Keys.Orbit.Command.Phase.Current, "phase" to gm.phase.name))
    }
}

private fun aliveCommand() = command("alive") {
    permission("orbit.command.alive")
    onPlayerExecute {
        val gm = gameMode() ?: run {
            player.sendMessage(player.translate(Keys.Orbit.Command.Game.NotInGameMode))
            return@onPlayerExecute
        }
        val alive = gm.tracker.alive
        val disconnected = gm.tracker.disconnected
        val spectating = gm.tracker.spectating
        player.sendMessage(player.translate(Keys.Orbit.Command.Alive.Summary,
            "alive" to alive.size.toString(),
            "disconnected" to disconnected.size.toString(),
            "spectating" to spectating.size.toString(),
        ))
        if (alive.isNotEmpty()) {
            val names = alive.mapNotNull { uuid ->
                MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.username
                    ?: uuid.toString().take(8)
            }
            player.sendMessage(player.translate(Keys.Orbit.Command.Alive.List, "names" to names.joinToString(", ")))
        }
        if (disconnected.isNotEmpty()) {
            val names = disconnected.map { it.toString().take(8) }
            player.sendMessage(player.translate(Keys.Orbit.Command.Alive.Disconnected, "names" to names.joinToString(", ")))
        }
    }
}

private fun lastDeathCommand() = command("lastdeath") {
    onPlayerExecute {
        val gm = gameMode() ?: run {
            player.sendMessage(player.translate(Keys.Orbit.Command.Game.NotInGameMode))
            return@onPlayerExecute
        }
        val tracker = gm.deathRecapTracker
        val lines = tracker?.getLastRecap(player.uuid)
        if (lines == null) {
            player.sendMessage(player.translate(Keys.Orbit.Deathrecap.NoRecap))
            return@onPlayerExecute
        }
        lines.forEach(player::sendMessage)
    }
}
