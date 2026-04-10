package me.nebula.orbit.utils.counter

import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.math.roundToLong

enum class Easing {
    LINEAR,
    EASE_OUT_QUAD,
    EASE_OUT_CUBIC,
    EASE_OUT_QUART,
    EASE_IN_QUAD,
    EASE_IN_OUT_QUAD;

    fun apply(t: Double): Double = when (this) {
        LINEAR -> t
        EASE_OUT_QUAD -> 1.0 - (1.0 - t).pow(2)
        EASE_OUT_CUBIC -> 1.0 - (1.0 - t).pow(3)
        EASE_OUT_QUART -> 1.0 - (1.0 - t).pow(4)
        EASE_IN_QUAD -> t.pow(2)
        EASE_IN_OUT_QUAD -> if (t < 0.5) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2
    }
}

class CounterAnimation(
    val id: String,
    val from: Long,
    val to: Long,
    val durationTicks: Int,
    val easing: Easing,
    val onTick: (Long) -> Unit,
    val onComplete: (() -> Unit)?,
) {
    var elapsed: Int = 0

    fun current(): Long {
        val t = (elapsed.toDouble() / durationTicks).coerceIn(0.0, 1.0)
        val eased = easing.apply(t)
        return from + ((to - from) * eased).roundToLong()
    }

    fun isDone(): Boolean = elapsed >= durationTicks
}

object AnimatedCounterManager {

    private val animations = ConcurrentHashMap<UUID, ConcurrentHashMap<String, CounterAnimation>>()
    private var tickTask: Task? = null

    fun start(
        player: Player,
        id: String,
        from: Long,
        to: Long,
        durationTicks: Int = 50,
        easing: Easing = Easing.EASE_OUT_CUBIC,
        onTick: (Long) -> Unit,
        onComplete: (() -> Unit)? = null,
    ) {
        val map = animations.getOrPut(player.uuid) { ConcurrentHashMap() }
        map[id] = CounterAnimation(id, from, to, durationTicks, easing, onTick, onComplete)
        onTick(from)
    }

    fun stop(player: Player, id: String) {
        animations[player.uuid]?.remove(id)
    }

    fun stopAll(player: Player) {
        animations.remove(player.uuid)
    }

    fun isRunning(player: Player, id: String): Boolean =
        animations[player.uuid]?.containsKey(id) == true

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
            animations.remove(event.player.uuid)
        }
        tickTask = repeat(1) { tick() }
    }

    fun uninstall() {
        tickTask?.cancel()
        tickTask = null
        animations.clear()
    }

    private fun tick() {
        val toClean = mutableListOf<UUID>()
        for ((uuid, map) in animations) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            if (player == null) { toClean += uuid; continue }

            val done = mutableListOf<String>()
            for ((id, anim) in map) {
                anim.elapsed++
                anim.onTick(anim.current())
                if (anim.isDone()) {
                    anim.onComplete?.invoke()
                    done += id
                }
            }
            done.forEach { map.remove(it) }
            if (map.isEmpty()) toClean += uuid
        }
        toClean.forEach { animations.remove(it) }
    }
}

fun Player.animateCounter(
    id: String,
    from: Long,
    to: Long,
    durationTicks: Int = 50,
    easing: Easing = Easing.EASE_OUT_CUBIC,
    onComplete: (() -> Unit)? = null,
    onTick: (Long) -> Unit,
) = AnimatedCounterManager.start(this, id, from, to, durationTicks, easing, onTick, onComplete)

fun Player.animateCounter(
    id: String,
    from: Int,
    to: Int,
    durationTicks: Int = 50,
    easing: Easing = Easing.EASE_OUT_CUBIC,
    onComplete: (() -> Unit)? = null,
    onTick: (Long) -> Unit,
) = AnimatedCounterManager.start(this, id, from.toLong(), to.toLong(), durationTicks, easing, onTick, onComplete)

fun Player.stopCounter(id: String) = AnimatedCounterManager.stop(this, id)

fun Player.stopAllCounters() = AnimatedCounterManager.stopAll(this)
