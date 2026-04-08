package me.nebula.orbit.commands

import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.replay.HighlightType
import me.nebula.orbit.utils.replay.ReplayStorage
import me.nebula.orbit.utils.replay.ReplayViewer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val activeViewers = ConcurrentHashMap<UUID, ReplayViewer>()
private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")

fun replayCommand(): Command = command("replay") {
    permission("orbit.replay")

    subCommand("list") {
        permission("orbit.replay.admin")
        stringArrayArgument("args")
        onPlayerExecute {
            if (!ReplayStorage.isInitialized()) {
                player.sendMessage(player.translate("orbit.command.replay.storage_unavailable"))
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val page = cmdArgs?.firstOrNull()?.toIntOrNull() ?: 1
            val replays = ReplayStorage.list().sorted()
            val pageSize = 10
            val totalPages = ((replays.size - 1) / pageSize) + 1
            val clamped = page.coerceIn(1, totalPages.coerceAtLeast(1))
            val start = (clamped - 1) * pageSize
            val end = (start + pageSize).coerceAtMost(replays.size)

            if (replays.isEmpty()) {
                player.sendMessage(player.translate("orbit.command.replay.list.empty"))
                return@onPlayerExecute
            }

            player.sendMessage(player.translate("orbit.command.replay.list.header",
                "page" to clamped.toString(), "total" to totalPages.toString()))
            for (i in start until end) {
                val name = replays[i]
                val clickable = Component.text(" [Play]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/replay play $name"))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to play $name")))
                val infoClick = Component.text(" [Info]", NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/replay info $name"))
                    .hoverEvent(HoverEvent.showText(Component.text("Click for info")))
                player.sendMessage(
                    Component.text("  $name", NamedTextColor.YELLOW)
                        .append(clickable)
                        .append(infoClick)
                )
            }
            if (clamped < totalPages) {
                val next = Component.text("[Next Page]", NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.runCommand("/replay list ${clamped + 1}"))
                player.sendMessage(next)
            }
        }
    }

    subCommand("play") {
        stringArrayArgument("args")
        onPlayerExecute {
            if (!ReplayStorage.isInitialized()) {
                player.sendMessage(player.translate("orbit.command.replay.storage_unavailable"))
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val name = cmdArgs?.firstOrNull()
            if (name == null) {
                player.sendMessage(player.translate("orbit.command.replay.play.usage"))
                return@onPlayerExecute
            }
            if (activeViewers.containsKey(player.uuid)) {
                player.sendMessage(player.translate("orbit.command.replay.play.already_watching"))
                return@onPlayerExecute
            }

            player.sendMessage(player.translate("orbit.command.replay.play.loading", "name" to name))

            val replayFile = ReplayStorage.loadBinary(name)
            if (replayFile == null) {
                player.sendMessage(player.translate("orbit.command.replay.not_found", "name" to name))
                return@onPlayerExecute
            }

            val viewer = ReplayViewer(replayFile)
            viewer.load().thenAccept { _ ->
                activeViewers[player.uuid] = viewer
                viewer.addViewer(player)
                viewer.play()
                player.sendMessage(player.translate("orbit.command.replay.play.started"))
                player.sendMessage(player.translate("orbit.command.replay.play.controls"))
            }
        }
    }

    subCommand("stop") {
        onPlayerExecute {
            val viewer = activeViewers.remove(player.uuid)
            if (viewer == null) {
                player.sendMessage(player.translate("orbit.command.replay.not_watching"))
                return@onPlayerExecute
            }
            viewer.removeViewer(player)
            viewer.destroy()
            player.sendMessage(player.translate("orbit.command.replay.stopped"))
        }
    }

    subCommand("pause") {
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMessage(player.translate("orbit.command.replay.not_watching"))
                return@onPlayerExecute
            }
            viewer.pause()
            player.sendMessage(player.translate("orbit.command.replay.paused"))
        }
    }

    subCommand("resume") {
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMessage(player.translate("orbit.command.replay.not_watching"))
                return@onPlayerExecute
            }
            viewer.resume()
            player.sendMessage(player.translate("orbit.command.replay.resumed"))
        }
    }

    subCommand("speed") {
        stringArrayArgument("args")
        tabComplete { _, input ->
            val tokens = input.trimEnd().split(" ")
            if (tokens.size == 3) listOf("0.5", "1", "2", "4").filter { it.startsWith(tokens.last()) }
            else emptyList()
        }
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMessage(player.translate("orbit.command.replay.not_watching"))
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val speed = cmdArgs?.firstOrNull()?.toDoubleOrNull()
            if (speed == null || speed !in listOf(0.5, 1.0, 2.0, 4.0)) {
                player.sendMessage(player.translate("orbit.command.replay.speed.usage"))
                return@onPlayerExecute
            }
            viewer.setSpeed(speed)
            player.sendMessage(player.translate("orbit.command.replay.speed.set", "speed" to speed.toString()))
        }
    }

    subCommand("seek") {
        stringArrayArgument("args")
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMessage(player.translate("orbit.command.replay.not_watching"))
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val input = cmdArgs?.firstOrNull()
            if (input == null) {
                player.sendMessage(player.translate("orbit.command.replay.seek.usage"))
                return@onPlayerExecute
            }
            val tick = if (input.endsWith("%")) {
                val pct = input.removeSuffix("%").toDoubleOrNull()
                if (pct == null) {
                    player.sendMessage(player.translate("orbit.command.replay.seek.invalid_percent"))
                    return@onPlayerExecute
                }
                (viewer.totalTicks * (pct / 100.0)).toInt()
            } else {
                input.toIntOrNull() ?: run {
                    player.sendMessage(player.translate("orbit.command.replay.seek.invalid_tick"))
                    return@onPlayerExecute
                }
            }
            viewer.seekTo(tick)
            player.sendMessage(player.translate("orbit.command.replay.seek.success",
                "tick" to tick.toString(), "total" to viewer.totalTicks.toString()))
        }
    }

    subCommand("pov") {
        stringArrayArgument("args")
        tabComplete { _, input ->
            val tokens = input.trimEnd().split(" ")
            val viewer = activeViewers[(tokens.firstOrNull() as? Player)?.uuid] ?: return@tabComplete emptyList()
            emptyList()
        }
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMessage(player.translate("orbit.command.replay.not_watching"))
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val targetName = cmdArgs?.firstOrNull()
            if (targetName == null) {
                viewer.setPerspective(null)
                player.sendMessage(player.translate("orbit.command.replay.pov.free"))
                return@onPlayerExecute
            }
            val entry = viewer.playerEntries().firstOrNull {
                it.name.equals(targetName, ignoreCase = true)
            }
            if (entry == null) {
                player.sendMessage(player.translate("orbit.command.replay.pov.not_found", "name" to targetName))
                return@onPlayerExecute
            }
            viewer.setPerspective(entry.uuid)
            player.sendMessage(player.translate("orbit.command.replay.pov.set", "name" to entry.name))
        }
    }

    subCommand("highlights") {
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMessage(player.translate("orbit.command.replay.not_watching"))
                return@onPlayerExecute
            }
            val highlights = viewer.highlights()
            if (highlights.isEmpty()) {
                player.sendMessage(player.translate("orbit.command.replay.highlights.empty"))
                return@onPlayerExecute
            }
            player.sendMessage(player.translate("orbit.command.replay.highlights.header"))
            for (highlight in highlights) {
                val time = ticksToTime(highlight.tick)
                val color = when (highlight.type) {
                    HighlightType.MULTI_KILL -> NamedTextColor.RED
                    HighlightType.FINAL_KILL -> NamedTextColor.GOLD
                    HighlightType.CLUTCH -> NamedTextColor.LIGHT_PURPLE
                    HighlightType.FIRST_BLOOD -> NamedTextColor.GREEN
                    HighlightType.LONG_RANGE_KILL -> NamedTextColor.AQUA
                }
                val msg = Component.text("  [$time] ", NamedTextColor.GRAY)
                    .append(Component.text("[${highlight.type.name}] ", color))
                    .append(Component.text(highlight.description, NamedTextColor.WHITE))
                    .append(Component.text(" [Seek]", NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("/replay seek ${highlight.tick}"))
                        .hoverEvent(HoverEvent.showText(Component.text("Jump to tick ${highlight.tick}"))))
                player.sendMessage(msg)
            }
        }
    }

    subCommand("info") {
        permission("orbit.replay.admin")
        stringArrayArgument("args")
        onPlayerExecute {
            if (!ReplayStorage.isInitialized()) {
                player.sendMessage(player.translate("orbit.command.replay.storage_unavailable"))
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val name = cmdArgs?.firstOrNull()
            if (name == null) {
                player.sendMessage(player.translate("orbit.command.replay.info.usage"))
                return@onPlayerExecute
            }
            val replayFile = ReplayStorage.loadBinary(name)
            if (replayFile == null) {
                player.sendMessage(player.translate("orbit.command.replay.not_found", "name" to name))
                return@onPlayerExecute
            }
            val header = replayFile.header
            val date = dateFormat.format(Date(header.recordedAt))
            val duration = ticksToTime(header.durationTicks)
            player.sendMessage(player.translate("orbit.command.replay.info.header", "name" to name))
            player.sendMessage(player.translate("orbit.command.replay.info.gamemode", "value" to header.gamemode))
            player.sendMessage(player.translate("orbit.command.replay.info.map", "value" to header.mapName))
            player.sendMessage(player.translate("orbit.command.replay.info.duration", "value" to duration))
            player.sendMessage(player.translate("orbit.command.replay.info.players", "value" to header.players.size.toString()))
            player.sendMessage(player.translate("orbit.command.replay.info.recorded", "value" to date))
            if (header.players.isNotEmpty()) {
                val names = header.players.joinToString(", ") { it.name }
                player.sendMessage(player.translate("orbit.command.replay.info.player_list", "names" to names))
            }
        }
    }
}

private fun ticksToTime(ticks: Int): String {
    val totalSeconds = ticks / 20
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun cleanupReplayViewer(playerUuid: UUID) {
    val viewer = activeViewers.remove(playerUuid)
    viewer?.destroy()
}
