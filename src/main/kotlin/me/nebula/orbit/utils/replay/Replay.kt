package me.nebula.orbit.utils.replay

import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.ether.utils.storage.StorageScope
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.kyori.adventure.nbt.TagStringIO
import net.minestom.server.item.ItemStack
import net.minestom.server.timer.Task
import java.io.ByteArrayOutputStream
import java.lang.reflect.Type
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

sealed class ReplayFrame(val tickOffset: Int) {
    class Position(tickOffset: Int, val uuid: UUID, val pos: Pos, val sneaking: Boolean = false) : ReplayFrame(tickOffset)
    class BlockChange(tickOffset: Int, val x: Int, val y: Int, val z: Int, val blockId: Int) : ReplayFrame(tickOffset)
    class Chat(tickOffset: Int, val uuid: UUID, val message: String) : ReplayFrame(tickOffset)
    class ItemHeld(tickOffset: Int, val uuid: UUID, val slot: Int, val item: ItemStack) : ReplayFrame(tickOffset)
    class EntitySpawn(tickOffset: Int, val uuid: UUID, val name: String, val skinValue: String?, val skinSignature: String?) : ReplayFrame(tickOffset)
    class EntityDespawn(tickOffset: Int, val uuid: UUID) : ReplayFrame(tickOffset)
    class Death(tickOffset: Int, val uuid: UUID, val killerUuid: UUID?) : ReplayFrame(tickOffset)
}

data class ReplayMetadata(
    val gameMode: String = "",
    val mapName: String = "",
    val recordedAt: Long = System.currentTimeMillis(),
    val playerCount: Int = 0,
    val winnerUuid: UUID? = null,
    val winnerName: String? = null,
    val durationTicks: Int = 0,
)

class ReplayRecorder {

    private val frames = java.util.Collections.synchronizedList(mutableListOf<ReplayFrame>())
    private var startTimeMillis = 0L
    @Volatile private var recording = false
    private val playerNames = ConcurrentHashMap<UUID, String>()
    private val playerSkins = ConcurrentHashMap<UUID, Pair<String?, String?>>()
    @Volatile var worldSnapshot: me.nebula.orbit.utils.nebulaworld.NebulaWorld? = null
        private set

    fun start(instance: net.minestom.server.instance.Instance? = null) {
        startTimeMillis = System.currentTimeMillis()
        frames.clear()
        playerNames.clear()
        playerSkins.clear()
        if (instance != null) {
            Thread.startVirtualThread {
                worldSnapshot = ReplayWorldCapture.capture(instance)
            }
        }
        recording = true
    }

    fun stop(): ReplayData {
        recording = false
        val snapshot = frames.toList()
        val names = playerNames.toMap()
        playerNames.clear()
        playerSkins.clear()
        return ReplayData(snapshot, names)
    }

    fun buildReplayFile(matchId: String, gameMode: String, mapName: String): ReplayFile {
        val data = stop()
        val players = playerNames.map { (uuid, name) ->
            val (skinValue, skinSig) = playerSkins[uuid] ?: (null to null)
            ReplayPlayerEntry(uuid, name, skinValue, skinSig)
        }
        val header = ReplayFileHeader(matchId, gameMode, mapName, System.currentTimeMillis(), data.durationTicks, players)
        val worldSource = worldSnapshot?.let { ReplayWorldSource.Embedded(it) }
            ?: ReplayWorldSource.Reference(mapName)
        return ReplayFile(header, worldSource, data)
    }

    private fun offset(): Int = ((System.currentTimeMillis() - startTimeMillis) / 50).toInt()

    fun recordPosition(player: Player) {
        if (!recording) return
        frames.add(ReplayFrame.Position(offset(), player.uuid, player.position, player.isSneaking))
    }

    fun recordBlockChange(x: Int, y: Int, z: Int, blockId: Int) {
        if (!recording) return
        frames.add(ReplayFrame.BlockChange(offset(), x, y, z, blockId))
    }

    fun recordChat(player: Player, message: String) {
        if (!recording) return
        frames.add(ReplayFrame.Chat(offset(), player.uuid, message))
    }

    fun recordItemHeld(player: Player, slot: Int, item: ItemStack) {
        if (!recording) return
        frames.add(ReplayFrame.ItemHeld(offset(), player.uuid, slot, item))
    }

    fun recordPlayerJoin(player: Player) {
        if (!recording) return
        playerNames[player.uuid] = player.username
        val skin = player.skin
        playerSkins[player.uuid] = skin?.textures() to skin?.signature()
        frames.add(ReplayFrame.EntitySpawn(
            offset(), player.uuid, player.username,
            skin?.textures(), skin?.signature(),
        ))
    }

    fun recordPlayerLeave(player: Player) {
        if (!recording) return
        frames.add(ReplayFrame.EntityDespawn(offset(), player.uuid))
    }

    fun recordDeath(victim: Player, killer: Player?) {
        if (!recording) return
        frames.add(ReplayFrame.Death(offset(), victim.uuid, killer?.uuid))
    }

