package me.nebula.orbit.utils.replay

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.nebulaworld.NebulaChunkLoader
import me.nebula.orbit.utils.nebulaworld.NebulaWorldLoader
import me.nebula.orbit.utils.nebulaworld.NebulaWorldReader
import me.nebula.orbit.utils.scheduler.delay
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import java.nio.file.Path
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import me.nebula.gravity.translation.Keys

private val logger = logger("ReplayViewer")

class ReplayViewer(
    private val replayFile: ReplayFile,
) {

    private var instance: InstanceContainer? = null
    private var replayPlayer: ReplayPlayer? = null
    private val fakeEntities = ConcurrentHashMap<UUID, Entity>()
    private val entitySkins = ConcurrentHashMap<UUID, Pair<String?, String?>>()
    private val entityNames = ConcurrentHashMap<UUID, String>()
    private val viewers = ConcurrentHashMap.newKeySet<Player>()

    fun load(): CompletableFuture<InstanceContainer> {
        val future = CompletableFuture<InstanceContainer>()

        Thread.startVirtualThread {
            val inst = when (val source = replayFile.worldSource) {
                is ReplayWorldSource.Embedded -> {
                    val world = source.world
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

            for (entry in replayFile.header.players) {
                entitySkins[entry.uuid] = entry.skinValue to entry.skinSignature
                entityNames[entry.uuid] = entry.name
            }

            future.complete(inst)
        }

        return future
    }

    fun addViewer(player: Player) {
        val inst = instance ?: return
        viewers.add(player)
        player.gameMode = GameMode.SPECTATOR
        val spawn = resolveSpawnPos()
        player.setInstance(inst, spawn)
    }

    fun removeViewer(player: Player) {
        viewers.remove(player)
    }

    fun play() {
        val player = ReplayPlayer(replayFile.data)
        replayPlayer = player

        player.onComplete {
            logger.info { "Replay ${replayFile.header.matchId} playback complete" }
            viewers.forEach { it.sendMessage(it.translate(Keys.Orbit.Replay.Finished)) }
        }

        player.play { frame ->
            processFrame(frame)
        }
    }

    fun pause() = replayPlayer?.pause()
    fun resume() = replayPlayer?.resume()
    fun setSpeed(speed: Double) = replayPlayer?.setSpeed(speed)
    fun seekTo(tick: Int) = replayPlayer?.seekTo(tick)

    fun setPerspective(uuid: UUID?) {
        replayPlayer?.setPerspective(uuid)
    }

    fun stop() {
        replayPlayer?.stop()
        replayPlayer = null
        for ((uuid, entity) in fakeEntities) {
            sendPlayerInfoRemove(uuid)
            entity.remove()
        }
        fakeEntities.clear()
        entitySkins.clear()
        entityNames.clear()
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

    fun highlights(): List<ReplayHighlight> {
        val frames = replayFile.data.frames.map { it.tickOffset to it }
        val metadata = ReplayMetadata(
            gameMode = replayFile.header.gamemode,
            mapName = replayFile.header.mapName,
            recordedAt = replayFile.header.recordedAt,
            playerCount = replayFile.header.players.size,
            durationTicks = replayFile.header.durationTicks,
        )
        return ReplayHighlights.detect(frames, metadata)
    }

    fun playerEntries(): List<ReplayPlayerEntry> = replayFile.header.players

    private fun resolveSpawnPos(): Pos {
        val firstPos = replayFile.data.frames
            .filterIsInstance<ReplayFrame.Position>()
            .firstOrNull()
        return firstPos?.pos ?: Pos(0.0, 65.0, 0.0)
    }

    private fun processFrame(frame: ReplayFrame) {
        val inst = instance ?: return
        when (frame) {
            is ReplayFrame.Position -> {
                val entity = fakeEntities[frame.uuid]
                entity?.teleport(frame.pos)
                if (entity != null) {
                    entity.setSneaking(frame.sneaking)
                }

                val perspective = replayPlayer?.getPerspective()
                if (perspective != null && perspective == frame.uuid) {
                    viewers.forEach { viewer ->
                        viewer.teleport(frame.pos)
                    }
                }
            }
            is ReplayFrame.BlockChange -> {
                val block = Block.fromStateId(frame.blockId) ?: Block.AIR
                inst.setBlock(frame.x, frame.y, frame.z, block)
            }
            is ReplayFrame.Chat -> {
                val name = entityNames[frame.uuid] ?: replayFile.data.playerNames[frame.uuid] ?: "Unknown"
                viewers.forEach { it.sendMessage(Component.text("<$name> ${frame.message}")) }
            }
            is ReplayFrame.EntitySpawn -> {
                spawnFakePlayer(frame.uuid, frame.name, frame.skinValue, frame.skinSignature)
            }
            is ReplayFrame.EntityDespawn -> {
                despawnFakePlayer(frame.uuid)
            }
            is ReplayFrame.Death -> {
                val entity = fakeEntities[frame.uuid]
                if (entity != null) {
                    val name = entityNames[frame.uuid] ?: replayFile.data.playerNames[frame.uuid] ?: "Unknown"
                    val killerName = frame.killerUuid?.let { entityNames[it] ?: replayFile.data.playerNames[it] } ?: "environment"
                    viewers.forEach { viewer ->
                        viewer.sendMessage(Component.text("$name was killed by $killerName"))
                    }
                    playDeathAnimation(frame.uuid, entity)
                }
            }
            is ReplayFrame.ItemHeld -> {}
        }
    }

    private fun spawnFakePlayer(uuid: UUID, name: String, skinValue: String?, skinSignature: String?) {
        entitySkins[uuid] = skinValue to skinSignature
        entityNames[uuid] = name

        val entity = Entity(EntityType.PLAYER)
        entity.customName = Component.text(name)
        entity.isCustomNameVisible = true

        val inst = instance ?: return
        entity.setInstance(inst, Pos(0.0, 65.0, 0.0))
        fakeEntities[uuid] = entity

        val properties = if (skinValue != null) {
            listOf(PlayerInfoUpdatePacket.Property("textures", skinValue, skinSignature))
        } else {
            emptyList()
        }
        val infoPacket = PlayerInfoUpdatePacket(
            EnumSet.of(
                PlayerInfoUpdatePacket.Action.ADD_PLAYER,
                PlayerInfoUpdatePacket.Action.UPDATE_LISTED,
            ),
            listOf(PlayerInfoUpdatePacket.Entry(
                uuid, name, properties, true, 0, GameMode.SURVIVAL, null, null, 0, true,
            )),
        )

        viewers.forEach { viewer ->
            viewer.sendPacket(infoPacket)
        }
    }

    private fun despawnFakePlayer(uuid: UUID) {
        val entity = fakeEntities.remove(uuid)
        entity?.remove()
        sendPlayerInfoRemove(uuid)
        entitySkins.remove(uuid)
        entityNames.remove(uuid)
    }

    private fun playDeathAnimation(uuid: UUID, entity: Entity) {
        viewers.forEach { viewer ->
            viewer.sendPacket(DestroyEntitiesPacket(entity.entityId))
        }

        delay(20) {
            despawnFakePlayer(uuid)
        }
    }

    private fun sendPlayerInfoRemove(uuid: UUID) {
        val packet = PlayerInfoRemovePacket(listOf(uuid))
        viewers.forEach { it.sendPacket(packet) }
    }
}
