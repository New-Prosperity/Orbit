package me.nebula.orbit.utils.statue

import com.hazelcast.query.Predicates
import me.nebula.gravity.player.PlayerData
import me.nebula.gravity.player.PlayerStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.commandbuilder.command
import net.minestom.server.command.builder.Command
import net.minestom.server.coordinate.Pos
import java.util.UUID

fun statueCommand(): Command = command("statue") {
    permission("orbit.statue")

    subCommand("add") {
        permission("orbit.statue")
        wordArgument("id")
        wordArgument("player")
        stringArgument("label")

        onPlayerExecute {
            val id = arg("id")
            val playerName = arg("player")
            val label = argOrNull("label")

            val targetUuid = resolvePlayerUuid(playerName)
            if (targetUuid == null) {
                reply("orbit.command.player_not_found", "name" to playerName)
                return@onPlayerExecute
            }

            val mode = Orbit.mode
            val result = StatueManager.spawn(id, StatueConfig(
                playerUuid = targetUuid,
                position = player.position,
                instance = mode.defaultInstance,
                rotationSpeed = 0f,
                showCosmetics = true,
                showHologram = true,
                label = label,
            ))

            if (result != null) {
                reply("orbit.statue.spawned", "id" to id, "player" to playerName)
            } else {
                reply("orbit.statue.already_exists", "id" to id)
            }
        }
    }

    subCommand("remove") {
        permission("orbit.statue")
        wordArgument("id")

        onPlayerExecute {
            val id = arg("id")
            if (StatueManager.remove(id)) {
                reply("orbit.statue.removed", "id" to id)
            } else {
                reply("orbit.statue.not_found", "id" to id)
            }
        }
    }

    subCommand("list") {
        permission("orbit.statue")

        onPlayerExecute {
            val statues = StatueManager.all()
            if (statues.isEmpty()) {
                reply("orbit.statue.list_empty")
                return@onPlayerExecute
            }
            replyMM("<yellow><bold>Statues (${statues.size}):")
            for (statue in statues) {
                val pos = statue.config.position
                replyMM("<gray>- <white>${statue.id} <gray>(${statue.playerName}) <dark_gray>[${pos.blockX()}, ${pos.blockY()}, ${pos.blockZ()}]")
            }
        }
    }

    subCommand("refresh") {
        permission("orbit.statue")

        onPlayerExecute {
            reply("orbit.statue.refreshing")
            Thread.startVirtualThread {
                StatueManager.all().toList().forEach { statue ->
                    StatueManager.remove(statue.id)
                    StatueManager.spawn(statue.id, statue.config)
                }
            }
        }
    }

    subCommand("move") {
        permission("orbit.statue")
        wordArgument("id")
        doubleArgument("x")
        doubleArgument("y")
        doubleArgument("z")

        onPlayerExecute {
            val id = arg("id")
            val x = doubleArg("x")
            val y = doubleArg("y")
            val z = doubleArg("z")

            if (StatueManager.moveStatue(id, Pos(x, y, z))) {
                reply("orbit.statue.moved", "id" to id)
            } else {
                reply("orbit.statue.not_found", "id" to id)
            }
        }
    }

    subCommand("pose") {
        permission("orbit.statue")
        wordArgument("id")
        wordArgument("animation")

        onPlayerExecute {
            val id = arg("id")
            val animation = arg("animation")
            val validAnimations = setOf("idle", "wave", "crossed_arms", "celebrate", "salute", "look_around", "sit")

            if (animation !in validAnimations) {
                replyMM("<red>Valid poses: ${validAnimations.joinToString(", ")}")
                return@onPlayerExecute
            }

            if (StatueManager.setAnimation(id, animation)) {
                reply("orbit.statue.pose_set", "id" to id, "pose" to animation)
            } else {
                reply("orbit.statue.not_found", "id" to id)
            }
        }
    }

    subCommand("leaderboard") {
        permission("orbit.statue")
        wordArgument("source")
        wordArgument("period")

        onPlayerExecute {
            val source = arg("source")
            val period = arg("period")
            StatueManager.setLeaderboardConfig(source, period)
            reply("orbit.statue.leaderboard_set", "source" to source, "period" to period)
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun resolvePlayerUuid(name: String): UUID? {
    val predicate = Predicates.equal<UUID, PlayerData>("name", name)
    val results = PlayerStore.entries(predicate)
    return results.firstOrNull()?.key
}