    val isRecording: Boolean get() = recording
}

data class ReplayData(
    val frames: List<ReplayFrame>,
    val playerNames: Map<UUID, String> = emptyMap(),
) {
    val durationTicks: Int get() = frames.maxOfOrNull { it.tickOffset } ?: 0
}

class ReplayPlayer(private val data: ReplayData) {

    private val framesByTick: Map<Int, List<ReplayFrame>> = data.frames.groupBy { it.tickOffset }
    private var playing = false
    private var currentTick = 0
    private var speed = 1.0
    private var task: Task? = null
    private var perspective: UUID? = null
    private var onFrame: ((ReplayFrame) -> Unit)? = null
    private var onSpeedChange: ((Double) -> Unit)? = null
    private var onPerspectiveChange: ((UUID?) -> Unit)? = null
    private var onComplete: (() -> Unit)? = null
    private var tickAccumulator = 0.0

    fun play(onFrame: (ReplayFrame) -> Unit) {
        this.onFrame = onFrame
        playing = true
        currentTick = 0
        tickAccumulator = 0.0

        task = repeat(1) {
            if (!playing) return@repeat

            tickAccumulator += speed
            while (tickAccumulator >= 1.0) {
                val tickFrames = framesByTick[currentTick]
                tickFrames?.forEach { frame ->
                    onFrame(frame)
                }
                currentTick++
                tickAccumulator -= 1.0

                if (currentTick > data.durationTicks) {
                    playing = false
                    task?.cancel()
                    task = null
                    onComplete?.invoke()
                    return@repeat
                }
            }
        }
    }

    fun stop() {
        playing = false
        task?.cancel()
        task = null
    }

    fun pause() {
        playing = false
    }

    fun resume() {
        val frame = onFrame
        if (task == null && frame != null) {
            play(frame)
        } else {
            playing = true
        }
    }

    fun setSpeed(newSpeed: Double) {
        require(newSpeed > 0) { "Speed must be positive" }
        speed = newSpeed
        onSpeedChange?.invoke(newSpeed)
    }

    fun getSpeed(): Double = speed

    fun seekTo(tick: Int) {
        currentTick = tick.coerceIn(0, data.durationTicks)
    }

    fun setPerspective(uuid: UUID?) {
        perspective = uuid
        onPerspectiveChange?.invoke(uuid)
    }

    fun getPerspective(): UUID? = perspective

    fun availablePerspectives(): Set<UUID> =
        data.frames.filterIsInstance<ReplayFrame.EntitySpawn>().map { it.uuid }.toSet()

    fun onSpeedChange(handler: (Double) -> Unit) { onSpeedChange = handler }
    fun onPerspectiveChange(handler: (UUID?) -> Unit) { onPerspectiveChange = handler }
    fun onComplete(handler: () -> Unit) { onComplete = handler }

    val isPlaying: Boolean get() = playing
    val currentPlayTick: Int get() = currentTick
    val totalTicks: Int get() = data.durationTicks
    val progressPercent: Double get() = if (data.durationTicks == 0) 0.0 else currentTick.toDouble() / data.durationTicks
}

object ReplayManager {

    private val recordings = ConcurrentHashMap<String, ReplayData>()

    fun save(name: String, data: ReplayData) {
        recordings[name] = data
    }

    fun load(name: String): ReplayData? = recordings[name]

    fun delete(name: String) = recordings.remove(name)

    fun list(): Set<String> = recordings.keys.toSet()

    fun clear() = recordings.clear()
}

object ReplayStorage {

    @Volatile private var scope: StorageScope? = null
    private val gson: Gson = GsonProvider.builder()
        .registerTypeAdapter(ReplayFrame::class.java, ReplayFrameSerializer())
        .create()

    fun initialize(scope: StorageScope) {
        this.scope = scope
    }

    fun isInitialized(): Boolean = scope != null

    fun save(name: String, data: ReplayData, metadata: ReplayMetadata = ReplayMetadata()) {
        val s = scope ?: error("ReplayStorage not initialized")
        val wrapper = ReplayWrapper(metadata, data.playerNames, data.frames)
        val json = gson.toJson(wrapper)
        val compressed = compress(json.toByteArray())
        s.upload("$name.replay.gz", compressed)
    }

    fun load(name: String): Pair<ReplayMetadata, ReplayData>? {
        val s = scope ?: error("ReplayStorage not initialized")
        if (!s.exists("$name.replay.gz")) return null

        val compressed = s.download("$name.replay.gz")
        val json = String(decompress(compressed))
        val wrapper = gson.fromJson(json, ReplayWrapper::class.java)
        return wrapper.metadata to ReplayData(wrapper.frames, wrapper.playerNames)
    }

    fun delete(name: String) {
        val s = scope ?: error("ReplayStorage not initialized")
        s.delete("$name.replay.gz")
    }

    fun list(): List<String> {
        val s = scope ?: return emptyList()
        return s.list().map {
            it.name.removeSuffix(".replay.gz")
        }
    }

