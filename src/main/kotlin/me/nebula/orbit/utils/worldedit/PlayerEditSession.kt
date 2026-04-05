package me.nebula.orbit.utils.worldedit

import me.nebula.orbit.utils.schematic.Schematic
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val MAX_HISTORY = 50

class PlayerEditSession(val player: Player) {

    var pos1: Pos? = null
    var pos2: Pos? = null
    var clipboard: Schematic? = null
    var clipboardOrigin: Pos? = null
    var brushBinding: BrushBinding? = null

    private val history = ArrayDeque<ChangeSet>()
    private val redoHistory = ArrayDeque<ChangeSet>()

    fun selection(): CuboidSelection? {
        val p1 = pos1 ?: return null
        val p2 = pos2 ?: return null
        return CuboidSelection(
            minOf(p1.blockX(), p2.blockX()), minOf(p1.blockY(), p2.blockY()), minOf(p1.blockZ(), p2.blockZ()),
            maxOf(p1.blockX(), p2.blockX()), maxOf(p1.blockY(), p2.blockY()), maxOf(p1.blockZ(), p2.blockZ()),
        )
    }

    fun pushHistory(changeSet: ChangeSet) {
        history.addLast(changeSet)
        redoHistory.clear()
        while (history.size > MAX_HISTORY) history.removeFirst()
    }

    fun undo(instance: Instance): Boolean {
        val cs = history.removeLastOrNull() ?: return false
        cs.undo(instance)
        redoHistory.addLast(cs)
        return true
    }

    fun redo(instance: Instance): Boolean {
        val cs = redoHistory.removeLastOrNull() ?: return false
        cs.redo(instance)
        history.addLast(cs)
        return true
    }

    fun historySize(): Int = history.size
    fun clearHistory() { history.clear(); redoHistory.clear() }
}

data class CuboidSelection(
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int,
) {
    val width: Int get() = maxX - minX + 1
    val height: Int get() = maxY - minY + 1
    val length: Int get() = maxZ - minZ + 1
    val volume: Long get() = width.toLong() * height * length
}

object EditSessionManager {

    private val sessions = ConcurrentHashMap<UUID, PlayerEditSession>()

    fun get(player: Player): PlayerEditSession =
        sessions.getOrPut(player.uuid) { PlayerEditSession(player) }

    fun remove(player: Player) {
        sessions.remove(player.uuid)
    }

    fun clear() {
        sessions.clear()
    }
}
