package me.nebula.orbit.utils.combo

import me.nebula.orbit.translation.translateDefault
import me.nebula.orbit.utils.actionbar.ActionBarManager
import me.nebula.orbit.utils.scheduler.repeat
import me.nebula.orbit.utils.vanish.VanishManager
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.timer.Task
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import me.nebula.gravity.translation.Keys

enum class ComboDisplay {
    ACTION_BAR,
    TITLE,
    BOSS_BAR,
    NONE,
}

data class ComboThreshold(
    val count: Int,
    val handler: (Player) -> Unit,
)

data class ComboConfig(
    val windowTicks: Int,
    val display: ComboDisplay,
    val thresholds: List<ComboThreshold>,
    val onDrop: ((Player, Int) -> Unit)?,
    val multiplier: (Int) -> Double,
)

data class ComboState(
    var count: Int = 0,
    var lastHitTick: Long = 0L,
    var bossBar: BossBar? = null,
)

object ComboManager {

    private val combos = ConcurrentHashMap<UUID, ComboState>()
    @Volatile private var config: ComboConfig? = null
    @Volatile private var eventNode: EventNode<*>? = null
    @Volatile private var tickTask: Task? = null
    @Volatile private var tickCounter: Long = 0L

    fun onHit(attacker: Player) {
        val cfg = config ?: return
        val state = combos.getOrPut(attacker.uuid) { ComboState() }
        state.count++
        state.lastHitTick = tickCounter

        cfg.thresholds
            .filter { it.count == state.count }
            .forEach { it.handler(attacker) }

        updateDisplay(attacker, state, cfg)
    }

    fun getCombo(player: Player): Int =
        combos[player.uuid]?.count ?: 0

    fun getMultiplier(player: Player): Double {
        val cfg = config ?: return 1.0
        val count = combos[player.uuid]?.count ?: 0
        return if (count == 0) 1.0 else cfg.multiplier(count)
    }

    fun install(comboConfig: ComboConfig) {
        config = comboConfig

        val node = EventNode.all("combo-counter")
        node.addListener(EntityDamageEvent::class.java) { event ->
            val damage = event.damage as? EntityDamage ?: return@addListener
            val attacker = damage.source as? Player ?: return@addListener
            val target = event.entity as? Player ?: return@addListener
            if (event.isCancelled) return@addListener
            if (VanishManager.isVanished(attacker) || VanishManager.isVanished(target)) return@addListener
            onHit(attacker)
        }

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            val state = combos.remove(event.player.uuid) ?: return@addListener
            clearDisplay(event.player, state)
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node

        tickTask = repeat(1) { tick() }
    }

    fun uninstall() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        tickTask?.cancel()
        tickTask = null
        combos.values.forEach { state ->
            state.bossBar?.let { bar ->
                MinecraftServer.getConnectionManager().onlinePlayers.forEach { p -> p.hideBossBar(bar) }
            }
        }
        combos.clear()
        config = null
        tickCounter = 0L
    }

    private fun tick() {
        tickCounter++
        val cfg = config ?: return

        combos.entries.removeIf { (uuid, state) ->
            if (tickCounter - state.lastHitTick >= cfg.windowTicks && state.count > 0) {
                val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
                if (player != null) {
                    cfg.onDrop?.invoke(player, state.count)
                    clearDisplay(player, state)
                }
                true
            } else {
                false
            }
        }
    }

    private fun updateDisplay(player: Player, state: ComboState, cfg: ComboConfig) {
        when (cfg.display) {
            ComboDisplay.ACTION_BAR -> {
                ActionBarManager.set(player, "combo", 10, translateDefault(Keys.Orbit.Combo.Counter, "count" to state.count.toString()), 2000L)
            }
            ComboDisplay.TITLE -> {
                val title = Title.title(
                    translateDefault(Keys.Orbit.Combo.TitleTop, "count" to state.count.toString()),
                    translateDefault(Keys.Orbit.Combo.TitleBottom),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(200)),
                )
                player.showTitle(title)
            }
            ComboDisplay.BOSS_BAR -> {
                val comboText = translateDefault(Keys.Orbit.Combo.Counter, "count" to state.count.toString())
                val bar = state.bossBar ?: BossBar.bossBar(
                    comboText,
                    1f,
                    BossBar.Color.YELLOW,
                    BossBar.Overlay.PROGRESS,
                ).also {
                    state.bossBar = it
                    player.showBossBar(it)
                }
                bar.name(comboText)
                val elapsed = tickCounter - state.lastHitTick
                val progress = (1f - elapsed.toFloat() / cfg.windowTicks).coerceIn(0f, 1f)
                bar.progress(progress)
            }
            ComboDisplay.NONE -> {}
        }
    }

    private fun clearDisplay(player: Player, state: ComboState) {
        val cfg = config ?: return
        when (cfg.display) {
            ComboDisplay.ACTION_BAR -> ActionBarManager.remove(player, "combo")
            ComboDisplay.TITLE -> player.clearTitle()
            ComboDisplay.BOSS_BAR -> state.bossBar?.let { player.hideBossBar(it) }
            ComboDisplay.NONE -> {}
        }
    }
}

class ComboCounterBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var windowTicks: Int = 40
    @PublishedApi internal var display: ComboDisplay = ComboDisplay.NONE
    @PublishedApi internal val thresholds: MutableList<ComboThreshold> = mutableListOf()
    @PublishedApi internal var onDropHandler: ((Player, Int) -> Unit)? = null
    @PublishedApi internal var multiplierFunc: (Int) -> Double = { 1.0 }

    fun windowTicks(ticks: Int) { windowTicks = ticks }
    fun display(display: ComboDisplay) { this.display = display }

    fun onCombo(count: Int, handler: (Player) -> Unit) {
        thresholds += ComboThreshold(count, handler)
    }

    fun onDrop(handler: (Player, Int) -> Unit) { onDropHandler = handler }
    fun multiplier(func: (Int) -> Double) { multiplierFunc = func }

    @PublishedApi internal fun build(): ComboConfig = ComboConfig(
        windowTicks = windowTicks,
        display = display,
        thresholds = thresholds.toList(),
        onDrop = onDropHandler,
        multiplier = multiplierFunc,
    )
}

inline fun comboCounter(block: ComboCounterBuilder.() -> Unit): ComboConfig =
    ComboCounterBuilder().apply(block).build()

fun ComboConfig.install() = ComboManager.install(this)
fun ComboConfig.uninstall() = ComboManager.uninstall()

val Player.combo: Int get() = ComboManager.getCombo(this)
val Player.comboMultiplier: Double get() = ComboManager.getMultiplier(this)
