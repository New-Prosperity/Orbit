package me.nebula.orbit.utils.replay

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.nebulaworld.NebulaWorld
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerPacketOutEvent
import net.minestom.server.instance.Instance
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.NetworkBuffer
import net.minestom.server.network.packet.PacketWriting
import net.minestom.server.network.packet.server.ServerPacket
import net.minestom.server.network.packet.server.play.ChunkDataPacket
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import net.minestom.server.network.packet.server.play.UpdateLightPacket
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = logger("PacketReplayRecorder")

private val FILTERED_PACKETS = setOf<Class<out ServerPacket>>(
    ChunkDataPacket::class.java,
    UpdateLightPacket::class.java,
    PlayerInfoUpdatePacket::class.java,
    PlayerInfoRemovePacket::class.java,
)

data class RecordedPacket(val tickOffset: Int, val packetBytes: ByteArray) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

class PacketReplayRecorder {

    private val packets = Collections.synchronizedList(mutableListOf<RecordedPacket>())
    private val seenHashes = ConcurrentHashMap.newKeySet<Long>()
    private var startTimeMillis = 0L
    @Volatile private var recording = false
    @Volatile var worldSnapshot: NebulaWorld? = null
        private set
    private var eventNode: EventNode<Event>? = null
    @Volatile private var targetInstance: Instance? = null
    private val playerNames = ConcurrentHashMap<UUID, String>()
    private val playerSkins = ConcurrentHashMap<UUID, Pair<String?, String?>>()

    val isRecording: Boolean get() = recording

    fun start(instance: Instance) {
        startTimeMillis = System.currentTimeMillis()
        packets.clear()
        seenHashes.clear()
        playerNames.clear()
        playerSkins.clear()
        recording = true

        Thread.startVirtualThread {
            worldSnapshot = ReplayWorldCapture.capture(instance)
            logger.info { "Replay world snapshot captured: ${worldSnapshot?.chunks?.size} chunks" }
        }

        for (player in instance.players) {
            playerNames[player.uuid] = player.username
            val skin = player.skin
            playerSkins[player.uuid] = skin?.textures() to skin?.signature()
        }

        targetInstance = instance

        val node = EventNode.all("packet-replay-capture")
        node.addListener(PlayerPacketOutEvent::class.java) { event ->
            if (!recording) return@addListener
            if (event.player.instance !== targetInstance) return@addListener
            val packet = event.packet
            if (packet !is ServerPacket.Play) return@addListener
            if (FILTERED_PACKETS.any { it.isInstance(packet) }) return@addListener

            val tick = currentTick()
            val bytes = serializePacket(packet)
            if (bytes.isEmpty()) return@addListener

            val hash = tick.toLong() shl 32 or bytes.contentHashCode().toLong()
            if (!seenHashes.add(hash)) return@addListener

            packets += RecordedPacket(tick, bytes)
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node

        logger.info { "Packet replay recording started" }
    }

    fun stop(instance: Instance): List<RecordedPacket> {
        recording = false
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        targetInstance = null
        seenHashes.clear()
        val result = synchronized(packets) { packets.toList() }
        logger.info { "Packet replay recording stopped: ${result.size} packets over ${currentTick()} ticks" }
        return result
    }

    fun buildReplayFile(instance: Instance, matchId: String, gameMode: String, mapName: String): ReplayFile {
        val recordedPackets = stop(instance)

        val semanticFrames = recordedPackets.map { rp ->
            ReplayFrame.BlockChange(rp.tickOffset, 0, 0, 0, 0)
        }

        val players = playerNames.map { (uuid, name) ->
            val (skinValue, skinSig) = playerSkins[uuid] ?: (null to null)
            ReplayPlayerEntry(uuid, name, skinValue, skinSig)
        }

        val header = ReplayFileHeader(
            matchId, gameMode, mapName,
            System.currentTimeMillis(),
            currentTick(),
            players,
        )

        val worldSource = worldSnapshot?.let { ReplayWorldSource.Embedded(it) }
            ?: ReplayWorldSource.Reference(mapName)

        return ReplayFile(header, worldSource, ReplayData(emptyList(), playerNames.toMap()), recordedPackets)
    }

    private fun currentTick(): Int = ((System.currentTimeMillis() - startTimeMillis) / 50).toInt()

    private fun serializePacket(packet: ServerPacket.Play): ByteArray {
        return try {
            val buffer = NetworkBuffer.resizableBuffer()
            PacketWriting.writeFramedPacket(buffer, ConnectionState.PLAY, packet, 0)
            val size = buffer.readableBytes()
            if (size == 0L) return ByteArray(0)
            val bytes = ByteArray(size.toInt())
            buffer.copyTo(0L, bytes, 0, size)
            bytes
        } catch (_: Exception) {
            ByteArray(0)
        }
    }
}
