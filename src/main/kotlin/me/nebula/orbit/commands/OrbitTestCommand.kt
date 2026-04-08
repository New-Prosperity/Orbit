package me.nebula.orbit.commands

import me.nebula.orbit.mode.game.GamePhase
import me.nebula.orbit.utils.botai.BotAI
import me.nebula.orbit.utils.commandbuilder.CommandBuilderDsl
import me.nebula.orbit.utils.gametest.GameTestRegistry
import me.nebula.orbit.utils.gametest.GameTestRunner
import me.nebula.orbit.utils.gametest.TestBehavior
import me.nebula.orbit.utils.gametest.TestBotPresets
import me.nebula.orbit.utils.matchresult.matchResult
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.damage.DamageType
import kotlin.time.Duration.Companion.seconds

internal fun CommandBuilderDsl.installTestSubcommands() {
    subCommand("test") {
        subCommand("run") {
            wordArgument("testId")
            tabComplete { _, input ->
                val prefix = input.substringAfterLast(" ")
                GameTestRegistry.ids().filter { it.startsWith(prefix, ignoreCase = true) }
            }
            onPlayerExecute {
                val testId = argOrNull("testId")
                if (testId == null) {
                    replyMM("<red>Usage: /orbit test run <testId>")
                    return@onPlayerExecute
                }
                GameTestRunner.run(player, testId)
            }
        }

        subCommand("runall") {
            onPlayerExecute {
                GameTestRunner.runAll(player)
            }
        }

        subCommand("runtag") {
            wordArgument("tag")
            tabComplete { _, input ->
                val prefix = input.substringAfterLast(" ")
                GameTestRegistry.tags().filter { it.startsWith(prefix, ignoreCase = true) }.toList()
            }
            onPlayerExecute {
                val tag = argOrNull("tag")
                if (tag == null) {
                    replyMM("<red>Usage: /orbit test runtag <tag>")
                    return@onPlayerExecute
                }
                GameTestRunner.runByTag(player, tag)
            }
        }

        subCommand("cancel") {
            onPlayerExecute {
                GameTestRunner.cancel(player)
            }
        }

        subCommand("list") {
            onPlayerExecute {
                val tests = GameTestRegistry.all()
                if (tests.isEmpty()) {
                    replyMM("<red>No tests registered.")
                    return@onPlayerExecute
                }
                replyMM("<gray>Registered tests (${tests.size}):")
                for ((id, definition) in tests) {
                    val desc = if (definition.description.isNotEmpty()) " <dark_gray>- ${definition.description}" else ""
                    val liveTag = if (definition.live) " <yellow>[live]" else ""
                    val tagsLabel = if (definition.tags.isNotEmpty()) " <aqua>[${definition.tags.joinToString(",")}]" else ""
                    replyMM("<white> $id$desc$liveTag$tagsLabel <dark_gray>[${definition.playerCount}p, ${definition.timeout}]")
                }
            }
        }

        installLiveSubcommands()

        onPlayerExecute {
            replyMM("<gray>Usage:")
            replyMM("<white> /orbit test run <testId> <dark_gray>- Run a specific test")
            replyMM("<white> /orbit test runall <dark_gray>- Run all registered tests")
            replyMM("<white> /orbit test runtag <tag> <dark_gray>- Run tests by tag")
            replyMM("<white> /orbit test cancel <dark_gray>- Cancel running test")
            replyMM("<white> /orbit test list <dark_gray>- List available tests")
            replyMM("<white> /orbit test live <dark_gray>- Start live test session")
        }
    }
}

