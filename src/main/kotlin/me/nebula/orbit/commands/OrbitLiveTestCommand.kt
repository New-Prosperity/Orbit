package me.nebula.orbit.commands

import me.nebula.ether.utils.parse.enumValueOfOrNull
import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.utils.commandbuilder.CommandBuilderDsl
import me.nebula.orbit.utils.commandbuilder.gamePhaseArgument
import me.nebula.orbit.utils.commandbuilder.liveSessionBotArgument
import me.nebula.orbit.utils.commandbuilder.liveSessionPlayerArgument
import me.nebula.orbit.utils.gametest.GameTestRunner
import me.nebula.orbit.utils.matchresult.matchResult
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.damage.DamageType

internal fun CommandBuilderDsl.installLiveTestSubcommands() {
    installLiveControlSubcommands()
    installLiveAssertSubcommands()
    installLiveBotSubcommands()
    installLiveConnectionSubcommands()
    installLiveForceSubcommands()

    onPlayerExecute {
        val existing = GameTestRunner.getLiveSession(player)
        if (existing != null) {
            replyMM("<yellow>You already have an active live session.")
            replyMM("<gray>Bots: <white>${existing.botUuids.size} <dark_gray>| Phase: <white>${existing.gameMode.phase.name}")
            return@onPlayerExecute
        }
        GameTestRunner.startLive(player)
    }
}

private fun CommandBuilderDsl.installLiveControlSubcommands() {
    subCommand("stop") {
        onPlayerExecute { GameTestRunner.stopLive(player) }
    }

    subCommand("spawn") {
        intArgument("count")
        onPlayerExecute {
            val session = GameTestRunner.getLiveSession(player)
            if (session == null) {
                replyMM("<red>No active live session. Start one with <white>/orbit test live")
                return@onPlayerExecute
            }
            val count = intArgOrNull("count") ?: 1
            if (count < 1 || count > 100) {
                replyMM("<red>Count must be between 1 and 100.")
                return@onPlayerExecute
            }
            session.spawnBots(count)
        }
    }

    subCommand("fill") {
        intArgument("count")
        onPlayerExecute {
            val session = GameTestRunner.getLiveSession(player)
            if (session == null) {
                replyMM("<red>No active live session. Start one with <white>/orbit test live")
                return@onPlayerExecute
            }
            val target = intArgOrNull("count")
            if (target == null || target < 1) {
                replyMM("<red>Usage: /orbit test live fill <count>")
                return@onPlayerExecute
            }
            val currentTotal = session.instance.players.size
            val needed = target - currentTotal
            if (needed <= 0) {
                replyMM("<yellow>Already at $currentTotal players (target: $target)")
                return@onPlayerExecute
            }
            session.spawnBots(needed)
        }
    }

    subCommand("bots") {
        onPlayerExecute {
            val session = GameTestRunner.getLiveSession(player)
            if (session == null) {
                replyMM("<red>No active live session.")
                return@onPlayerExecute
            }
            val bots = session.botPlayers
            if (bots.isEmpty()) {
                replyMM("<gray>No bots spawned.")
                return@onPlayerExecute
            }
            replyMM("<gray>Live bots (${bots.size}):")
            for (bot in bots) {
                val alive = session.gameMode.tracker.isAlive(bot.uuid)
                val state = if (alive) "<green>alive" else "<red>eliminated"
                replyMM("<white> ${bot.username} <dark_gray>- $state")
            }
        }
    }

    subCommand("kill") {
        liveSessionPlayerArgument("target")
        onPlayerExecute {
            val session = GameTestRunner.getLiveSession(player)
            if (session == null) {
                replyMM("<red>No active live session.")
                return@onPlayerExecute
            }
            val targetName = argOrNull("target")
            if (targetName == null) {
                replyMM("<red>Usage: /orbit test live kill <player>")
                return@onPlayerExecute
            }
            val target = session.instance.players.firstOrNull { it.username.equals(targetName, ignoreCase = true) }
            if (target == null) {
                replyMM("<red>Player not found: $targetName")
                return@onPlayerExecute
            }
            session.gameMode.handleDeath(target, null)
            replyMM("<yellow>Killed ${target.username}")
        }
    }

    subCommand("damage") {
        liveSessionPlayerArgument("target")
        floatArgument("amount")
        onPlayerExecute {
            val session = GameTestRunner.getLiveSession(player)
            if (session == null) {
                replyMM("<red>No active live session.")
                return@onPlayerExecute
            }
            val targetName = argOrNull("target")
            if (targetName == null) {
                replyMM("<red>Usage: /orbit test live damage <player> <amount>")
                return@onPlayerExecute
            }
            val amount = runCatching { floatArg("amount") }.getOrElse {
                replyMM("<red>Usage: /orbit test live damage <player> <amount>")
                return@onPlayerExecute
            }
            val target = session.instance.players.firstOrNull { it.username.equals(targetName, ignoreCase = true) }
            if (target == null) {
                replyMM("<red>Player not found: $targetName")
                return@onPlayerExecute
            }
            target.damage(DamageType.GENERIC, amount)
            replyMM("<yellow>Damaged ${target.username} for $amount")
        }
    }

    subCommand("phase") {
        onPlayerExecute {
            val session = GameTestRunner.getLiveSession(player)
            if (session == null) {
                replyMM("<red>No active live session.")
                return@onPlayerExecute
            }
            replyMM("<gray>Current phase: <white>${session.gameMode.phase.name}")
        }
    }
}

