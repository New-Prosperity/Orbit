package me.nebula.orbit.commands

import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.replay.ReplayStorage
import me.nebula.orbit.utils.replay.ReplayViewer
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import me.nebula.gravity.translation.Keys
import me.nebula.ether.utils.translation.asTranslationKey

private val activeViewers = ConcurrentHashMap<UUID, ReplayViewer>()
private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")

fun replayCommand(): Command = command("replay") {
    permission("orbit.replay")

    subCommand("list") {
        permission("orbit.replay.admin")
        stringArrayArgument("args")
        onPlayerExecute {
            if (!ReplayStorage.isInitialized()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.StorageUnavailable))
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
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.List.Empty))
                return@onPlayerExecute
            }

            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.List.Header,
                "page" to clamped.toString(), "total" to totalPages.toString()))
            for (i in start until end) {
                val name = replays[i]
                val clickable = player.translate(Keys.Orbit.Command.Replay.List.Play)
                    .clickEvent(ClickEvent.runCommand("/replay play $name"))
                    .hoverEvent(HoverEvent.showText(player.translate(Keys.Orbit.Command.Replay.List.PlayHover, "name" to name)))
                val infoClick = player.translate(Keys.Orbit.Command.Replay.List.Info)
                    .clickEvent(ClickEvent.runCommand("/replay info $name"))
                    .hoverEvent(HoverEvent.showText(player.translate(Keys.Orbit.Command.Replay.List.InfoHover)))
                player.sendMessage(
                    player.translate(Keys.Orbit.Command.Replay.List.Entry, "name" to name)
                        .append(clickable)
                        .append(infoClick)
                )
            }
            if (clamped < totalPages) {
                val next = player.translate(Keys.Orbit.Command.Replay.List.NextPage)
                    .clickEvent(ClickEvent.runCommand("/replay list ${clamped + 1}"))
                player.sendMessage(next)
            }
        }
    }

    subCommand("play") {
        stringArrayArgument("args")
        onPlayerExecute {
            if (!ReplayStorage.isInitialized()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.StorageUnavailable))
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val name = cmdArgs?.firstOrNull()
            if (name == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Play.Usage))
                return@onPlayerExecute
            }
            if (activeViewers.containsKey(player.uuid)) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Play.AlreadyWatching))
                return@onPlayerExecute
            }

            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Play.Loading, "name" to name))

            val replayFile = ReplayStorage.loadBinary(name)
            if (replayFile == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.NotFound, "name" to name))
                return@onPlayerExecute
            }

            val viewer = ReplayViewer(replayFile)
            viewer.load().thenAccept { _ ->
                activeViewers[player.uuid] = viewer
                viewer.addViewer(player)
                viewer.play()
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Play.Started))
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Play.Controls))
            }
        }
    }

    subCommand("stop") {
        onPlayerExecute {
            val viewer = activeViewers.remove(player.uuid)
            if (viewer == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.NotWatching))
                return@onPlayerExecute
            }
            viewer.removeViewer(player)
            viewer.destroy()
            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Stopped))
        }
    }

    subCommand("pause") {
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.NotWatching))
                return@onPlayerExecute
            }
            viewer.pause()
            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Paused))
        }
    }

    subCommand("resume") {
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.NotWatching))
                return@onPlayerExecute
            }
            viewer.resume()
            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Resumed))
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
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.NotWatching))
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val speed = cmdArgs?.firstOrNull()?.toDoubleOrNull()
            if (speed == null || speed !in listOf(0.5, 1.0, 2.0, 4.0)) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Speed.Usage))
                return@onPlayerExecute
            }
            viewer.setSpeed(speed)
            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Speed.Set, "speed" to speed.toString()))
        }
    }

    subCommand("seek") {
        stringArrayArgument("args")
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.NotWatching))
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val input = cmdArgs?.firstOrNull()
            if (input == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Seek.Usage))
                return@onPlayerExecute
            }
            val tick = if (input.endsWith("%")) {
                val pct = input.removeSuffix("%").toDoubleOrNull()
                if (pct == null) {
                    player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Seek.InvalidPercent))
                    return@onPlayerExecute
                }
                (viewer.totalTicks * (pct / 100.0)).toInt()
            } else {
                input.toIntOrNull() ?: run {
                    player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Seek.InvalidTick))
                    return@onPlayerExecute
                }
            }
            viewer.seekTo(tick)
            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Seek.Success,
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
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.NotWatching))
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val targetName = cmdArgs?.firstOrNull()
            if (targetName == null) {
                viewer.setPerspective(null)
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Pov.Free))
                return@onPlayerExecute
            }
            val entry = viewer.playerEntries().firstOrNull {
                it.name.equals(targetName, ignoreCase = true)
            }
            if (entry == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Pov.NotFound, "name" to targetName))
                return@onPlayerExecute
            }
            viewer.setPerspective(entry.uuid)
            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Pov.Set, "name" to entry.name))
        }
    }

    subCommand("highlights") {
        onPlayerExecute {
            val viewer = activeViewers[player.uuid]
            if (viewer == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.NotWatching))
                return@onPlayerExecute
            }
            val highlights = viewer.highlights()
            if (highlights.isEmpty()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Highlights.Empty))
                return@onPlayerExecute
            }
            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Highlights.Header))
            for (highlight in highlights) {
                val time = ticksToTime(highlight.tick)
                val typeKey = "orbit.command.replay.highlights.type.${highlight.type.name.lowercase()}"
                val msg = player.translate(Keys.Orbit.Command.Replay.Highlights.Entry,
                    "time" to time,
                    "type" to player.translateRaw(typeKey.asTranslationKey()),
                    "description" to highlight.description,
                ).append(player.translate(Keys.Orbit.Command.Replay.Highlights.Seek)
                    .clickEvent(ClickEvent.runCommand("/replay seek ${highlight.tick}"))
                    .hoverEvent(HoverEvent.showText(player.translate(Keys.Orbit.Command.Replay.Highlights.SeekHover, "tick" to highlight.tick.toString()))))
                player.sendMessage(msg)
            }
        }
    }

    subCommand("info") {
        permission("orbit.replay.admin")
        stringArrayArgument("args")
        onPlayerExecute {
            if (!ReplayStorage.isInitialized()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.StorageUnavailable))
                return@onPlayerExecute
            }
            val cmdArgs = args.get("args") as? Array<String>
            val name = cmdArgs?.firstOrNull()
            if (name == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Info.Usage))
                return@onPlayerExecute
            }
            val replayFile = ReplayStorage.loadBinary(name)
            if (replayFile == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.NotFound, "name" to name))
                return@onPlayerExecute
            }
            val header = replayFile.header
            val date = dateFormat.format(Date(header.recordedAt))
            val duration = ticksToTime(header.durationTicks)
            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Info.Header, "name" to name))
            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Info.Gamemode, "value" to header.gamemode))
            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Info.Map, "value" to header.mapName))
            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Info.Duration, "value" to duration))
            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Info.Players, "value" to header.players.size.toString()))
            player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Info.Recorded, "value" to date))
            if (header.players.isNotEmpty()) {
                val names = header.players.joinToString(", ") { it.name }
                player.sendMessage(player.translate(Keys.Orbit.Command.Replay.Info.PlayerList, "names" to names))
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
