package me.nebula.orbit.mode.build

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.translation.translate
import me.nebula.orbit.mode.ServerMode
import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.utils.anvilloader.AnvilWorldLoader
import me.nebula.orbit.utils.maploader.MapLoader
import me.nebula.orbit.utils.nebulaworld.NebulaWorldLoader
import me.nebula.orbit.utils.nebulaworld.NebulaWorldWriter
import me.nebula.orbit.utils.replay.ReplayWorldCapture
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.worldedit.EditSessionManager
import me.nebula.orbit.utils.worldedit.SelectionRenderer
import me.nebula.orbit.utils.vanilla.VanillaModules
import me.nebula.orbit.utils.vanilla.installGameRuleCommand
import me.nebula.orbit.utils.worldedit.installEditCommands
import me.nebula.orbit.utils.worldedit.installWandListeners
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.timer.Task
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class BuildMode(private val worldPathOverride: String? = null) : ServerMode {

    private val logger = logger("BuildMode")

    override val maxPlayers: Int = 20
    override val cosmeticConfig: CosmeticConfig = CosmeticConfig()
    override val spawnPoint: Pos = Pos(0.5, 5.0, 0.5)
    override val defaultInstance: InstanceContainer = createInstance()

    private var selectionTask: Task? = null

    private fun createInstance(): InstanceContainer {
        val override = worldPathOverride
        if (override != null) {
            val path = Path.of(override)
            if (MapLoader.isNebulaFile(path)) {
                return NebulaWorldLoader.load("build", path)
            }
            val resolved = runCatching { MapLoader.resolve(override) }.getOrNull() // noqa: runCatching{}.getOrNull() as null check
            if (resolved != null) {
                return AnvilWorldLoader.load("build", resolved)
            }
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
                val name = argOrNull("name") ?: "build"
                val outputPath = Path.of("maps", "$name.nebula")
                Thread.startVirtualThread {
                    player.sendMessage(player.translate("orbit.build.save_start", "name" to name))
                    val world = ReplayWorldCapture.capture(defaultInstance)
                    Files.createDirectories(outputPath.parent)
                    NebulaWorldWriter.write(world, outputPath)
                    player.sendMessage(player.translate("orbit.build.save_complete", "chunks" to world.chunks.size.toString(), "path" to outputPath.toString(), "size" to (Files.size(outputPath) / 1024).toString()))
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

        selectionTask = defaultInstance.scheduler().buildTask { SelectionRenderer.tick() }
            .repeat(Duration.ofMillis(500))
            .schedule()

        logger.info { "Build mode installed" }
    }

    override fun shutdown() {
        selectionTask?.cancel()
        VanillaModules.disableAll(defaultInstance)
        EditSessionManager.clear()
    }
}
