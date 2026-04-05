package me.nebula.orbit.utils.replay

import com.github.luben.zstd.Zstd
import me.nebula.orbit.utils.nebulaworld.GrowableBuffer
import me.nebula.orbit.utils.nebulaworld.NebulaWorld
import me.nebula.orbit.utils.nebulaworld.NebulaWorldReader
import me.nebula.orbit.utils.nebulaworld.NebulaWorldWriter
import net.minestom.server.coordinate.Pos
import net.minestom.server.item.ItemStack
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

private const val REPLAY_MAGIC = 0x4E655272
private const val REPLAY_VERSION: Short = 1
private const val PROTOCOL_VERSION = 775
private const val TICK_CHUNK_SIZE = 1000

private const val FRAME_POSITION: Byte = 0
private const val FRAME_BLOCK_CHANGE: Byte = 1
private const val FRAME_CHAT: Byte = 2
private const val FRAME_ITEM_HELD: Byte = 3
private const val FRAME_ENTITY_SPAWN: Byte = 4
private const val FRAME_ENTITY_DESPAWN: Byte = 5
private const val FRAME_DEATH: Byte = 6

private const val WORLD_REFERENCE: Byte = 0
private const val WORLD_EMBEDDED: Byte = 1

data class ReplayPlayerEntry(val uuid: UUID, val name: String, val skinValue: String?, val skinSignature: String?)

data class ReplayFileHeader(
    val matchId: String,
    val gamemode: String,
    val mapName: String,
    val recordedAt: Long,
    val durationTicks: Int,
    val players: List<ReplayPlayerEntry>,
)

sealed interface ReplayWorldSource {
    data class Reference(val mapName: String) : ReplayWorldSource
    data class Embedded(val world: NebulaWorld) : ReplayWorldSource
}

data class ReplayFile(
    val header: ReplayFileHeader,
    val worldSource: ReplayWorldSource,
    val data: ReplayData,
    val rawPackets: List<RecordedPacket> = emptyList(),
)

object ReplayFormat {

    fun write(
        replay: ReplayFile,
        path: Path,
    ) {
        Files.write(path, write(replay))
    }

    fun write(replay: ReplayFile): ByteArray {
        val buf = GrowableBuffer()

        buf.putInt(REPLAY_MAGIC)
        buf.putShort(REPLAY_VERSION)
        buf.putVarInt(PROTOCOL_VERSION)

        buf.putString(replay.header.matchId)
        buf.putString(replay.header.gamemode)
        buf.putString(replay.header.mapName)
        buf.putLong(replay.header.recordedAt)
        buf.putVarInt(replay.header.durationTicks)

        buf.putVarInt(replay.header.players.size)
        for (p in replay.header.players) {
            buf.putLong(p.uuid.mostSignificantBits)
            buf.putLong(p.uuid.leastSignificantBits)
            buf.putString(p.name)
            buf.putString(p.skinValue ?: "")
            buf.putString(p.skinSignature ?: "")
        }

        when (val world = replay.worldSource) {
            is ReplayWorldSource.Reference -> {
                buf.putByte(WORLD_REFERENCE)
                buf.putString(world.mapName)
            }
            is ReplayWorldSource.Embedded -> {
                buf.putByte(WORLD_EMBEDDED)
                val worldBytes = NebulaWorldWriter.write(world.world)
                buf.putByteArray(worldBytes)
            }
        }

        val tickChunks = buildTickChunks(replay.data.frames)
        buf.putVarInt(tickChunks.size)
        @Suppress("UNUSED_VARIABLE") val chunkDataStart = buf.position()

        val placeholders = mutableListOf<Int>()
        for (chunk in tickChunks) {
            buf.putVarInt(chunk.startTick)
            placeholders += buf.position()
            buf.putInt(0)
            buf.putInt(0)
        }

        val chunkOffsets = mutableListOf<Pair<Int, Int>>()
        for (chunk in tickChunks) {
            val frameBytes = encodeFrames(chunk.frames)
            val compressed = Zstd.compress(frameBytes)
            val offset = buf.position()
            buf.putBytes(compressed)
            chunkOffsets += offset to compressed.size
        }

        val result = buf.toByteArray()
        var placeholderIdx = 0
        for ((offset, size) in chunkOffsets) {
            val pos = placeholders[placeholderIdx++]
            writeIntAt(result, pos, offset)
            writeIntAt(result, pos + 4, size)
        }

        return result
    }

    fun read(path: Path): ReplayFile = read(Files.readAllBytes(path))

