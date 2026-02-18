package me.nebula.orbit.utils.coinflip

import me.nebula.orbit.utils.chat.mm
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.TaskSchedule
import kotlin.random.Random

class CoinFlipResult @PublishedApi internal constructor(
    private val onHeadsHandler: (() -> Unit)?,
    private val onTailsHandler: (() -> Unit)?,
    private val announceInstance: Instance?,
    private val animationTicks: Int,
) {

    fun execute() {
        if (announceInstance == null && animationTicks <= 0) {
            resolveImmediate()
            return
        }
        runAnimated()
    }

    private fun resolveImmediate() {
        if (Random.nextBoolean()) onHeadsHandler?.invoke() else onTailsHandler?.invoke()
    }

    private fun runAnimated() {
        val steps = (animationTicks / 4).coerceAtLeast(3)
        var step = 0
        val taskHolder = arrayOfNulls<net.minestom.server.timer.Task>(1)

        taskHolder[0] = MinecraftServer.getSchedulerManager()
            .buildTask {
                step++
                if (step < steps) {
                    val display = if (Random.nextBoolean()) "<yellow>Heads..." else "<yellow>Tails..."
                    announceInstance?.sendMessage(mm(display))
                    announceInstance?.playSound(
                        Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_HAT.key(), Sound.Source.MASTER, 0.8f, 1f + step * 0.05f),
                        0.0, 0.0, 0.0,
                    )
                } else {
                    taskHolder[0]?.cancel()
                    val isHeads = Random.nextBoolean()
                    val resultText = if (isHeads) "<gold><bold>HEADS!" else "<gold><bold>TAILS!"
                    announceInstance?.sendMessage(mm(resultText))
                    announceInstance?.playSound(
                        Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP.key(), Sound.Source.MASTER, 1f, 1.5f),
                        0.0, 0.0, 0.0,
                    )
                    if (isHeads) onHeadsHandler?.invoke() else onTailsHandler?.invoke()
                }
            }
            .repeat(TaskSchedule.tick(4))
            .schedule()
    }
}

class CoinFlipBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var onHeadsHandler: (() -> Unit)? = null
    @PublishedApi internal var onTailsHandler: (() -> Unit)? = null
    @PublishedApi internal var announceInstance: Instance? = null
    @PublishedApi internal var animationTicks: Int = 40

    fun onHeads(handler: () -> Unit) { onHeadsHandler = handler }
    fun onTails(handler: () -> Unit) { onTailsHandler = handler }
    fun announce(instance: Instance) { announceInstance = instance }
    fun animationTicks(ticks: Int) { animationTicks = ticks }

    @PublishedApi internal fun build(): CoinFlipResult =
        CoinFlipResult(onHeadsHandler, onTailsHandler, announceInstance, animationTicks)
}

inline fun coinFlip(block: CoinFlipBuilder.() -> Unit): CoinFlipResult =
    CoinFlipBuilder().apply(block).build()

class DiceRollResult @PublishedApi internal constructor(
    private val sides: Int,
    private val onResultHandler: ((Int) -> Unit)?,
    private val announceInstance: Instance?,
    private val animationTicks: Int,
) {

    fun execute() {
        if (announceInstance == null && animationTicks <= 0) {
            onResultHandler?.invoke(Random.nextInt(1, sides + 1))
            return
        }
        runAnimated()
    }

    private fun runAnimated() {
        val steps = (animationTicks / 4).coerceAtLeast(3)
        var step = 0
        val taskHolder = arrayOfNulls<net.minestom.server.timer.Task>(1)

        taskHolder[0] = MinecraftServer.getSchedulerManager()
            .buildTask {
                step++
                if (step < steps) {
                    val value = Random.nextInt(1, sides + 1)
                    announceInstance?.sendMessage(mm("<yellow>Rolling... <white>$value"))
                    announceInstance?.playSound(
                        Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_HAT.key(), Sound.Source.MASTER, 0.8f, 1f + step * 0.05f),
                        0.0, 0.0, 0.0,
                    )
                } else {
                    taskHolder[0]?.cancel()
                    val result = Random.nextInt(1, sides + 1)
                    announceInstance?.sendMessage(mm("<gold><bold>Rolled: $result"))
                    announceInstance?.playSound(
                        Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP.key(), Sound.Source.MASTER, 1f, 1.5f),
                        0.0, 0.0, 0.0,
                    )
                    onResultHandler?.invoke(result)
                }
            }
            .repeat(TaskSchedule.tick(4))
            .schedule()
    }
}

class DiceRollBuilder @PublishedApi internal constructor(private val sides: Int) {

    @PublishedApi internal var onResultHandler: ((Int) -> Unit)? = null
    @PublishedApi internal var announceInstance: Instance? = null
    @PublishedApi internal var animationTicks: Int = 40

    fun onResult(handler: (Int) -> Unit) { onResultHandler = handler }
    fun announce(instance: Instance) { announceInstance = instance }
    fun animationTicks(ticks: Int) { animationTicks = ticks }

    @PublishedApi internal fun build(): DiceRollResult =
        DiceRollResult(sides, onResultHandler, announceInstance, animationTicks)
}

inline fun diceRoll(sides: Int = 6, block: DiceRollBuilder.() -> Unit): DiceRollResult =
    DiceRollBuilder(sides).apply(block).build()

class WeightedOption<T>(val value: T, val weight: Double)

class WeightedRandomBuilder<T> @PublishedApi internal constructor() {

    @PublishedApi internal val options = mutableListOf<WeightedOption<T>>()

    fun option(value: T, weight: Double = 1.0) {
        require(weight > 0.0) { "Weight must be positive" }
        options.add(WeightedOption(value, weight))
    }

    @PublishedApi internal fun build(): WeightedRandom<T> {
        require(options.isNotEmpty()) { "At least one option required" }
        return WeightedRandom(options.toList())
    }
}

class WeightedRandom<T>(private val options: List<WeightedOption<T>>) {

    private val totalWeight = options.sumOf { it.weight }

    fun roll(): T {
        var remaining = Random.nextDouble() * totalWeight
        for (option in options) {
            remaining -= option.weight
            if (remaining <= 0.0) return option.value
        }
        return options.last().value
    }

    fun rollMultiple(count: Int): List<T> = (1..count).map { roll() }
}

inline fun <T> weightedRandom(block: WeightedRandomBuilder<T>.() -> Unit): WeightedRandom<T> =
    WeightedRandomBuilder<T>().apply(block).build()
