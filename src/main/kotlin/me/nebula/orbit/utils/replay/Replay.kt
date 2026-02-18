package me.nebula.orbit.utils.replay

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class ReplayFrame(val tickOffset: Int) {
    class Position(tickOffset: Int, val uuid: UUID, val pos: Pos) : ReplayFrame(tickOffset)
    class BlockChange(tickOffset: Int, val x: Int, val y: Int, val z: Int, val blockId: Int) : ReplayFrame(tickOffset)
    class Chat(tickOffset: Int, val uuid: UUID, val message: String) : ReplayFrame(tickOffset)
    class ItemHeld(tickOffset: Int, val uuid: UUID, val slot: Int, val item: ItemStack) : ReplayFrame(tickOffset)
}

class ReplayRecorder {

    private val frames = mutableListOf<ReplayFrame>()
    private var startTick = 0L
    private var recording = false

    fun start() {
        startTick = System.currentTimeMillis()
        frames.clear()
        recording = true
    }

    fun stop(): ReplayData {
        recording = false
        return ReplayData(frames.toList())
    }

    fun recordPosition(player: Player) {
        if (!recording) return
        val offset = ((System.currentTimeMillis() - startTick) / 50).toInt()
        frames.add(ReplayFrame.Position(offset, player.uuid, player.position))
    }

    fun recordBlockChange(x: Int, y: Int, z: Int, blockId: Int) {
        if (!recording) return
        val offset = ((System.currentTimeMillis() - startTick) / 50).toInt()
        frames.add(ReplayFrame.BlockChange(offset, x, y, z, blockId))
    }

    fun recordChat(player: Player, message: String) {
        if (!recording) return
        val offset = ((System.currentTimeMillis() - startTick) / 50).toInt()
        frames.add(ReplayFrame.Chat(offset, player.uuid, message))
    }

    val isRecording: Boolean get() = recording
}

data class ReplayData(val frames: List<ReplayFrame>) {
    val durationTicks: Int get() = frames.maxOfOrNull { it.tickOffset } ?: 0
}

class ReplayPlayer(private val data: ReplayData) {

    private var playing = false
    private var currentTick = 0

    fun play(onFrame: (ReplayFrame) -> Unit) {
        playing = true
        currentTick = 0

        MinecraftServer.getSchedulerManager().buildTask {
            if (!playing) return@buildTask
            val tickFrames = data.frames.filter { it.tickOffset == currentTick }
            tickFrames.forEach(onFrame)
            currentTick++
            if (currentTick > data.durationTicks) playing = false
        }.repeat(TaskSchedule.tick(1)).schedule()
    }

    fun stop() {
        playing = false
    }

    val isPlaying: Boolean get() = playing
}

object ReplayManager {

    private val recordings = ConcurrentHashMap<String, ReplayData>()

    fun save(name: String, data: ReplayData) {
        recordings[name] = data
    }

    fun load(name: String): ReplayData? = recordings[name]

    fun delete(name: String) = recordings.remove(name)

    fun list(): Set<String> = recordings.keys.toSet()
}