    fun read(data: ByteArray): ReplayFile {
        val buf = ByteBuffer.wrap(data)

        val magic = buf.int
        require(magic == REPLAY_MAGIC) { "Invalid replay magic" }
        val version = buf.short
        require(version <= REPLAY_VERSION) { "Unsupported replay version: $version" }
        val protocol = readVarInt(buf)

        val matchId = readString(buf)
        val gamemode = readString(buf)
        val mapName = readString(buf)
        val recordedAt = buf.long
        val durationTicks = readVarInt(buf)

        val playerCount = readVarInt(buf)
        val players = (0 until playerCount).map {
            val msb = buf.long
            val lsb = buf.long
            ReplayPlayerEntry(
                UUID(msb, lsb),
                readString(buf),
                readString(buf).ifEmpty { null },
                readString(buf).ifEmpty { null },
            )
        }

        val worldMode = buf.get()
        val worldSource = when (worldMode) {
            WORLD_REFERENCE -> ReplayWorldSource.Reference(readString(buf))
            WORLD_EMBEDDED -> {
                val worldBytes = readByteArray(buf)
                ReplayWorldSource.Embedded(NebulaWorldReader.read(worldBytes))
            }
            else -> error("Unknown world mode: $worldMode")
        }

        val chunkCount = readVarInt(buf)
        val chunks = (0 until chunkCount).map {
            val startTick = readVarInt(buf)
            val offset = buf.int
            val size = buf.int
            Triple(startTick, offset, size)
        }

        val allFrames = mutableListOf<ReplayFrame>()
        for ((_, offset, size) in chunks) {
            val compressed = ByteArray(size)
            System.arraycopy(data, offset, compressed, 0, size)
            val origSize = Zstd.decompressedSize(compressed)
            val output = ByteArray(if (origSize > 0) origSize.toInt() else size * 20)
            Zstd.decompress(output, compressed)
            val decompressed = output
            allFrames += decodeFrames(decompressed)
        }

        val header = ReplayFileHeader(matchId, gamemode, mapName, recordedAt, durationTicks, players)
        val playerNames = players.associate { it.uuid to it.name }
        return ReplayFile(header, worldSource, ReplayData(allFrames, playerNames))
    }

    private fun buildTickChunks(frames: List<ReplayFrame>): List<TickChunk> {
        if (frames.isEmpty()) return emptyList()
        val sorted = frames.sortedBy { it.tickOffset }
        val chunks = mutableListOf<TickChunk>()
        var chunkStart = 0
        var chunkFrames = mutableListOf<ReplayFrame>()

        for (frame in sorted) {
            if (frame.tickOffset - chunkStart >= TICK_CHUNK_SIZE && chunkFrames.isNotEmpty()) {
                chunks += TickChunk(chunkStart, chunkFrames)
                chunkStart = frame.tickOffset
                chunkFrames = mutableListOf()
            }
            chunkFrames += frame
        }
        if (chunkFrames.isNotEmpty()) chunks += TickChunk(chunkStart, chunkFrames)
        return chunks
    }

    private fun encodeFrames(frames: List<ReplayFrame>): ByteArray {
        val buf = GrowableBuffer()
        buf.putVarInt(frames.size)
        for (f in frames) {
            buf.putVarInt(f.tickOffset)
            when (f) {
                is ReplayFrame.Position -> {
                    buf.putByte(FRAME_POSITION)
                    buf.putLong(f.uuid.mostSignificantBits)
                    buf.putLong(f.uuid.leastSignificantBits)
                    buf.putDouble(f.pos.x())
                    buf.putDouble(f.pos.y())
                    buf.putDouble(f.pos.z())
                    buf.putFloat(f.pos.yaw())
                    buf.putFloat(f.pos.pitch())
                    buf.putByte(if (f.sneaking) 1 else 0)
                }
                is ReplayFrame.BlockChange -> {
                    buf.putByte(FRAME_BLOCK_CHANGE)
                    buf.putInt(f.x)
                    buf.putInt(f.y)
                    buf.putInt(f.z)
                    buf.putVarInt(f.blockId)
                }
                is ReplayFrame.Chat -> {
                    buf.putByte(FRAME_CHAT)
                    buf.putLong(f.uuid.mostSignificantBits)
                    buf.putLong(f.uuid.leastSignificantBits)
                    buf.putString(f.message)
                }
                is ReplayFrame.ItemHeld -> {
                    buf.putByte(FRAME_ITEM_HELD)
                    buf.putLong(f.uuid.mostSignificantBits)
                    buf.putLong(f.uuid.leastSignificantBits)
                    buf.putVarInt(f.slot)
                }
                is ReplayFrame.EntitySpawn -> {
                    buf.putByte(FRAME_ENTITY_SPAWN)
                    buf.putLong(f.uuid.mostSignificantBits)
                    buf.putLong(f.uuid.leastSignificantBits)
                    buf.putString(f.name)
                    buf.putString(f.skinValue ?: "")
                    buf.putString(f.skinSignature ?: "")
                }
                is ReplayFrame.EntityDespawn -> {
                    buf.putByte(FRAME_ENTITY_DESPAWN)
                    buf.putLong(f.uuid.mostSignificantBits)
                    buf.putLong(f.uuid.leastSignificantBits)
                }
                is ReplayFrame.Death -> {
                    buf.putByte(FRAME_DEATH)
                    buf.putLong(f.uuid.mostSignificantBits)
                    buf.putLong(f.uuid.leastSignificantBits)
                    buf.putByte(if (f.killerUuid != null) 1 else 0)
                    if (f.killerUuid != null) {
                        buf.putLong(f.killerUuid.mostSignificantBits)
                        buf.putLong(f.killerUuid.leastSignificantBits)
                    }
                }
            }
        }
        return buf.toByteArray()
    }

