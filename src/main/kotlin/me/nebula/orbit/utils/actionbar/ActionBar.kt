package me.nebula.orbit.utils.actionbar

import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.entity.Player
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val miniMessage = MiniMessage.miniMessage()
private val activeBars = ConcurrentHashMap<UUID, ActionBarEntry>()

private data class ActionBarEntry(val message: String, val expiresAt: Long)

fun Player.showActionBar(message: String, durationMs: Long = 3000) {
    activeBars[uuid] = ActionBarEntry(message, System.currentTimeMillis() + durationMs)
    sendActionBar(miniMessage.deserialize(message))

    scheduler().buildTask {
        val entry = activeBars[uuid] ?: return@buildTask
        if (System.currentTimeMillis() >= entry.expiresAt) {
            activeBars.remove(uuid)
        } else {
            sendActionBar(miniMessage.deserialize(entry.message))
        }
    }.repeat(TaskSchedule.tick(20)).schedule()
}

fun Player.clearActionBar() {
    activeBars.remove(uuid)
    sendActionBar(net.kyori.adventure.text.Component.empty())
}

fun Player.hasActionBar(): Boolean = activeBars.containsKey(uuid)
