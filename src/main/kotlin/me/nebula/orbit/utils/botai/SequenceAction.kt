package me.nebula.orbit.utils.botai

import net.minestom.server.entity.Player

class Sequence(private val actions: List<BotAction>) : BotAction {
    override val isComplete: Boolean get() = currentIndex >= actions.size
    private var currentIndex = 0

    override fun start(player: Player) {
        if (actions.isNotEmpty()) actions[0].start(player)
    }

    override fun tick(player: Player) {
        if (currentIndex >= actions.size) return
        val current = actions[currentIndex]
        current.tick(player)
        if (current.isComplete) {
            currentIndex++
            if (currentIndex < actions.size) actions[currentIndex].start(player)
        }
    }

    override fun cancel(player: Player) {
        if (currentIndex < actions.size) actions[currentIndex].cancel(player)
    }
}