    private fun decodeFrames(data: ByteArray): List<ReplayFrame> {
        val buf = ByteBuffer.wrap(data)
        val count = readVarInt(buf)
        return (0 until count).map { decodeFrame(buf) }
    }

    private fun decodeFrame(buf: ByteBuffer): ReplayFrame {
        val tick = readVarInt(buf)
        return when (buf.get()) {
            FRAME_POSITION -> ReplayFrame.Position(
                tick, readUuid(buf),
                Pos(buf.double, buf.double, buf.double, buf.float, buf.float),
                buf.get() != 0.toByte(),
            )
            FRAME_BLOCK_CHANGE -> ReplayFrame.BlockChange(tick, buf.int, buf.int, buf.int, readVarInt(buf))
            FRAME_CHAT -> ReplayFrame.Chat(tick, readUuid(buf), readString(buf))
            FRAME_ITEM_HELD -> ReplayFrame.ItemHeld(tick, readUuid(buf), readVarInt(buf), ItemStack.AIR)
            FRAME_ENTITY_SPAWN -> ReplayFrame.EntitySpawn(
                tick, readUuid(buf), readString(buf),
                readString(buf).ifEmpty { null }, readString(buf).ifEmpty { null },
            )
            FRAME_ENTITY_DESPAWN -> ReplayFrame.EntityDespawn(tick, readUuid(buf))
            FRAME_DEATH -> {
                val uuid = readUuid(buf)
                val hasKiller = buf.get() != 0.toByte()
                val killer = if (hasKiller) readUuid(buf) else null
                ReplayFrame.Death(tick, uuid, killer)
            }
            else -> error("Unknown frame type")
        }
    }

    private fun readUuid(buf: ByteBuffer): UUID = UUID(buf.long, buf.long)

    private fun readVarInt(buf: ByteBuffer): Int {
        var value = 0; var shift = 0; var b: Int
        do { b = buf.get().toInt() and 0xFF; value = value or ((b and 0x7F) shl shift); shift += 7 } while (b and 0x80 != 0)
        return value
    }

    private fun readString(buf: ByteBuffer): String {
        val len = readVarInt(buf); val bytes = ByteArray(len); buf.get(bytes); return String(bytes, Charsets.UTF_8)
    }

    private fun readByteArray(buf: ByteBuffer): ByteArray {
        val len = readVarInt(buf); val bytes = ByteArray(len); buf.get(bytes); return bytes
    }

    private fun writeIntAt(data: ByteArray, pos: Int, value: Int) {
        data[pos] = (value shr 24).toByte()
        data[pos + 1] = (value shr 16).toByte()
        data[pos + 2] = (value shr 8).toByte()
        data[pos + 3] = value.toByte()
    }

    private data class TickChunk(val startTick: Int, val frames: List<ReplayFrame>)
}

internal fun GrowableBuffer.putShort(v: Short) {
    putByte((v.toInt() shr 8).toByte())
    putByte(v.toByte())
}

internal fun GrowableBuffer.putDouble(v: Double) {
    putLong(v.toRawBits())
}

internal fun GrowableBuffer.putFloat(v: Float) {
    putInt(v.toRawBits())
}
