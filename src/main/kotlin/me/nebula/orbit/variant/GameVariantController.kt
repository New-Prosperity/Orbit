package me.nebula.orbit.variant

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.PlayerTracker
import me.nebula.orbit.rules.GameRules
import me.nebula.orbit.script.GameContext
import me.nebula.orbit.script.GameTickContext
import me.nebula.orbit.script.ScriptRunner
import me.nebula.orbit.script.ScriptStep
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.scheduler.repeat
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.title.Title
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import java.time.Duration as JDuration
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class GameVariantController(private val owner: GameMode) {

    private val logger = logger("GameVariantController")

    @Volatile var active: GameVariant? = null
        private set

    private var tickTask: Task? = null
    private var tickCount = 0L
    private val runners = mutableListOf<ScriptRunner>()

    private val context = object : GameTickContext {
        override val rules: GameRules get() = owner.rules
        override val tracker: PlayerTracker get() = owner.tracker
        override val instance: Instance get() = owner.gameInstanceOrNull() ?: error("game instance not set")
        override val gameMode: GameMode get() = owner
        override val gameTime: Duration get() = (System.currentTimeMillis() - owner.gameStartTime).milliseconds
        override val tickCount: Long get() = this@GameVariantController.tickCount

        override fun broadcast(translationKey: String, sound: String?) {
            val inst = owner.gameInstanceOrNull() ?: return
            val soundEvent = sound?.let { SoundEvent.fromKey(it) }
            for (p in inst.players) {
                p.sendMessage(p.translate(translationKey))
                soundEvent?.let { ev ->
                    p.playSound(Sound.sound(ev.key(), Sound.Source.MASTER, 1.0f, 1.0f))
                }
            }
        }

        override fun broadcastPlayers(action: (Player) -> Unit) {
            owner.gameInstanceOrNull()?.players?.forEach(action)
        }
    }

    fun install(variant: GameVariant) {
        dispose()
        active = variant
        runners.clear()
        logger.info { "Activating variant '${variant.id}'" }
        for (component in variant.components) {
            runCatching { component.apply(context) }.onFailure {
                logger.warn(it) { "Component ${component::class.simpleName} apply failed for variant '${variant.id}'" }
            }
            if (component is GameComponent.Script) {
                component.runnerFor(context)?.let(runners::add)
            }
        }
        if (runners.isNotEmpty()) {
            tickCount = 0
            tickTask = repeat(1) {
                tickCount++
                for (runner in runners) runner.tick(context)
            }
        }
        announceBanner(variant)
        owner.events.publish(me.nebula.orbit.event.GameEvent.VariantActivated(variant))
    }

    private fun announceBanner(variant: GameVariant) {
        val inst = owner.gameInstanceOrNull() ?: return
        val bannerSound = SoundEvent.UI_TOAST_CHALLENGE_COMPLETE
        for (p in inst.players) {
            val name = p.translate(variant.nameKey)
            val description = p.translate(variant.descriptionKey)
            p.showTitle(Title.title(
                name, description,
                Title.Times.times(
                    JDuration.ofMillis(500),
                    JDuration.ofMillis(3500),
                    JDuration.ofMillis(800),
                ),
            ))
            p.sendMessage(p.translate(
                "orbit.variant.banner",
                "name" to p.translateRaw(variant.nameKey),
                "description" to p.translateRaw(variant.descriptionKey),
            ))
            p.playSound(Sound.sound(bannerSound.key(), Sound.Source.MASTER, 0.8f, 1.2f))
        }
    }

    fun dispose() {
        tickTask?.cancel()
        tickTask = null
        tickCount = 0
        runners.clear()
        val current = active ?: return
        for (component in current.components.reversed()) {
            runCatching { component.dispose(context) }.onFailure {
                logger.warn(it) { "Component ${component::class.simpleName} dispose failed for variant '${current.id}'" }
            }
        }
        active = null
    }

    fun contextSnapshot(): GameContext = context

    fun runAuxiliarySteps(steps: List<ScriptStep>) {
        if (steps.isEmpty()) return
        runners += ScriptRunner(steps)
        ensureTickTaskRunning()
    }

    private fun ensureTickTaskRunning() {
        if (tickTask != null) return
        tickCount = 0
        tickTask = repeat(1) {
            tickCount++
            for (runner in runners) runner.tick(context)
        }
    }
}
