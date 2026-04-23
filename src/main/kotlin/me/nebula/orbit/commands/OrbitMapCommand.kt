package me.nebula.orbit.commands

import me.nebula.orbit.utils.commandbuilder.CommandBuilderDsl
import me.nebula.orbit.utils.maploader.MapLoader
import java.nio.file.Files
import java.nio.file.Path

internal fun CommandBuilderDsl.installMapSubcommands() {
    subCommand("map") {
        subCommand("scan") {
            onPlayerExecute {
                val ingested = runCatching { MapLoader.scanAndIngest() }
                    .onFailure { e -> replyMM("<red>Scan failed: ${e.message}") }
                    .getOrNull() // noqa: runCatching{}.getOrNull() as null check
                if (ingested != null) {
                    replyMM("<green>Scan complete — ingested <white>$ingested</white> Anvil world(s).")
                }
            }
        }

        subCommand("convert") {
            wordArgument("name") {
                val dir = Path.of("maps")
                if (!Files.isDirectory(dir)) return@wordArgument emptyList()
                val names = mutableListOf<String>()
                Files.newDirectoryStream(dir).use { stream ->
                    for (entry in stream) {
                        if (Files.isDirectory(entry) &&
                            Files.isDirectory(entry.resolve("dimensions/minecraft/overworld/region"))) {
                            names += entry.fileName.toString()
                        }
                    }
                }
                names.filter { it.startsWith(partial, ignoreCase = true) }
            }
            onPlayerExecute {
                val name = argOrNull("name")
                if (name.isNullOrBlank()) {
                    replyMM("<red>Usage: /orbit map convert <name>")
                    return@onPlayerExecute
                }
                val result = runCatching { MapLoader.ingestAnvilIfPresent(name) }
                val produced = result.getOrNull()
                val error = result.exceptionOrNull()
                when {
                    error != null -> replyMM("<red>Convert failed: ${error.message}")
                    produced == null -> replyMM("<yellow>No Anvil world at <white>maps/$name<yellow> — nothing to convert.")
                    else -> replyMM("<green>Converted <white>$name</white> → <white>${produced.fileName}</white>.")
                }
            }
        }

        subCommand("list") {
            onPlayerExecute {
                val mapsDir = Path.of("maps")
                val worldsDir = Path.of("worlds")
                val anvilDirs = mutableListOf<String>()
                if (Files.isDirectory(mapsDir)) {
                    Files.newDirectoryStream(mapsDir).use { stream ->
                        for (entry in stream) {
                            if (Files.isDirectory(entry) &&
                                Files.isDirectory(entry.resolve("dimensions/minecraft/overworld/region"))) {
                                anvilDirs += entry.fileName.toString()
                            }
                        }
                    }
                }
                val nebulaFiles = mutableListOf<Pair<String, Long>>()
                if (Files.isDirectory(worldsDir)) {
                    Files.newDirectoryStream(worldsDir).use { stream ->
                        for (entry in stream) {
                            if (Files.isRegularFile(entry) && entry.toString().endsWith(".nebula")) {
                                nebulaFiles += entry.fileName.toString() to Files.size(entry)
                            }
                        }
                    }
                }
                replyMM("<gold><bold>Maps</bold></gold> <dark_gray>(${nebulaFiles.size} .nebula in worlds/, ${anvilDirs.size} anvil in maps/)")
                if (nebulaFiles.isEmpty() && anvilDirs.isEmpty()) {
                    replyMM("<gray>No maps found.")
                    return@onPlayerExecute
                }
                nebulaFiles.sortedBy { it.first }.forEach { (name, size) ->
                    replyMM("<green>✓</green> <white>$name</white> <dark_gray>(${size / 1024}KB)")
                }
                anvilDirs.sortedBy { it }.forEach { name ->
                    replyMM("<yellow>⧖</yellow> <white>$name</white>/ <dark_gray>(anvil — run /orbit map convert $name)")
                }
            }
        }

        onPlayerExecute {
            replyMM("<gold><bold>/orbit map</bold></gold>")
            replyMM("<white> /orbit map list <dark_gray>- List maps and their format")
            replyMM("<white> /orbit map scan <dark_gray>- Scan maps/ for Anvil worlds and convert each in place")
            replyMM("<white> /orbit map convert <name> <dark_gray>- Convert a single Anvil world to .nebula")
        }
    }
}
