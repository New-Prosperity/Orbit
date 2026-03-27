package me.nebula.orbit.utils.rewards

import me.nebula.orbit.utils.actionbar.ActionBarManager
import me.nebula.orbit.utils.counter.AnimatedCounterManager
import me.nebula.orbit.utils.counter.Easing
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import me.nebula.orbit.utils.chat.miniMessage
import net.minestom.server.entity.Player
import net.minestom.server.sound.SoundEvent

data class SummaryLine(
    val icon: String,
    val label: String,
    val amount: Long,
    val previousAmount: Long = 0,
    val color: NamedTextColor = NamedTextColor.WHITE,
    val animate: Boolean = true,
)

class RewardSummaryBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val lines = mutableListOf<SummaryLine>()
    @PublishedApi internal var header: String = "<dark_gray><st>                              "
    @PublishedApi internal var durationTicks: Int = 50
    @PublishedApi internal var sound: SoundEvent? = SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP
    @PublishedApi internal var actionBarSlot: String? = null
    @PublishedApi internal var actionBarPriority: Int = 0

    fun xp(gained: Long, previous: Long = 0) {
        lines += SummaryLine("\u2B50", "XP", gained, previous, NamedTextColor.AQUA)
    }

    fun coins(gained: Long, previous: Long = 0) {
        lines += SummaryLine("\u26C1", "Coins", gained, previous, NamedTextColor.GOLD)
    }

    fun kills(count: Long) {
        lines += SummaryLine("\u2694", "Kills", count, color = NamedTextColor.RED, animate = false)
    }

    fun wins(count: Long) {
        lines += SummaryLine("\u2605", "Wins", count, color = NamedTextColor.GREEN, animate = false)
    }

    fun line(icon: String, label: String, amount: Long, previous: Long = 0, color: NamedTextColor = NamedTextColor.WHITE, animate: Boolean = true) {
        lines += SummaryLine(icon, label, amount, previous, color, animate)
    }

    fun header(text: String) { header = text }
    fun duration(ticks: Int) { durationTicks = ticks }
    fun sound(event: SoundEvent?) { sound = event }

    fun animateOnActionBar(slotId: String, priority: Int = 0) {
        actionBarSlot = slotId
        actionBarPriority = priority
    }
}

fun Player.showRewardSummary(block: RewardSummaryBuilder.() -> Unit) {
    val builder = RewardSummaryBuilder().apply(block)

    val chatParts = builder.lines.map { entry ->
        val display = if (entry.animate) "+${entry.amount}" else "${entry.amount}"
        miniMessage.deserialize("<${entry.color.toString().lowercase()}>${entry.icon} $display ${entry.label}")
    }

    val headerLine = miniMessage.deserialize(builder.header)
    val rewardLine = Component.join(
        JoinConfiguration.separator(miniMessage.deserialize(" <dark_gray>\u2502 ")),
        chatParts,
    )

    sendMessage(headerLine)
    sendMessage(rewardLine)
    sendMessage(headerLine)

    builder.sound?.let { event ->
        playSound(Sound.sound(event.key(), Sound.Source.MASTER, 1f, 1f))
    }

    val slotId = builder.actionBarSlot ?: return
    val animatable = builder.lines.firstOrNull { it.animate && it.amount != 0L } ?: return

    AnimatedCounterManager.start(
        player = this,
        id = "reward_summary",
        from = animatable.previousAmount,
        to = animatable.previousAmount + animatable.amount,
        durationTicks = builder.durationTicks,
        easing = Easing.EASE_OUT_CUBIC,
        onTick = { value ->
            val parts = builder.lines.map { e ->
                val v = if (e === animatable) value else if (e.animate) e.previousAmount + e.amount else e.amount
                miniMessage.deserialize("<${e.color.toString().lowercase()}>${e.icon} $v")
            }
            ActionBarManager.set(
                this, slotId, builder.actionBarPriority,
                Component.join(JoinConfiguration.separator(miniMessage.deserialize(" <dark_gray>\u2502 ")), parts),
                durationMs = (builder.durationTicks * 50 + 2000).toLong(),
            )
        },
    )
}