private fun CommandBuilderDsl.installLiveAssertSubcommands() {
    subCommand("assert") {
        subCommand("alive") {
            intArgument("count")
            onPlayerExecute {
                val session = GameTestRunner.getLiveSession(player)
                if (session == null) {
                    replyMM("<red>No active live session.")
                    return@onPlayerExecute
                }
                val expected = intArgOrNull("count")
                if (expected == null) {
                    replyMM("<red>Usage: /orbit test live assert alive <count>")
                    return@onPlayerExecute
                }
                val actual = session.gameMode.tracker.aliveCount
                if (actual == expected) {
                    replyMM("<green>PASS: alive count is $actual")
                } else {
                    replyMM("<red>FAIL: expected $expected alive but found $actual")
                }
            }
        }

        subCommand("phase") {
            gamePhaseArgument("phase")
            onPlayerExecute {
                val session = GameTestRunner.getLiveSession(player)
                if (session == null) {
                    replyMM("<red>No active live session.")
                    return@onPlayerExecute
                }
                val phaseName = argOrNull("phase")
                if (phaseName == null) {
                    replyMM("<red>Usage: /orbit test live assert phase <phase>")
                    return@onPlayerExecute
                }
                val expected = enumValueOfOrNull<GamePhase>(phaseName.uppercase())
                if (expected == null) {
                    replyMM("<red>Unknown phase: $phaseName. Valid: ${GamePhase.entries.joinToString { it.name }}")
                    return@onPlayerExecute
                }
                val actual = session.gameMode.phase
                if (actual == expected) {
                    replyMM("<green>PASS: phase is ${actual.name}")
                } else {
                    replyMM("<red>FAIL: expected ${expected.name} but phase is ${actual.name}")
                }
            }
        }

        onPlayerExecute {
            replyMM("<gray>Usage:")
            replyMM("<white> /orbit test live assert alive <count>")
            replyMM("<white> /orbit test live assert phase <phase>")
        }
    }
}

