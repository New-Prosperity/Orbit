package me.nebula.orbit.mode.build

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.translation.translate
import me.nebula.orbit.mode.ServerMode
import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.utils.maploader.MapLoader
import me.nebula.orbit.utils.nebulaworld.NebulaWorldLoader
import me.nebula.orbit.utils.nebulaworld.NebulaWorldWriter
import me.nebula.orbit.utils.replay.ReplayWorldCapture
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.customcontent.furniture.FurniturePersistence
import me.nebula.orbit.utils.scheduler.repeat
import me.nebula.orbit.utils.worldedit.EditSessionManager
import me.nebula.orbit.utils.worldedit.SelectionRenderer
import me.nebula.orbit.utils.vanilla.VanillaModules
import me.nebula.orbit.utils.vanilla.installGameRuleCommand
import me.nebula.orbit.utils.worldedit.installEditCommands
import me.nebula.orbit.utils.worldedit.installWandListeners
import me.nebula.orbit.utils.customcontent.furniture.FurniturePlacedEvent
import me.nebula.orbit.utils.customcontent.furniture.FurnitureBrokenEvent
import me.nebula.orbit.utils.chat.miniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.timer.Task
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import me.nebula.gravity.translation.Keys

class BuildMode(private val worldPathOverride: String? = null) : ServerMode {

    private val logger = logger("BuildMode")

    override val maxPlayers: Int = 20
    override val cosmeticConfig: CosmeticConfig = CosmeticConfig()
    override val spawnPoint: Pos = Pos(0.5, 5.0, 0.5)

    override val defaultInstance: InstanceContainer = createInstance()

    private var selectionTask: Task? = null
    private var autoSaveTask: Task? = null
    private val saveInProgress = AtomicBoolean(false)
    private val dirty = AtomicBoolean(false)

    private fun createInstance(): InstanceContainer {
        val override = worldPathOverride
        if (override != null) {
            val path = Path.of(override)
            if (MapLoader.isNebulaFile(path)) {
                logger.info { "Loading build world from override: $path" }
                return NebulaWorldLoader.load("build", path)
            }
            val resolved = runCatching { MapLoader.resolve(override) }.getOrNull() // noqa: runCatching{}.getOrNull() as null check
            if (resolved != null) {
                logger.info { "Loading build world via MapLoader: $resolved" }
                return NebulaWorldLoader.load("build", resolved)
            }
            logger.warn { "Override '$override' did not resolve to a .nebula — falling back to saved session or superflat" }
        }

        val defaultPath = Path.of(WORLDS_DIR, "$DEFAULT_SESSION_NAME.nebula")
        if (Files.isRegularFile(defaultPath)) {
            logger.info { "Loading previous build session: $defaultPath" }
            return NebulaWorldLoader.load("build", defaultPath)
        }

        return createSuperflat()
    }

    private fun createSuperflat(): InstanceContainer {
        logger.info { "No world found, generating superflat" }
        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setChunkSupplier { inst, cx, cz -> LightingChunk(inst, cx, cz) }
        instance.setGenerator { unit ->
            val minY = unit.absoluteStart().blockY()
            val maxY = unit.absoluteEnd().blockY()
            if (minY < 1) unit.modifier().fillHeight(minY, minOf(1, maxY), Block.BEDROCK)
            if (minY < 4 && maxY > 1) unit.modifier().fillHeight(maxOf(1, minY), minOf(4, maxY), Block.DIRT)
            if (minY < 5 && maxY > 4) unit.modifier().fillHeight(maxOf(4, minY), minOf(5, maxY), Block.GRASS_BLOCK)
        }
        instance.time = 6000
        return instance
    }

