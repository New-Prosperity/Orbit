package me.nebula.orbit.utils.anticheat.checks

import me.nebula.gravity.config.ConfigStore
import me.nebula.gravity.config.NetworkConfig
import me.nebula.orbit.utils.anticheat.AntiCheat
import me.nebula.orbit.utils.anticheat.AntiCheatCheck
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ClickPatternCheck : AntiCheatCheck {

    override val id: String = "click_pattern"

    private const val SAMPLE_SIZE = 20
    private const val MIN_STDDEV_MS = 8.0
    private const val WEIGHT = 3

    private class Buffer {
        val intervals = ArrayDeque<Long>()
        var lastTime = 0L
    }

    private val buffers = ConcurrentHashMap<UUID, Buffer>()

    override fun install(node: EventNode<in Event>) {
        node.addListener(EntityAttackEvent::class.java) { event ->
            if (!ConfigStore.get(NetworkConfig.AC_CHECK_CLICK_PATTERN_ENABLED)) return@addListener
            val player = event.entity as? Player ?: return@addListener
            if (player.gameMode == GameMode.CREATIVE) return@addListener

            val now = System.currentTimeMillis()
            var flagged = false
            buffers.compute(player.uuid) { _, existing ->
                val buf = existing ?: Buffer()
                if (buf.lastTime > 0) {
                    val interval = now - buf.lastTime
                    if (interval in 10..500) {
                        buf.intervals.addLast(interval)
                        while (buf.intervals.size > SAMPLE_SIZE) buf.intervals.removeFirst()
                    }
                }
                buf.lastTime = now

                if (buf.intervals.size >= SAMPLE_SIZE) {
                    val mean = buf.intervals.average()
                    val variance = buf.intervals.sumOf { (it - mean) * (it - mean) } / buf.intervals.size
                    val stddev = kotlin.math.sqrt(variance)
                    if (stddev < MIN_STDDEV_MS) {
                        flagged = true
                        buf.intervals.clear()
                    }
                }
                buf
            }
            if (flagged) {
                AntiCheat.flag(
                    player.uuid, "click_pattern", WEIGHT,
                    AntiCheat.combatFlagThreshold, AntiCheat.combatKickThreshold,
                )
            }
        }
    }

    override fun cleanup(uuid: UUID) {
        buffers.remove(uuid)
    }

    override fun clearAll() {
        buffers.clear()
    }
}
