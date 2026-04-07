package me.nebula.orbit.commands

import me.nebula.orbit.Orbit
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.chat.sendMM
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.suggestPlayers
import me.nebula.orbit.utils.matchresult.matchResult
import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandManager
import net.minestom.server.entity.Player

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
            player.sendMM("<red>Not in a game mode")
            return@onPlayerExecute
        }
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMM("<red>Usage: /eliminate <player>")
            return@onPlayerExecute
        }
        val target = resolveOnline(cmdArgs[0]) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs[0]}")
            return@onPlayerExecute
        }
        if (gm.phase != GamePhase.PLAYING) {
            player.sendMM("<red>Game is not in PLAYING phase")
            return@onPlayerExecute
        }
        if (!gm.tracker.isAlive(target.uuid)) {
            player.sendMM("<red>${target.username} is not alive")
            return@onPlayerExecute
        }
        gm.eliminate(target)
        player.sendMM("<green>Eliminated <white>${target.username}")
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
            player.sendMM("<red>Not in a game mode")
            return@onPlayerExecute
        }
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMM("<red>Usage: /revive <player>")
            return@onPlayerExecute
        }
        val target = resolveOnline(cmdArgs[0]) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs[0]}")
            return@onPlayerExecute
        }
        if (gm.phase != GamePhase.PLAYING) {
            player.sendMM("<red>Game is not in PLAYING phase")
            return@onPlayerExecute
        }
        if (!gm.tracker.isSpectating(target.uuid)) {
            player.sendMM("<red>${target.username} is not spectating")
            return@onPlayerExecute
        }
        gm.revive(target)
        player.sendMM("<green>Revived <white>${target.username}")
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
            player.sendMM("<red>Not in a game mode")
            return@onPlayerExecute
        }
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMM("<red>Usage: /reconnect <player>")
            return@onPlayerExecute
        }
        val target = resolveOnline(cmdArgs[0]) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs[0]}")
            return@onPlayerExecute
        }
        if (gm.phase != GamePhase.PLAYING) {
            player.sendMM("<red>Game is not in PLAYING phase")
            return@onPlayerExecute
        }
        if (!gm.tracker.isDisconnected(target.uuid)) {
            player.sendMM("<red>${target.username} is not in disconnected state")
            return@onPlayerExecute
        }
        gm.forceReconnect(target)
        player.sendMM("<green>Force reconnected <white>${target.username}")
    }
}

private fun forceStartCommand() = command("forcestart") {
    permission("orbit.command.forcestart")
    onPlayerExecute {
        val gm = gameMode() ?: run {
            player.sendMM("<red>Not in a game mode")
            return@onPlayerExecute
        }
        if (gm.phase != GamePhase.WAITING && gm.phase != GamePhase.STARTING) {
            player.sendMM("<red>Game is already in ${gm.phase.name} phase")
            return@onPlayerExecute
        }
        gm.forceStart()
        player.sendMM("<green>Game force started")
    }
}

private fun forceEndCommand() = command("forceend") {
    permission("orbit.command.forceend")
    onPlayerExecute {
        val gm = gameMode() ?: run {
            player.sendMM("<red>Not in a game mode")
            return@onPlayerExecute
        }
        if (gm.phase != GamePhase.PLAYING) {
            player.sendMM("<red>Game is not in PLAYING phase")
            return@onPlayerExecute
        }
        gm.forceEnd(matchResult { draw() })
        player.sendMM("<green>Game force ended")
    }
}

private fun phaseCommand() = command("phase") {
    permission("orbit.command.phase")
    onPlayerExecute {
        val gm = gameMode() ?: run {
            player.sendMM("<red>Not in a game mode")
            return@onPlayerExecute
        }
        player.sendMM("<green>Current phase: <white>${gm.phase.name}")
    }
}

private fun aliveCommand() = command("alive") {
    permission("orbit.command.alive")
    onPlayerExecute {
        val gm = gameMode() ?: run {
            player.sendMM("<red>Not in a game mode")
            return@onPlayerExecute
        }
        val alive = gm.tracker.alive
        val disconnected = gm.tracker.disconnected
        val spectating = gm.tracker.spectating
        player.sendMM("<green>Alive: <white>${alive.size}<gray> | <green>Disconnected: <white>${disconnected.size}<gray> | <green>Spectating: <white>${spectating.size}")
        if (alive.isNotEmpty()) {
            val names = alive.mapNotNull { uuid ->
                MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.username
                    ?: uuid.toString().take(8)
            }
            player.sendMM("<green>Alive players: <white>${names.joinToString(", ")}")
        }
        if (disconnected.isNotEmpty()) {
            val names = disconnected.map { it.toString().take(8) }
            player.sendMM("<yellow>Disconnected: <white>${names.joinToString(", ")}")
        }
    }
}

private fun lastDeathCommand() = command("lastdeath") {
    onPlayerExecute {
        val gm = gameMode() ?: run {
            player.sendMM("<red>Not in a game mode")
            return@onPlayerExecute
        }
        val tracker = gm.deathRecapTracker
        val lines = tracker?.getLastRecap(player.uuid)
        if (lines == null) {
            player.sendMessage(player.translate("orbit.deathrecap.no_recap"))
            return@onPlayerExecute
        }
        lines.forEach(player::sendMessage)
    }
}