    fun exists(name: String): Boolean {
        val s = scope ?: return false
        return s.exists("$name.replay.gz") || s.exists("$name.nebr")
    }

    fun saveBinary(name: String, replayFile: ReplayFile) {
        val s = scope ?: error("ReplayStorage not initialized")
        val bytes = ReplayFormat.write(replayFile)
        s.upload("$name.nebr", bytes)
    }

    fun loadBinary(name: String): ReplayFile? {
        val s = scope ?: error("ReplayStorage not initialized")
        if (!s.exists("$name.nebr")) return null
        val bytes = s.download("$name.nebr")
        return ReplayFormat.read(bytes)
    }

    fun existsBinary(name: String): Boolean {
        val s = scope ?: return false
        return s.exists("$name.nebr")
    }

    private fun compress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size)
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun decompress(data: ByteArray): ByteArray =
        GZIPInputStream(data.inputStream()).use { it.readAllBytes() }
}

private data class ReplayWrapper(
    val metadata: ReplayMetadata,
    val playerNames: Map<UUID, String>,
    val frames: List<ReplayFrame>,
)

private class ReplayFrameSerializer : JsonSerializer<ReplayFrame>, JsonDeserializer<ReplayFrame> {

    override fun serialize(src: ReplayFrame, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        obj.addProperty("tick", src.tickOffset)
        when (src) {
            is ReplayFrame.Position -> {
                obj.addProperty("type", "pos")
                obj.addProperty("uuid", src.uuid.toString())
                obj.addProperty("x", src.pos.x())
                obj.addProperty("y", src.pos.y())
                obj.addProperty("z", src.pos.z())
                obj.addProperty("yaw", src.pos.yaw())
                obj.addProperty("pitch", src.pos.pitch())
                obj.addProperty("sneak", src.sneaking)
            }
            is ReplayFrame.BlockChange -> {
                obj.addProperty("type", "block")
                obj.addProperty("x", src.x)
                obj.addProperty("y", src.y)
                obj.addProperty("z", src.z)
                obj.addProperty("bid", src.blockId)
            }
            is ReplayFrame.Chat -> {
                obj.addProperty("type", "chat")
                obj.addProperty("uuid", src.uuid.toString())
                obj.addProperty("msg", src.message)
            }
            is ReplayFrame.ItemHeld -> {
                obj.addProperty("type", "item")
                obj.addProperty("uuid", src.uuid.toString())
                obj.addProperty("slot", src.slot)
                obj.addProperty("snbt", TagStringIO.get().asString(src.item.toItemNBT()))
            }
            is ReplayFrame.EntitySpawn -> {
                obj.addProperty("type", "spawn")
                obj.addProperty("uuid", src.uuid.toString())
                obj.addProperty("name", src.name)
                src.skinValue?.let { obj.addProperty("sv", it) }
                src.skinSignature?.let { obj.addProperty("ss", it) }
            }
            is ReplayFrame.EntityDespawn -> {
                obj.addProperty("type", "despawn")
                obj.addProperty("uuid", src.uuid.toString())
            }
            is ReplayFrame.Death -> {
                obj.addProperty("type", "death")
                obj.addProperty("uuid", src.uuid.toString())
                src.killerUuid?.let { obj.addProperty("killer", it.toString()) }
            }
        }
        return obj
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ReplayFrame {
        val obj = json.asJsonObject
        val tick = obj.get("tick").asInt
        return when (obj.get("type").asString) {
            "pos" -> ReplayFrame.Position(
                tick, UUID.fromString(obj.get("uuid").asString),
                Pos(obj.get("x").asDouble, obj.get("y").asDouble, obj.get("z").asDouble,
                    obj.get("yaw").asFloat, obj.get("pitch").asFloat),
                obj.get("sneak")?.asBoolean ?: false,
            )
            "block" -> ReplayFrame.BlockChange(tick, obj.get("x").asInt, obj.get("y").asInt, obj.get("z").asInt, obj.get("bid").asInt)
            "chat" -> ReplayFrame.Chat(tick, UUID.fromString(obj.get("uuid").asString), obj.get("msg").asString)
            "item" -> {
                val snbt = obj.get("snbt")?.asString
                val item = if (snbt != null) ItemStack.fromItemNBT(TagStringIO.get().asCompound(snbt)) else ItemStack.AIR
                ReplayFrame.ItemHeld(tick, UUID.fromString(obj.get("uuid").asString), obj.get("slot").asInt, item)
            }
            "spawn" -> ReplayFrame.EntitySpawn(
                tick, UUID.fromString(obj.get("uuid").asString), obj.get("name").asString,
                obj.get("sv")?.asString, obj.get("ss")?.asString,
            )
            "despawn" -> ReplayFrame.EntityDespawn(tick, UUID.fromString(obj.get("uuid").asString))
            "death" -> ReplayFrame.Death(tick, UUID.fromString(obj.get("uuid").asString), obj.get("killer")?.let { UUID.fromString(it.asString) })
            else -> error("Unknown replay frame type: ${obj.get("type").asString}")
        }
    }
}
