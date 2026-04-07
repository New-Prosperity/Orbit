package me.nebula.orbit.utils.gametest

import me.nebula.orbit.utils.commandbuilder.command
import net.minestom.server.command.builder.Command

fun gameTestCommand(): Command = command("gametest") {
    permission("nebula.gametest")

    subCommand("run") {
        wordArgument("testId")
        tabComplete { _, input ->
            val prefix = input.substringAfterLast(" ")
            GameTestRegistry.ids().filter { it.startsWith(prefix, ignoreCase = true) }
        }
        onPlayerExecute {
            val testId = argOrNull("testId")
            if (testId == null) {
                replyMM("<red>Usage: /gametest run <testId>")
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
                replyMM("<white> $id$desc <dark_gray>[${definition.playerCount}p, ${definition.timeout}]")
            }
        }
    }

    onPlayerExecute {
        replyMM("<gray>Usage:")
        replyMM("<white> /gametest run <testId> <dark_gray>- Run a specific test")
        replyMM("<white> /gametest runall <dark_gray>- Run all registered tests")
        replyMM("<white> /gametest cancel <dark_gray>- Cancel running test")
        replyMM("<white> /gametest list <dark_gray>- List available tests")
    }
}
