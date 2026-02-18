package me.nebula.orbit.utils.bossbar

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.entity.Player

private val miniMessage = MiniMessage.miniMessage()

class BossBarBuilder @PublishedApi internal constructor(private val title: String) {

    var color: BossBar.Color = BossBar.Color.WHITE
    var overlay: BossBar.Overlay = BossBar.Overlay.PROGRESS
    var progress: Float = 1f

    @PublishedApi internal fun build(): BossBar =
        BossBar.bossBar(miniMessage.deserialize(title), progress.coerceIn(0f, 1f), color, overlay)
}

inline fun bossBar(title: String, block: BossBarBuilder.() -> Unit = {}): BossBar =
    BossBarBuilder(title).apply(block).build()

fun Player.showBossBar(bar: BossBar): Player = apply { showBossBar(bar as net.kyori.adventure.bossbar.BossBar) }
fun Player.hideBossBar(bar: BossBar): Player = apply { hideBossBar(bar as net.kyori.adventure.bossbar.BossBar) }

fun BossBar.updateTitle(title: String): BossBar = name(miniMessage.deserialize(title))
