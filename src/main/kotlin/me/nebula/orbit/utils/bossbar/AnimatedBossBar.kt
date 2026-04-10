package me.nebula.orbit.utils.bossbar

import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.counter.Easing
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AnimatedBossBarInstance(
    val bar: BossBar,
    val ownerUuid: UUID,
) {
    internal var animation: ProgressAnimation? = null

    fun setProgress(
        target: Float,
        durationTicks: Int = 20,
        easing: Easing = Easing.EASE_OUT_CUBIC,
        onComplete: (() -> Unit)? = null,
    ) {
        val clamped = target.coerceIn(0f, 1f)
        animation = ProgressAnimation(bar.progress(), clamped, durationTicks, easing, 0, onComplete)
    }

    fun setProgressInstant(value: Float) {
        animation = null
        bar.progress(value.coerceIn(0f, 1f))
    }

    fun updateTitle(title: String) {
        bar.name(miniMessage.deserialize(title))
    }

    fun updateTitle(component: Component) {
        bar.name(component)
    }

    fun updateColor(color: BossBar.Color) {
        bar.color(color)
    }
}

internal class ProgressAnimation(
    val from: Float,
    val to: Float,
    val durationTicks: Int,
    val easing: Easing,
    var elapsed: Int,
    val onComplete: (() -> Unit)?,
) {
    fun current(): Float {
        val t = (elapsed.toDouble() / durationTicks).coerceIn(0.0, 1.0)
        val eased = easing.apply(t)
        return (from + (to - from) * eased).toFloat()
    }

    fun isDone(): Boolean = elapsed >= durationTicks
}

object AnimatedBossBarManager {

    private val bars = ConcurrentHashMap<UUID, ConcurrentHashMap<String, AnimatedBossBarInstance>>()
    private var tickTask: Task? = null

    fun create(
        player: Player,
        id: String,
        title: String,
        progress: Float = 1f,
        color: BossBar.Color = BossBar.Color.WHITE,
        overlay: BossBar.Overlay = BossBar.Overlay.PROGRESS,
    ): AnimatedBossBarInstance {
        val map = bars.getOrPut(player.uuid) { ConcurrentHashMap() }
        map[id]?.let { player.hideBossBar(it.bar) }
        val bar = BossBar.bossBar(miniMessage.deserialize(title), progress.coerceIn(0f, 1f), color, overlay)
        val instance = AnimatedBossBarInstance(bar, player.uuid)
        map[id] = instance
        player.showBossBar(bar)
        return instance
    }

    fun get(player: Player, id: String): AnimatedBossBarInstance? =
        bars[player.uuid]?.get(id)

    fun remove(player: Player, id: String) {
        val instance = bars[player.uuid]?.remove(id) ?: return
        player.hideBossBar(instance.bar)
    }

    fun removeAll(player: Player) {
        val map = bars.remove(player.uuid) ?: return
        map.values.forEach { player.hideBossBar(it.bar) }
    }

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
            bars.remove(event.player.uuid)
        }
        tickTask = repeat(1) { tick() }
    }

    fun uninstall() {
        tickTask?.cancel()
        tickTask = null
        bars.clear()
    }

    private fun tick() {
        for ((_, map) in bars) {
            for ((_, instance) in map) {
                val anim = instance.animation ?: continue
                anim.elapsed++
                instance.bar.progress(anim.current().coerceIn(0f, 1f))
                if (anim.isDone()) {
                    instance.animation = null
                    anim.onComplete?.invoke()
                }
            }
        }
    }
}

fun Player.animatedBossBar(
    id: String,
    title: String,
    progress: Float = 1f,
    color: BossBar.Color = BossBar.Color.WHITE,
    overlay: BossBar.Overlay = BossBar.Overlay.PROGRESS,
): AnimatedBossBarInstance = AnimatedBossBarManager.create(this, id, title, progress, color, overlay)

fun Player.animatedBossBar(id: String): AnimatedBossBarInstance? = AnimatedBossBarManager.get(this, id)

fun Player.removeAnimatedBossBar(id: String) = AnimatedBossBarManager.remove(this, id)

fun Player.removeAllAnimatedBossBars() = AnimatedBossBarManager.removeAll(this)
