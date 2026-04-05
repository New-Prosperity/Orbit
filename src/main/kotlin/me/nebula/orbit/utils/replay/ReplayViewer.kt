package me.nebula.orbit.utils.replay

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.nebulaworld.NebulaChunkLoader
import me.nebula.orbit.utils.nebulaworld.NebulaWorldLoader
import me.nebula.orbit.utils.nebulaworld.NebulaWorldReader
import me.nebula.orbit.utils.scheduler.repeat
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.Task
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

private val logger = logger("ReplayViewer")

class ReplayViewer(
    private val replayFile: ReplayFile,
) {

    private var instance: InstanceContainer? = null
    private var replayPlayer: ReplayPlayer? = null
    private val fakeEntities = ConcurrentHashMap<UUID, Entity>()
    private val viewers = ConcurrentHashMap.newKeySet<Player>()

    fun load(): CompletableFuture<InstanceContainer> {
        val future = CompletableFuture<InstanceContainer>()

        Thread.startVirtualThread {
            val inst = when (val source = replayFile.worldSource) {
                is ReplayWorldSource.Embedded -> {
                    val world = source.world
                    val name = "replay-${replayFile.header.matchId}"
                    val container = MinecraftServer.getInstanceManager().createInstanceContainer()
                    container.chunkLoader = NebulaChunkLoader(world)
                    container
                }
                is ReplayWorldSource.Reference -> {
                    val mapPath = Path.of("maps", "${source.mapName}.nebula")
                    NebulaWorldLoader.load("replay-${replayFile.header.matchId}", mapPath)
                }
            }
            instance = inst

            val world = when (val source = replayFile.worldSource) {
                is ReplayWorldSource.Embedded -> source.world
                is ReplayWorldSource.Reference -> NebulaWorldReader.read(Path.of("maps", "${source.mapName}.nebula"))
            }
            for (key in world.chunks.keys) {
                val chunk = world.chunkAt(
                    (key shr 32).toInt(),
                    key.toInt(),
                )
                if (chunk != null) {
                    inst.loadChunk(chunk.x, chunk.z).join()
                }
            }

            future.complete(inst)
        }

        return future
    }

    fun addViewer(player: Player) {
        val inst = instance ?: return
        viewers.add(player)
        player.gameMode = GameMode.SPECTATOR
        player.setInstance(inst, Pos(0.0, 65.0, 0.0))
    }

    fun removeViewer(player: Player) {
        viewers.remove(player)
    }

    fun play() {
        val player = ReplayPlayer(replayFile.data)
        replayPlayer = player

        player.onComplete {
            logger.info { "Replay ${replayFile.header.matchId} playback complete" }
            viewers.forEach { it.sendMessage(Component.text("Replay finished.")) }
        }

        player.play { frame ->
            processFrame(frame)
        }
    }

    fun pause() = replayPlayer?.pause()
    fun resume() = replayPlayer?.resume()
    fun setSpeed(speed: Double) = replayPlayer?.setSpeed(speed)
    fun seekTo(tick: Int) = replayPlayer?.seekTo(tick)

    fun stop() {
        replayPlayer?.stop()
        replayPlayer = null
        fakeEntities.values.forEach { it.remove() }
        fakeEntities.clear()
    }

    fun destroy() {
        stop()
        viewers.clear()
        val inst = instance ?: return
        MinecraftServer.getInstanceManager().unregisterInstance(inst)
        instance = null
    }

    val isPlaying: Boolean get() = replayPlayer?.isPlaying == true
    val currentTick: Int get() = replayPlayer?.currentPlayTick ?: 0
    val totalTicks: Int get() = replayPlayer?.totalTicks ?: 0
    val progress: Double get() = replayPlayer?.progressPercent ?: 0.0

    private fun processFrame(frame: ReplayFrame) {
        val inst = instance ?: return
        when (frame) {
            is ReplayFrame.Position -> {
                val entity = fakeEntities[frame.uuid]
                entity?.teleport(frame.pos)
                if (frame.sneaking) entity?.setSneaking(true)
            }
            is ReplayFrame.BlockChange -> {
                val block = Block.fromStateId(frame.blockId) ?: Block.AIR
                inst.setBlock(frame.x, frame.y, frame.z, block)
            }
            is ReplayFrame.Chat -> {
                val name = replayFile.data.playerNames[frame.uuid] ?: "Unknown"
                viewers.forEach { it.sendMessage(Component.text("<$name> ${frame.message}")) }
            }
            is ReplayFrame.EntitySpawn -> {
                val entity = Entity(EntityType.PLAYER)
                entity.customName = Component.text(frame.name)
                entity.isCustomNameVisible = true
                entity.setInstance(inst, Pos(0.0, 65.0, 0.0))
                fakeEntities[frame.uuid] = entity
            }
            is ReplayFrame.EntityDespawn -> {
                fakeEntities.remove(frame.uuid)?.remove()
            }
            is ReplayFrame.Death -> {
                val entity = fakeEntities[frame.uuid]
                entity?.let {
                    viewers.forEach { viewer ->
                        val name = replayFile.data.playerNames[frame.uuid] ?: "Unknown"
                        val killerName = frame.killerUuid?.let { replayFile.data.playerNames[it] } ?: "environment"
                        viewer.sendMessage(Component.text("$name was killed by $killerName"))
                    }
                }
            }
            is ReplayFrame.ItemHeld -> {}
        }
    }
}
