package me.nebula.orbit.commands

import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.commandbuilder.CommandBuilderDsl
import me.nebula.orbit.utils.tpsmonitor.TPSMonitor
import net.minestom.server.MinecraftServer

internal fun CommandBuilderDsl.installStatusSubcommands() {
    subCommand("status") {
        onPlayerExecute {
            val instances = MinecraftServer.getInstanceManager().instances
            val players = MinecraftServer.getConnectionManager().onlinePlayers
            val runtime = Runtime.getRuntime()
            val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576
            val maxMb = runtime.maxMemory() / 1_048_576
            val tps = "%.1f".format(TPSMonitor.averageTPS)
            val mspt = "%.1f".format(TPSMonitor.averageMspt)

            replyMM("<gold><bold>Orbit Status</bold></gold>")
            replyMM("<gray>Server: <white>${Orbit.serverName}")
            replyMM("<gray>Mode: <white>${Orbit.gameMode ?: "hub"}")
            replyMM("<gray>Instances: <white>${instances.size}")
            replyMM("<gray>Players: <white>${players.size}")
            replyMM("<gray>TPS: <white>$tps <dark_gray>($mspt mspt)")
            replyMM("<gray>Memory: <white>${usedMb}MB <dark_gray>/ ${maxMb}MB")

            val gm = orbitGameMode()
            if (gm != null) {
                replyMM("<gray>Phase: <white>${gm.phase.name}")
                replyMM("<gray>Alive: <white>${gm.tracker.aliveCount} <dark_gray>/ ${gm.tracker.size}")
            }
        }
    }

    subCommand("mode") {
        onPlayerExecute {
            val gm = orbitGameMode()
            if (gm == null) {
                replyMM("<gray>Mode: <white>${Orbit.gameMode ?: "hub"} <dark_gray>(not a GameMode)")
                return@onPlayerExecute
            }

            replyMM("<gold><bold>Game Mode</bold></gold>")
            replyMM("<gray>Type: <white>${Orbit.gameMode ?: "unknown"}")
            replyMM("<gray>Map: <white>${gm.settings.mapName ?: "none"}")
            replyMM("<gray>Phase: <white>${gm.phase.name}")
            replyMM("<gray>Players: <white>${gm.tracker.size}")
            replyMM("<gray>Alive: <white>${gm.tracker.aliveCount}")
            replyMM("<gray>Spectating: <white>${gm.tracker.spectating.size}")
            replyMM("<gray>Disconnected: <white>${gm.tracker.disconnected.size}")
            replyMM("<gray>Max Players: <white>${gm.maxPlayers}")
            replyMM("<gray>Initial Players: <white>${gm.initialPlayerCount}")

            if (gm.isTeamMode) {
                val teams = gm.tracker.allTeams()
                replyMM("<gray>Teams: <white>${teams.size}")
                for (team in teams) {
                    val alive = gm.tracker.aliveInTeam(team).size
                    val total = gm.tracker.teamMembers(team).size
                    val score = "%.0f".format(gm.tracker.teamScoreOf(team))
                    replyMM("<gray> $team: <white>$alive<dark_gray>/$total alive <dark_gray>(score: $score)")
                }
            }

            if (gm.isOvertime) replyMM("<red>OVERTIME active")
            if (gm.isSuddenDeath) replyMM("<red>SUDDEN DEATH active")
        }
    }
}
