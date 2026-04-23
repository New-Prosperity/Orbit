package me.nebula.orbit.utils.statue

import com.hazelcast.query.Predicates
import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.gravity.player.PlayerData
import me.nebula.gravity.player.PlayerStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.statueAnimationArgument
import me.nebula.orbit.utils.commandbuilder.statueArgument
import net.minestom.server.command.builder.Command
import net.minestom.server.coordinate.Pos
import java.util.UUID

fun statueCommand(): Command = command("statue") {
    permission("orbit.statue")

    subCommand("add") {
        permission("orbit.statue")
        wordArgument("id")
        wordArgument("player")
        stringArgument("labelKey")
        intArgument("tier")

        onPlayerExecute {
            val id = arg("id")
            val playerName = arg("player")
            val labelKey = argOrNull("labelKey")
            val tier = intArgOrNull("tier")

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
                labelKey = labelKey?.let { TranslationKey(it) },
                tier = tier,
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
        statueArgument("id")

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
        statueArgument("id")
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
        statueArgument("id")
        statueAnimationArgument("animation", "id")

        onPlayerExecute {
            val id = arg("id")
            val animation = arg("animation")
            val statue = StatueManager.all().firstOrNull { it.id == id }
            if (statue == null) {
                reply("orbit.statue.not_found", "id" to id)
                return@onPlayerExecute
            }
            val availableAnimations = statue.modelOwner?.modeledEntity?.models?.values
                ?.firstOrNull()?.blueprint?.animations?.keys.orEmpty()
            if (animation !in availableAnimations) {
                replyMM("<red>Unknown animation '<white>$animation</white>'. Available: <gray>${availableAnimations.sorted().joinToString(", ")}")
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
        enumArgument("source", "rating:battleroyale", "kills:battleroyale", "wins:battleroyale")
        enumArgument("period", "ALL_TIME", "SEASON", "MONTH", "WEEK", "DAY")

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