private fun CommandBuilderDsl.installLiveSubcommands() {
    subCommand("live") {
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
            wordArgument("target")
            tabComplete { sender, input ->
                val prefix = input.substringAfterLast(" ")
                val session = GameTestRunner.getLiveSession(sender) ?: return@tabComplete emptyList()
                session.instance.players.map { it.username }.filter { it.startsWith(prefix, ignoreCase = true) }
            }
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
            wordArgument("target")
            floatArgument("amount")
            tabComplete { sender, input ->
                val prefix = input.substringAfterLast(" ")
                val session = GameTestRunner.getLiveSession(sender) ?: return@tabComplete emptyList()
                session.instance.players.map { it.username }.filter { it.startsWith(prefix, ignoreCase = true) }
            }
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
                wordArgument("phase")
                tabComplete { _, input ->
                    val prefix = input.substringAfterLast(" ")
                    GamePhase.entries.map { it.name }
                        .filter { it.startsWith(prefix, ignoreCase = true) }
                }
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
                    val expected = runCatching { GamePhase.valueOf(phaseName.uppercase()) }.getOrNull()
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

        subCommand("behavior") {
            wordArgument("target")
            wordArgument("behavior")
            tabComplete { sender, input ->
                val parts = input.trim().split("\\s+".toRegex())
                val session = GameTestRunner.getLiveSession(sender) ?: return@tabComplete emptyList()
                when {
                    parts.size <= 5 -> session.botPlayers.map { it.username }
                    else -> TestBehavior.entries.map { it.name.lowercase() }
                }.filter { it.startsWith(parts.last(), ignoreCase = true) }
            }
            onPlayerExecute {
                val session = GameTestRunner.getLiveSession(player)
                if (session == null) {
                    replyMM("<red>No active live session.")
                    return@onPlayerExecute
                }
                val targetName = argOrNull("target")
                val behaviorName = argOrNull("behavior")
                if (targetName == null || behaviorName == null) {
                    replyMM("<red>Usage: /orbit test live behavior <player> <behavior>")
                    return@onPlayerExecute
                }
                val target = session.instance.players.firstOrNull { it.username.equals(targetName, ignoreCase = true) }
                if (target == null) {
                    replyMM("<red>Player not found: $targetName")
                    return@onPlayerExecute
                }
                val behavior = runCatching { TestBehavior.valueOf(behaviorName.uppercase()) }.getOrNull()
                if (behavior == null) {
                    replyMM("<red>Unknown behavior: $behaviorName. Valid: ${TestBehavior.entries.joinToString { it.name.lowercase() }}")
                    return@onPlayerExecute
                }
                session.setBotBehavior(target, behavior)
                replyMM("<green>Set ${target.username} behavior to ${behavior.name}")
            }
        }

        subCommand("behaviorall") {
            wordArgument("behavior")
            tabComplete { _, input ->
                val prefix = input.substringAfterLast(" ")
                TestBehavior.entries.map { it.name.lowercase() }
                    .filter { it.startsWith(prefix, ignoreCase = true) }
            }
            onPlayerExecute {
                val session = GameTestRunner.getLiveSession(player)
                if (session == null) {
                    replyMM("<red>No active live session.")
                    return@onPlayerExecute
                }
                val behaviorName = argOrNull("behavior")
                if (behaviorName == null) {
                    replyMM("<red>Usage: /orbit test live behaviorall <behavior>")
                    return@onPlayerExecute
                }
                val behavior = runCatching { TestBehavior.valueOf(behaviorName.uppercase()) }.getOrNull()
                if (behavior == null) {
                    replyMM("<red>Unknown behavior: $behaviorName. Valid: ${TestBehavior.entries.joinToString { it.name.lowercase() }}")
                    return@onPlayerExecute
                }
                session.setAllBotBehavior(behavior)
                replyMM("<green>Set all bots' behavior to ${behavior.name}")
            }
        }

        subCommand("ai") {
            wordArgument("target")
            wordArgument("action")
            tabComplete { sender, input ->
                val parts = input.trim().split("\\s+".toRegex())
                val session = GameTestRunner.getLiveSession(sender) ?: return@tabComplete emptyList()
                val aiPresets = listOf("survival", "combat", "pvp", "gatherer", "passive", "miner")
                val personalityNames = listOf("warrior", "survivor", "explorer", "berserker", "builder", "random", "balanced")
                when {
                    parts.size <= 5 -> {
                        val names = session.botPlayers.map { it.username } + listOf("all")
                        names.filter { it.startsWith(parts.last(), ignoreCase = true) }
                    }
                    parts.size == 6 -> {
                        val options = aiPresets + listOf("off", "info", "personality")
                        options.filter { it.startsWith(parts.last(), ignoreCase = true) }
                    }
                    parts.size == 7 && parts[5].equals("personality", ignoreCase = true) -> {
                        personalityNames.filter { it.startsWith(parts.last(), ignoreCase = true) }
                    }
                    else -> emptyList()
                }
            }
            onPlayerExecute {
                val session = GameTestRunner.getLiveSession(player)
                if (session == null) {
                    replyMM("<red>No active live session.")
                    return@onPlayerExecute
                }
                val targetName = argOrNull("target")
                val action = argOrNull("action")
                if (targetName == null || action == null) {
                    replyMM("<red>Usage: /orbit test live ai <player|all> <preset|off|info|personality> [value]")
                    return@onPlayerExecute
                }

                when {
                    action.equals("off", ignoreCase = true) -> {
                        if (targetName.equals("all", ignoreCase = true)) {
                            session.detachAllAI()
                            replyMM("<yellow>Detached AI from all bots")
                        } else {
                            val target = session.instance.players.firstOrNull { it.username.equals(targetName, ignoreCase = true) }
                            if (target == null) { replyMM("<red>Player not found: $targetName"); return@onPlayerExecute }
                            BotAI.detach(target)
                            replyMM("<yellow>Detached AI from ${target.username}")
                        }
                    }
                    action.equals("info", ignoreCase = true) -> {
                        val target = session.instance.players.firstOrNull { it.username.equals(targetName, ignoreCase = true) }
                        if (target == null) { replyMM("<red>Player not found: $targetName"); return@onPlayerExecute }
                        val brain = BotAI.getBrain(target)
                        if (brain == null) {
                            replyMM("<red>${target.username} has no AI attached")
                            return@onPlayerExecute
                        }
                        val p = brain.personality
                        val goal = brain.currentGoal
                        replyMM("<gold>${target.username} AI Status:")
                        replyMM("<gray>  Personality: <white>aggro=${"%.1f".format(p.aggression)}, caution=${"%.1f".format(p.caution)}, resource=${"%.1f".format(p.resourcefulness)}, curious=${"%.1f".format(p.curiosity)}")
                        replyMM("<gray>  Current Goal: <white>${goal?.let { it::class.simpleName } ?: "none"}")
                    }
                    action.equals("personality", ignoreCase = true) -> {
                        replyMM("<red>Usage: /orbit test live ai <player> personality <name>")
                    }
                    else -> {
                        val presets = listOf("survival", "combat", "pvp", "gatherer", "passive", "miner")
                        if (action.lowercase() !in presets) {
                            replyMM("<red>Unknown AI action: $action. Valid: ${presets.joinToString()}, off, info, personality")
                            return@onPlayerExecute
                        }
                        if (targetName.equals("all", ignoreCase = true)) {
                            session.attachAllAI(action.lowercase())
                            replyMM("<green>Attached $action AI to all bots")
                        } else {
                            val target = session.instance.players.firstOrNull { it.username.equals(targetName, ignoreCase = true) }
                            if (target == null) { replyMM("<red>Player not found: $targetName"); return@onPlayerExecute }
                            session.attachAI(target, action.lowercase())
                            replyMM("<green>Attached $action AI to ${target.username}")
                        }
                    }
                }
            }
        }

        subCommand("preset") {
            wordArgument("preset")
            intArgument("count")
            tabComplete { _, input ->
                val prefix = input.substringAfterLast(" ")
                TestBotPresets.names().filter { it.startsWith(prefix, ignoreCase = true) }.toList()
            }
            onPlayerExecute {
                val session = GameTestRunner.getLiveSession(player)
                if (session == null) {
                    replyMM("<red>No active live session.")
                    return@onPlayerExecute
                }
                val presetName = argOrNull("preset")
                val count = intArgOrNull("count")
                if (presetName == null || count == null) {
                    replyMM("<red>Usage: /orbit test live preset <preset> <count>")
                    return@onPlayerExecute
                }
                if (count < 1 || count > 100) {
                    replyMM("<red>Count must be between 1 and 100.")
                    return@onPlayerExecute
                }
                val preset = TestBotPresets.get(presetName)
                if (preset == null) {
                    replyMM("<red>Unknown preset: $presetName. Valid: ${TestBotPresets.names().joinToString()}")
                    return@onPlayerExecute
                }
                session.spawnBots(count, preset)
                replyMM("<green>Spawned $count bot(s) with preset '$presetName'")
            }
        }

        subCommand("ramp") {
            intArgument("count")
            intArgument("intervalTicks")
            onPlayerExecute {
                val session = GameTestRunner.getLiveSession(player)
                if (session == null) {
                    replyMM("<red>No active live session.")
                    return@onPlayerExecute
                }
                val count = intArgOrNull("count")
                if (count == null || count < 1 || count > 100) {
                    replyMM("<red>Usage: /orbit test live ramp <count> [intervalTicks]")
                    return@onPlayerExecute
                }
                val interval = intArgOrNull("intervalTicks") ?: 20
                session.gradualSpawn(count, interval)
            }
        }

        subCommand("stress") {
            intArgument("count")
            intArgument("durationSec")
            onPlayerExecute {
                val session = GameTestRunner.getLiveSession(player)
                if (session == null) {
                    replyMM("<red>No active live session.")
                    return@onPlayerExecute
                }
                val count = intArgOrNull("count")
                val durationSec = intArgOrNull("durationSec")
                if (count == null || durationSec == null) {
                    replyMM("<red>Usage: /orbit test live stress <count> <durationSec>")
                    return@onPlayerExecute
                }
                if (count < 1 || count > 100) {
                    replyMM("<red>Count must be between 1 and 100.")
                    return@onPlayerExecute
                }
                session.stressTest(count, durationSec.seconds)
            }
        }

        subCommand("metrics") {
            onPlayerExecute {
                val session = GameTestRunner.getLiveSession(player)
                if (session == null) {
                    replyMM("<red>No active live session.")
                    return@onPlayerExecute
                }
                val m = session.metrics()
                replyMM("<gold><bold>Live Metrics</bold></gold>")
                replyMM("<gray>Bots: <white>${m.botCount}")
                replyMM("<gray>Total Players: <white>${m.totalPlayers}")
                replyMM("<gray>TPS: <white>${"%.1f".format(m.tps)}")
                replyMM("<gray>Memory: <white>${m.memoryMb}MB")
                replyMM("<gray>Uptime: <white>${m.uptimeMs / 1000}s")
            }
        }

        subCommand("run") {
            wordArgument("testId")
            tabComplete { _, input ->
                val prefix = input.substringAfterLast(" ")
                GameTestRegistry.ids().filter { it.startsWith(prefix, ignoreCase = true) }
            }
            onPlayerExecute {
                val session = GameTestRunner.getLiveSession(player)
                if (session == null) {
                    replyMM("<red>No active live session. Start one with <white>/orbit test live")
                    return@onPlayerExecute
                }
                val testId = argOrNull("testId")
                if (testId == null) {
                    replyMM("<red>Usage: /orbit test live run <testId>")
                    return@onPlayerExecute
                }
                val definition = GameTestRegistry.get(testId)
                if (definition == null) {
                    replyMM("<red>Unknown test: $testId")
                    return@onPlayerExecute
                }
                session.runScript(definition)
            }
        }

        subCommand("disconnect") {
            wordArgument("target")
            tabComplete { sender, input ->
                val prefix = input.substringAfterLast(" ")
                val session = GameTestRunner.getLiveSession(sender) ?: return@tabComplete emptyList()
                session.botPlayers.map { it.username }.filter { it.startsWith(prefix, ignoreCase = true) }
            }
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
            wordArgument("target")
            tabComplete { sender, input ->
                val prefix = input.substringAfterLast(" ")
                val session = GameTestRunner.getLiveSession(sender) ?: return@tabComplete emptyList()
                session.botPlayers.map { it.username }.filter { it.startsWith(prefix, ignoreCase = true) }
            }
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
            wordArgument("player1")
            wordArgument("player2")
            tabComplete { sender, input ->
                val prefix = input.substringAfterLast(" ")
                val session = GameTestRunner.getLiveSession(sender) ?: return@tabComplete emptyList()
                session.instance.players.map { it.username }.filter { it.startsWith(prefix, ignoreCase = true) }
            }
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
            wordArgument("phase")
            tabComplete { _, input ->
                val prefix = input.substringAfterLast(" ")
                GamePhase.entries.map { it.name }.filter { it.startsWith(prefix, ignoreCase = true) }
            }
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
                val targetPhase = runCatching { GamePhase.valueOf(phaseName.uppercase()) }.getOrNull()
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
}