    override fun install(handler: GlobalEventHandler) {
        logger.info { "Installing build mode" }

        val commandManager = MinecraftServer.getCommandManager()
        VanillaModules.enable(defaultInstance, "block-pick")
        VanillaModules.enable(defaultInstance, "gravity-blocks")

        installEditCommands(commandManager)
        installGameRuleCommand(commandManager)
        installWandListeners(handler)

        commandManager.register(command("save") {
            permission("nebula.worldedit")
            wordArgument("name")
            onPlayerExecute {
                val name = argOrNull("name") ?: DEFAULT_SESSION_NAME
                Thread.startVirtualThread {
                    if (!saveInProgress.compareAndSet(false, true)) {
                        player.sendMessage(miniMessage.deserialize("<yellow>A save is already in progress — please retry shortly."))
                        return@startVirtualThread
                    }
                    try {
                        player.sendMessage(player.translate(Keys.Orbit.Build.SaveStart, "name" to name))
                        broadcastSaveStart(name)
                        val result = runCatching { saveTo(name) }
                        result.onSuccess { out ->
                            player.sendMessage(player.translate(Keys.Orbit.Build.SaveComplete,
                                "chunks" to out.chunks.toString(),
                                "path" to out.path.toString(),
                                "size" to (out.sizeBytes / 1024).toString()))
                            broadcastSaveComplete(name, out)
                            dirty.set(false)
                        }.onFailure { e ->
                            player.sendMessage(miniMessage.deserialize("<red>Save failed: ${e.message}"))
                            logger.warn { "Manual save failed for '$name': ${e.message}" }
                        }
                    } finally {
                        saveInProgress.set(false)
                    }
                }
            }
        })

        commandManager.register(command("saves") {
            permission("nebula.worldedit")
            onPlayerExecute {
                val worldsDir = Path.of(WORLDS_DIR)
                if (!Files.isDirectory(worldsDir)) {
                    replyMM("<yellow>No <white>$WORLDS_DIR/<yellow> directory yet.")
                    return@onPlayerExecute
                }
                val files = Files.list(worldsDir).use { stream ->
                    stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".nebula") }
                        .map { path ->
                            Triple(
                                path.fileName.toString().removeSuffix(".nebula"),
                                Files.size(path),
                                Files.getLastModifiedTime(path).toInstant(),
                            )
                        }
                        .sorted(compareByDescending { (_, _, t) -> t })
                        .toList()
                }
                if (files.isEmpty()) {
                    replyMM("<yellow>No saves found.")
                    return@onPlayerExecute
                }
                replyMM("<gold><bold>Saves</bold></gold> <dark_gray>(${files.size})")
                val now = Instant.now()
                for ((name, size, modified) in files) {
                    val agoSec = (now.epochSecond - modified.epochSecond).coerceAtLeast(0)
                    val ago = formatAge(agoSec)
                    val current = if (name == DEFAULT_SESSION_NAME) " <green>● active" else ""
                    replyMM("<gray>- <white>$name <dark_gray>(${size / 1024}KB, $ago ago)</dark_gray>$current")
                }
            }
        })

        commandManager.register(command("load") {
            permission("nebula.worldedit")
            wordArgument("name") {
                val worldsDir = Path.of(WORLDS_DIR)
                if (!Files.isDirectory(worldsDir)) return@wordArgument emptyList()
                val names = mutableListOf<String>()
                Files.newDirectoryStream(worldsDir).use { stream ->
                    for (entry in stream) {
                        if (Files.isRegularFile(entry) && entry.toString().endsWith(".nebula")) {
                            names += entry.fileName.toString().removeSuffix(".nebula")
                        }
                    }
                }
                names.filter { it.startsWith(partial, ignoreCase = true) }
            }
            onPlayerExecute {
                val name = argOrNull("name") ?: run {
                    replyMM("<red>Usage: /load <name>")
                    return@onPlayerExecute
                }
                val path = Path.of(WORLDS_DIR, "$name.nebula")
                if (!Files.isRegularFile(path)) {
                    replyMM("<red>No .nebula file found at <white>$path")
                    return@onPlayerExecute
                }
                Thread.startVirtualThread {
                    if (!saveInProgress.compareAndSet(false, true)) {
                        replyMM("<yellow>A save is already in progress — please retry shortly.")
                        return@startVirtualThread
                    }
                    try {
                        replyMM("<yellow>Saving current session before loading <white>$name<yellow>...")
                        runCatching { saveTo(DEFAULT_SESSION_NAME) }
                            .onFailure { e -> replyMM("<red>Save failed: ${e.message} — aborting load to avoid data loss."); return@startVirtualThread }
                        Files.copy(path, Path.of(WORLDS_DIR, "$DEFAULT_SESSION_NAME.nebula"),
                            StandardCopyOption.REPLACE_EXISTING)
                        replyMM("<green>Queued <white>$name</white> for next session. Restart the server (/stop) to enter it.")
                    } finally {
                        saveInProgress.set(false)
                    }
                }
            }
        })

        handler.addListener(PlayerSpawnEvent::class.java) { event ->
            if (!event.isFirstSpawn) return@addListener
            event.player.gameMode = GameMode.CREATIVE
            event.player.isAllowFlying = true
            event.player.isFlying = true
            val wand = ItemStack.of(Material.WOODEN_AXE)
            event.player.inventory.addItemStack(wand)
        }

        handler.addListener(PlayerBlockPlaceEvent::class.java) { markDirty() }
        handler.addListener(PlayerBlockBreakEvent::class.java) { markDirty() }
        handler.addListener(FurniturePlacedEvent::class.java) { markDirty() }
        handler.addListener(FurnitureBrokenEvent::class.java) { markDirty() }

        selectionTask = defaultInstance.scheduler().buildTask { SelectionRenderer.tick() }
            .repeat(Duration.ofMillis(500))
            .schedule()

        autoSaveTask = repeat(AUTO_SAVE_INTERVAL) {
            runAutoSaveAsync()
        }

        logger.info { "Build mode installed (auto-save every ${AUTO_SAVE_INTERVAL.toMinutes()}m)" }
    }

    private fun runAutoSaveAsync() {
        if (!dirty.get()) return
        if (!saveInProgress.compareAndSet(false, true)) {
            logger.info { "Auto-save skipped (previous save still running)" }
            return
        }
        Thread.startVirtualThread {
            try {
                val result = saveTo(DEFAULT_SESSION_NAME)
                dirty.set(false)
                logger.info { "Auto-saved build → ${result.path} (${result.chunks} chunks, ${result.sizeBytes / 1024}KB, ${result.durationMs}ms)" }
            } catch (e: Throwable) {
                logger.warn { "Auto-save failed: ${e.message}" }
            } finally {
                saveInProgress.set(false)
            }
        }
    }

    private fun markDirty() { dirty.set(true) }

    private fun broadcastSaveStart(name: String) {
        val msg = miniMessage.deserialize("<gray>[<white>Save</white>] saving <white>$name</white>…")
        defaultInstance.players.forEach { it.sendMessage(msg) }
    }

    private fun broadcastSaveComplete(name: String, result: SaveResult) {
        val msg = miniMessage.deserialize("<gray>[<white>Save</white>] <green>done</green> <white>$name</white> <dark_gray>(${result.chunks}c, ${result.sizeBytes / 1024}KB, ${result.durationMs}ms)")
        defaultInstance.players.forEach { it.sendMessage(msg) }
    }

    private fun formatAge(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        else -> "${seconds / 86400}d"
    }

    override fun shutdown() {
        selectionTask?.cancel()
        autoSaveTask?.cancel()
        blockingSaveOnShutdown()
        VanillaModules.disableAll(defaultInstance)
        EditSessionManager.clear()
    }

    private fun blockingSaveOnShutdown() {
        var waited = 0
        while (saveInProgress.get() && waited < SHUTDOWN_SAVE_WAIT_MS) {
            Thread.sleep(50)
            waited += 50
        }
        if (saveInProgress.get()) {
            logger.warn { "In-flight auto-save still running after ${SHUTDOWN_SAVE_WAIT_MS}ms — proceeding with a fresh blocking save anyway" }
        }
        logger.info { "Saving build session before shutdown (blocking)..." }
        saveInProgress.set(true)
        try {
            val result = saveTo(DEFAULT_SESSION_NAME)
            dirty.set(false)
            logger.info { "Shutdown save complete: ${result.path} (${result.chunks} chunks, ${result.sizeBytes / 1024}KB, ${result.durationMs}ms)" }
        } catch (e: Throwable) {
            logger.warn { "Shutdown save failed: ${e.message} — data may be lost" }
        } finally {
            saveInProgress.set(false)
        }
    }

    private fun saveTo(name: String): SaveResult {
        val start = System.currentTimeMillis()
        val outputPath = Path.of(WORLDS_DIR, "$name.nebula")
        Files.createDirectories(outputPath.parent)
        val captured = ReplayWorldCapture.capture(defaultInstance)
        val world = FurniturePersistence.embed(captured, defaultInstance)

        val tmpPath = outputPath.resolveSibling("${outputPath.fileName}.tmp")
        NebulaWorldWriter.write(world, tmpPath)

        if (Files.exists(outputPath)) {
            rotateBackups(outputPath)
        }

        Files.move(tmpPath, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)

        val durationMs = System.currentTimeMillis() - start
        return SaveResult(outputPath, world.chunks.size, Files.size(outputPath), durationMs)
    }

    private fun rotateBackups(outputPath: Path) {
        val base = outputPath.fileName.toString()
        for (i in BACKUP_COUNT downTo 1) {
            val olderBackup = outputPath.resolveSibling("$base.bak$i")
            if (i == BACKUP_COUNT && Files.exists(olderBackup)) {
                runCatching { Files.delete(olderBackup) }
                    .onFailure { logger.warn { "Backup delete failed for $olderBackup: ${it.message}" } }
                continue
            }
            if (i < BACKUP_COUNT) {
                val newerBackup = outputPath.resolveSibling("$base.bak$i")
                val nextTarget = outputPath.resolveSibling("$base.bak${i + 1}")
                if (Files.exists(newerBackup)) {
                    runCatching { Files.move(newerBackup, nextTarget, StandardCopyOption.REPLACE_EXISTING) }
                        .onFailure { logger.warn { "Backup rotate failed $newerBackup → $nextTarget: ${it.message}" } }
                }
            }
        }
        val firstBackup = outputPath.resolveSibling("$base.bak1")
        runCatching { Files.copy(outputPath, firstBackup, StandardCopyOption.REPLACE_EXISTING) }
            .onFailure { logger.warn { "Backup copy failed for $firstBackup: ${it.message}" } }
    }

    private data class SaveResult(
        val path: Path,
        val chunks: Int,
        val sizeBytes: Long,
        val durationMs: Long,
    )

    companion object {
        const val DEFAULT_SESSION_NAME: String = "build"
        const val WORLDS_DIR: String = "worlds"
        private val AUTO_SAVE_INTERVAL: Duration = Duration.ofMinutes(5)
        private const val SHUTDOWN_SAVE_WAIT_MS: Int = 10_000
        private const val BACKUP_COUNT: Int = 3
    }
}
