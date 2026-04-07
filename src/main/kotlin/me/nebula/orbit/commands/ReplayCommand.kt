package me.nebula.orbit.commands

import me.nebula.orbit.utils.chat.sendMM
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
                player.sendMM("<red>Replay storage is not available.")
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
                player.sendMM("<gray>No replays found.")
                return@onPlayerExecute
            }

            player.sendMM("<gold>--- Replays (Page $clamped/$totalPages) ---")
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
                player.sendMM("<red>Replay storage is not available.")
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val name = cmdArgs?.firstOrNull()
            if (name == null) {
                player.sendMM("<red>Usage: /replay play <name>")
                return@onPlayerExecute
            }
            if (activeViewers.containsKey(player.uuid)) {
                player.sendMM("<red>You are already watching a replay. Use /replay stop first.")
                return@onPlayerExecute
            }

            player.sendMM("<gray>Loading replay <white>$name<gray>...")

            val replayFile = ReplayStorage.loadBinary(name)
            if (replayFile == null) {
                player.sendMM("<red>Replay not found: <white>$name")
                return@onPlayerExecute
            }

            val viewer = ReplayViewer(replayFile)
            viewer.load().thenAccept { _ ->
                activeViewers[player.uuid] = viewer
                viewer.addViewer(player)
                viewer.play()
                player.sendMM("<green>Replay started. Use <white>/replay stop<green> to exit.")
                player.sendMM("<gray>Controls: <white>/replay pause<gray>, <white>/replay resume<gray>, <white>/replay speed <0.5|1|2|4>")
            }
        }
    }

    subCommand("stop") {
        onPlayerExecute {
            val viewer = activeViewers.remove(player.uuid)
            if (viewer == null) {
                player.sendMM("<red>You are not watching a replay.")
                return@onPlayerExecute
            }
            viewer.removeViewer(player)
            viewer.destroy()
            player.sendMM("<green>Replay stopped.")
        }
    }

    subCommand("pause") {
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMM("<red>You are not watching a replay.")
                return@onPlayerExecute
            }
            viewer.pause()
            player.sendMM("<yellow>Replay paused.")
        }
    }

    subCommand("resume") {
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMM("<red>You are not watching a replay.")
                return@onPlayerExecute
            }
            viewer.resume()
            player.sendMM("<green>Replay resumed.")
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
                player.sendMM("<red>You are not watching a replay.")
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val speed = cmdArgs?.firstOrNull()?.toDoubleOrNull()
            if (speed == null || speed !in listOf(0.5, 1.0, 2.0, 4.0)) {
                player.sendMM("<red>Usage: /replay speed <0.5|1|2|4>")
                return@onPlayerExecute
            }
            viewer.setSpeed(speed)
            player.sendMM("<green>Playback speed set to <white>${speed}x")
        }
    }

    subCommand("seek") {
        stringArrayArgument("args")
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMM("<red>You are not watching a replay.")
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val input = cmdArgs?.firstOrNull()
            if (input == null) {
                player.sendMM("<red>Usage: /replay seek <tick|percent%>")
                return@onPlayerExecute
            }
            val tick = if (input.endsWith("%")) {
                val pct = input.removeSuffix("%").toDoubleOrNull()
                if (pct == null) {
                    player.sendMM("<red>Invalid percentage.")
                    return@onPlayerExecute
                }
                (viewer.totalTicks * (pct / 100.0)).toInt()
            } else {
                input.toIntOrNull() ?: run {
                    player.sendMM("<red>Invalid tick number.")
                    return@onPlayerExecute
                }
            }
            viewer.seekTo(tick)
            player.sendMM("<green>Seeked to tick <white>$tick <gray>(${viewer.totalTicks} total)")
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
                player.sendMM("<red>You are not watching a replay.")
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val targetName = cmdArgs?.firstOrNull()
            if (targetName == null) {
                viewer.setPerspective(null)
                player.sendMM("<green>Switched to free camera.")
                return@onPlayerExecute
            }
            val entry = viewer.playerEntries().firstOrNull {
                it.name.equals(targetName, ignoreCase = true)
            }
            if (entry == null) {
                player.sendMM("<red>Player not found in replay: <white>$targetName")
                return@onPlayerExecute
            }
            viewer.setPerspective(entry.uuid)
            player.sendMM("<green>Switched to POV of <white>${entry.name}")
        }
    }

    subCommand("highlights") {
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMM("<red>You are not watching a replay.")
                return@onPlayerExecute
            }
            val highlights = viewer.highlights()
            if (highlights.isEmpty()) {
                player.sendMM("<gray>No highlights detected in this replay.")
                return@onPlayerExecute
            }
            player.sendMM("<gold>--- Replay Highlights ---")
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
                player.sendMM("<red>Replay storage is not available.")
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val name = cmdArgs?.firstOrNull()
            if (name == null) {
                player.sendMM("<red>Usage: /replay info <name>")
                return@onPlayerExecute
            }
            val replayFile = ReplayStorage.loadBinary(name)
            if (replayFile == null) {
                player.sendMM("<red>Replay not found: <white>$name")
                return@onPlayerExecute
            }
            val header = replayFile.header
            val date = dateFormat.format(Date(header.recordedAt))
            val duration = ticksToTime(header.durationTicks)
            player.sendMM("<gold>--- Replay Info: <white>$name <gold>---")
            player.sendMM("<gray>  Game Mode: <white>${header.gamemode}")
            player.sendMM("<gray>  Map: <white>${header.mapName}")
            player.sendMM("<gray>  Duration: <white>$duration")
            player.sendMM("<gray>  Players: <white>${header.players.size}")
            player.sendMM("<gray>  Recorded: <white>$date")
            if (header.players.isNotEmpty()) {
                val names = header.players.joinToString(", ") { it.name }
                player.sendMM("<gray>  Player list: <white>$names")
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
