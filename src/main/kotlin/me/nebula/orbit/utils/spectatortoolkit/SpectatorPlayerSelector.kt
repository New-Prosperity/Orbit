package me.nebula.orbit.utils.spectatortoolkit

import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.vanish.VanishManager
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.item.Material
import kotlin.math.roundToInt

internal object SpectatorPlayerSelector {

    fun open(
        spectator: Player,
        candidates: List<Player>,
        statsLookup: ((Player) -> SpectatorTargetStats?)?,
        onSelect: (Player, Player) -> Unit,
    ) {
        val visible = candidates.filter { VanishManager.canSee(spectator, it) && it.uuid != spectator.uuid }
        if (visible.isEmpty()) {
            spectator.sendActionBar(spectator.translate("orbit.spectator.no_targets"))
            return
        }

        val sorted = visible.sortedBy { it.position.distanceSquared(spectator.position) }
        val rows = ((sorted.size + 8) / 9).coerceIn(1, 6)
        val capacity = rows * 9

        val title = spectator.translateRaw("orbit.spectator.selector_title")
        gui(title, rows) {
            sorted.take(capacity).forEachIndexed { index, target ->
                slot(index, buildHead(spectator, target, statsLookup)) { player ->
                    player.closeInventory()
                    onSelect(player, target)
                }
            }
        }.open(spectator)
    }

    private fun buildHead(
        viewer: Player,
        target: Player,
        statsLookup: ((Player) -> SpectatorTargetStats?)?,
    ) = itemStack(Material.PLAYER_HEAD) {
        name(viewer.translateRaw("orbit.spectator.lore.name", "name" to target.username))

        val maxHealth = target.getAttributeValue(Attribute.MAX_HEALTH).toFloat().coerceAtLeast(1f)
        val healthHearts = ((target.health / maxHealth) * 10f).roundToInt().coerceIn(0, 10)
        val healthBar = "<red>" + "❤".repeat(healthHearts) + "<dark_gray>" + "❤".repeat(10 - healthHearts)
        lore(viewer.translateRaw("orbit.spectator.lore.health", "bar" to healthBar))

        val armor = target.getAttributeValue(Attribute.ARMOR).toFloat().coerceAtLeast(0f)
        val armorIcons = ((armor / 20f) * 10f).roundToInt().coerceIn(0, 10)
        val armorBar = "<aqua>" + "⛨".repeat(armorIcons) + "<dark_gray>" + "⛨".repeat(10 - armorIcons)
        lore(viewer.translateRaw("orbit.spectator.lore.armor", "bar" to armorBar))

        val stats = statsLookup?.invoke(target)
        if (stats != null) {
            lore(viewer.translateRaw("orbit.spectator.lore.kills", "kills" to stats.kills.toString()))
            stats.team?.let { lore(viewer.translateRaw("orbit.spectator.lore.team", "team" to it)) }
            stats.kit?.let { lore(viewer.translateRaw("orbit.spectator.lore.kit", "kit" to it)) }
        }

        val distance = viewer.position.distance(target.position).roundToInt()
        lore(viewer.translateRaw("orbit.spectator.lore.distance", "distance" to distance.toString()))

        lore(viewer.translateRaw("orbit.spectator.lore.click_to_watch"))
        clean()
    }
}
