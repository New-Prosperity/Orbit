package me.nebula.orbit.commands

import me.nebula.orbit.utils.commandbuilder.CommandBuilderDsl
import me.nebula.orbit.utils.gametest.GameTestRegistry
import me.nebula.orbit.utils.gametest.GameTestRunner

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

        subCommand("live") { installLiveTestSubcommands() }

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
