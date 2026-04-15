package me.nebula.orbit.commands

import me.nebula.ether.utils.parse.enumValueOfOrNull
import me.nebula.orbit.utils.botai.BotAI
import me.nebula.orbit.utils.commandbuilder.CommandBuilderDsl
import me.nebula.orbit.utils.gametest.GameTestRegistry
import me.nebula.orbit.utils.gametest.GameTestRunner
import me.nebula.orbit.utils.gametest.TestBehavior
import me.nebula.orbit.utils.gametest.TestBotPresets
import kotlin.time.Duration.Companion.seconds

internal fun CommandBuilderDsl.installLiveBotSubcommands() {
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
            val behavior = enumValueOfOrNull<TestBehavior>(behaviorName.uppercase())
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
            val behavior = enumValueOfOrNull<TestBehavior>(behaviorName.uppercase())
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
}
