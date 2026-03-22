package me.nebula.orbit.utils.spectatortoolkit

import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.hotbar.Hotbar
import me.nebula.orbit.utils.hotbar.hotbar
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SpectatorToolkit @PublishedApi internal constructor(
    private val nextTarget: (Player) -> Player?,
    private val previousTarget: (Player) -> Player?,
    private val alivePlayers: () -> List<Player>,
    private val speedSteps: List<Float>,
    private val onLeave: ((Player) -> Unit)?,
) {

    private val currentSpeedIndex = ConcurrentHashMap<UUID, Int>()
    private var hotbar: Hotbar? = null

    fun install() {
        hotbar = hotbar("spectator-toolkit") {
            clearOtherSlots = true

            slot(0, itemStack(Material.COMPASS) {
                name("<green>Next Player <gray>(Right Click)")
                clean()
            }) { player ->
                val target = nextTarget(player)
                if (target != null) {
                    player.sendActionBar(
                        player.translate("orbit.spectator.watching", "name" to target.username)
                    )
                }
            }

            slot(1, itemStack(Material.CLOCK) {
                name("<red>Previous Player <gray>(Right Click)")
                clean()
            }) { player ->
                val target = previousTarget(player)
                if (target != null) {
                    player.sendActionBar(
                        player.translate("orbit.spectator.watching", "name" to target.username)
                    )
                }
            }

            slot(4, itemStack(Material.PAPER) {
                name("<yellow>Player List <gray>(Right Click)")
                clean()
            }) { player ->
                openPlayerSelector(player)
            }

            slot(7, itemStack(Material.FEATHER) {
                name("<aqua>Speed Toggle <gray>(Right Click)")
                clean()
            }) { player ->
                cycleSpeed(player)
            }

            if (onLeave != null) {
                slot(8, itemStack(Material.BARRIER) {
                    name("<red>Leave <gray>(Right Click)")
                    clean()
                }) { player ->
                    onLeave.invoke(player)
                }
            }
        }
        hotbar!!.install()
    }

    fun uninstall() {
        hotbar?.uninstall()
        hotbar = null
        currentSpeedIndex.clear()
    }

    fun apply(player: Player) {
        hotbar?.apply(player)
    }

    fun remove(player: Player) {
        currentSpeedIndex.remove(player.uuid)
        hotbar?.remove(player)
        player.flyingSpeed = DEFAULT_FLY_SPEED
    }

    private fun cycleSpeed(player: Player) {
        if (speedSteps.isEmpty()) return
        val index = (currentSpeedIndex.getOrDefault(player.uuid, 0) + 1) % speedSteps.size
        currentSpeedIndex[player.uuid] = index
        val speed = speedSteps[index]
        player.flyingSpeed = DEFAULT_FLY_SPEED * speed
        player.sendActionBar(
            player.translate("orbit.spectator.speed", "speed" to "${speed}x")
        )
    }

    private fun openPlayerSelector(spectator: Player) {
        val alive = alivePlayers()
        if (alive.isEmpty()) return

        val rows = ((alive.size + 8) / 9).coerceIn(1, 6)
        gui("<yellow>Players", rows) {
            alive.forEachIndexed { index, target ->
                if (index >= rows * 9) return@forEachIndexed
                slot(index, itemStack(Material.PLAYER_HEAD) {
                    name("<yellow>${target.username}")
                }) { player ->
                    player.closeInventory()
                    player.spectate(target)
                    player.sendActionBar(
                        player.translate("orbit.spectator.watching", "name" to target.username)
                    )
                }
            }
        }.open(spectator)
    }

    private companion object {
        const val DEFAULT_FLY_SPEED = 0.05f
    }
}

class SpectatorToolkitBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var nextTarget: (Player) -> Player? = { null }
    @PublishedApi internal var previousTarget: (Player) -> Player? = { null }
    @PublishedApi internal var alivePlayers: () -> List<Player> = { emptyList() }
    @PublishedApi internal var speedSteps: List<Float> = listOf(1f, 2f, 4f)
    @PublishedApi internal var onLeave: ((Player) -> Unit)? = null

    fun onNext(handler: (Player) -> Player?) { nextTarget = handler }
    fun onPrevious(handler: (Player) -> Player?) { previousTarget = handler }
    fun alivePlayers(provider: () -> List<Player>) { alivePlayers = provider }
    fun speedSteps(vararg steps: Float) { speedSteps = steps.toList() }
    fun onLeave(handler: (Player) -> Unit) { onLeave = handler }

    @PublishedApi internal fun build(): SpectatorToolkit =
        SpectatorToolkit(nextTarget, previousTarget, alivePlayers, speedSteps, onLeave)
}

inline fun spectatorToolkit(block: SpectatorToolkitBuilder.() -> Unit): SpectatorToolkit =
    SpectatorToolkitBuilder().apply(block).build()