private fun CommandBuilderDsl.installLiveConnectionSubcommands() {
    subCommand("disconnect") {
        liveSessionBotArgument("target")
        onPlayerExecute {
            val session = GameTestRunner.getLiveSession(player)
            if (session == null) {
                replyMM("<red>No active live session.")
                return@onPlayerExecute
            }
            val targetName = argOrNull("target")
            if (targetName == null) {
                replyMM("<red>Usage: /orbit test live disconnect <player>")
                return@onPlayerExecute
            }
            val target = session.botPlayers.firstOrNull { it.username.equals(targetName, ignoreCase = true) }
            if (target == null) {
                replyMM("<red>Bot not found: $targetName")
                return@onPlayerExecute
            }
            target.playerConnection.disconnect()
            MinecraftServer.getConnectionManager().removePlayer(target.playerConnection)
            replyMM("<yellow>Disconnected ${target.username}")
        }
    }

    subCommand("reconnect") {
        liveSessionBotArgument("target")
        onPlayerExecute {
            val session = GameTestRunner.getLiveSession(player)
            if (session == null) {
                replyMM("<red>No active live session.")
                return@onPlayerExecute
            }
            val targetName = argOrNull("target")
            if (targetName == null) {
                replyMM("<red>Usage: /orbit test live reconnect <player>")
                return@onPlayerExecute
            }
            val target = session.instance.players.firstOrNull { it.username.equals(targetName, ignoreCase = true) }
            if (target != null) {
                val gm = session.gameMode
                if (gm.tracker.isDisconnected(target.uuid)) {
                    gm.forceReconnect(target)
                    replyMM("<green>Reconnected ${target.username}")
                } else {
                    replyMM("<red>${target.username} is not in disconnected state")
                }
            } else {
                replyMM("<yellow>Spawning new bot to simulate reconnect...")
                session.spawnBots(1)
                replyMM("<green>Spawned replacement bot")
            }
        }
    }

    subCommand("mutualkill") {
        liveSessionPlayerArgument("player1")
        liveSessionPlayerArgument("player2")
        onPlayerExecute {
            val session = GameTestRunner.getLiveSession(player)
            if (session == null) {
                replyMM("<red>No active live session.")
                return@onPlayerExecute
            }
            val p1Name = argOrNull("player1")
            val p2Name = argOrNull("player2")
            if (p1Name == null || p2Name == null) {
                replyMM("<red>Usage: /orbit test live mutualkill <player1> <player2>")
                return@onPlayerExecute
            }
            val p1 = session.instance.players.firstOrNull { it.username.equals(p1Name, ignoreCase = true) }
            val p2 = session.instance.players.firstOrNull { it.username.equals(p2Name, ignoreCase = true) }
            if (p1 == null) {
                replyMM("<red>Player not found: $p1Name")
                return@onPlayerExecute
            }
            if (p2 == null) {
                replyMM("<red>Player not found: $p2Name")
                return@onPlayerExecute
            }
            val gm = session.gameMode
            MinecraftServer.getSchedulerManager().buildTask {
                gm.handleDeath(p1, p2)
                gm.handleDeath(p2, p1)
            }.schedule()
            replyMM("<yellow>Mutual kill triggered for ${p1.username} and ${p2.username}")
        }
    }

    subCommand("alldisconnect") {
        onPlayerExecute {
            val session = GameTestRunner.getLiveSession(player)
            if (session == null) {
                replyMM("<red>No active live session.")
                return@onPlayerExecute
            }
            val gm = session.gameMode
            val bots = session.botPlayers.toList()
            for (bot in bots) {
                if (gm.tracker.isAlive(bot.uuid)) {
                    gm.eliminate(bot)
                }
            }
            replyMM("<yellow>All bots eliminated (${bots.size} bots)")
        }
    }
}

private fun CommandBuilderDsl.installLiveForceSubcommands() {
    subCommand("forcestart") {
        onPlayerExecute {
            val session = GameTestRunner.getLiveSession(player)
            if (session == null) {
                replyMM("<red>No active live session.")
                return@onPlayerExecute
            }
            val gm = session.gameMode
            if (gm.phase != GamePhase.WAITING && gm.phase != GamePhase.STARTING) {
                replyMM("<red>Game is already in ${gm.phase.name} phase")
                return@onPlayerExecute
            }
            gm.forceStart()
            replyMM("<green>Game force started via live session")
        }
    }

    subCommand("forcephase") {
        gamePhaseArgument("phase")
        onPlayerExecute {
            val session = GameTestRunner.getLiveSession(player)
            if (session == null) {
                replyMM("<red>No active live session.")
                return@onPlayerExecute
            }
            val phaseName = argOrNull("phase")
            if (phaseName == null) {
                replyMM("<red>Usage: /orbit test live forcephase <phase>")
                return@onPlayerExecute
            }
            val targetPhase = enumValueOfOrNull<GamePhase>(phaseName.uppercase())
            if (targetPhase == null) {
                replyMM("<red>Unknown phase: $phaseName. Valid: ${GamePhase.entries.joinToString { it.name }}")
                return@onPlayerExecute
            }
            val gm = session.gameMode
            when (targetPhase) {
                GamePhase.PLAYING -> gm.forceStart()
                GamePhase.ENDING -> gm.forceEnd(matchResult { draw() })
                else -> replyMM("<red>Can only force transition to PLAYING or ENDING")
            }
            if (targetPhase == GamePhase.PLAYING || targetPhase == GamePhase.ENDING) {
                replyMM("<green>Forced phase transition to ${targetPhase.name}")
            }
        }
    }
}
