package me.nebula.orbit.commands

import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.botai.BotLobbyFiller
import me.nebula.orbit.utils.botai.FillerConfig
import me.nebula.orbit.utils.commandbuilder.CommandBuilderDsl

internal fun CommandBuilderDsl.installAdminSubcommands() {
    subCommand("reload") {
        onPlayerExecute {
            replyMM("<yellow>No hot-reload configuration available.")
        }
    }

    subCommand("fill") {
        subCommand("start") {
            intArgument("targetCount")
            enumArgument("preset", "survival", "combat", "pvp", "miner", "gatherer", "passive")
            enumArgument("skillRange", "0.2-0.5", "0.3-0.7", "0.5-0.9", "0.0-1.0")
            onPlayerExecute {
                val gm = orbitGameMode()
                if (gm == null) {
                    replyMM("<red>Not in a game mode.")
                    return@onPlayerExecute
                }
                val target = intArgOrNull("targetCount")
                if (target == null || target < 2) {
                    replyMM("<red>Usage: /orbit fill start <targetCount> [preset] [skillMin-skillMax]")
                    return@onPlayerExecute
                }
                val preset = argOrNull("preset") ?: "survival"
                val skillRangeStr = argOrNull("skillRange")
                val skillRange = if (skillRangeStr != null) {
                    val parts = skillRangeStr.split("-")
                    if (parts.size == 2) {
                        val min = parts[0].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.3f
                        val max = parts[1].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.7f
                        min..max
                    } else 0.3f..0.7f
                } else 0.3f..0.7f

                val gameId = Orbit.serverName
                BotLobbyFiller.startFilling(gameId, gm, FillerConfig(
                    targetPlayerCount = target,
                    preset = preset,
                    skillRange = skillRange,
                ))
                replyMM("<green>Started filling to $target players with $preset AI (skill: $skillRange)")
            }
        }

        subCommand("stop") {
            onPlayerExecute {
                val gameId = Orbit.serverName
                BotLobbyFiller.stopFilling(gameId)
                replyMM("<yellow>Stopped filling and removed all filler bots.")
            }
        }

        subCommand("status") {
            onPlayerExecute {
                val gameId = Orbit.serverName
                val status = BotLobbyFiller.getStatus(gameId)
                if (status == null) {
                    replyMM("<gray>No active filler for this server.")
                } else {
                    replyMM("<gold>Filler Status:</gold> <white>$status")
                }
            }
        }

        onPlayerExecute {
            replyMM("<gray>Usage:")
            replyMM("<white> /orbit fill start <targetCount> [preset] [skillMin-skillMax]")
            replyMM("<white> /orbit fill stop")
            replyMM("<white> /orbit fill status")
        }
    }
}
