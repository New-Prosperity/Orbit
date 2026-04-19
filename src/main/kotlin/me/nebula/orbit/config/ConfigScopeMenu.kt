package me.nebula.orbit.config

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.config.ConfigCatalog
import me.nebula.gravity.config.ConfigEntry
import me.nebula.gravity.config.ConfigScope
import me.nebula.gravity.config.ConfigStore
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.ClickContext
import me.nebula.orbit.utils.gui.GuiClickType
import me.nebula.orbit.utils.gui.GuiSlot
import me.nebula.orbit.utils.gui.confirmGui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

object ConfigScopeMenu {

    fun open(player: Player, scope: ConfigScope, qualifier: Any? = null) {
        val entries = ConfigCatalog.byScope(scope).sortedBy { it.key }
        val title = player.translateRaw(
            "orbit.config.scope.title".asTranslationKey(),
            "scope" to scope.name,
        )
        val paginated = paginatedGui(title, rows = 6) {
            borderDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            key("config-scope-${scope.name}")

            entries.forEach { entry ->
                val slot = makeEntrySlot(player, entry, qualifier)
                item(slot)
            }

            staticSlot(49, itemStack(Material.ARROW) {
                name("<gray>${player.translateRaw("orbit.gui.button.back".asTranslationKey())}")
                clean()
            }) { p -> ConfigMainMenu.open(p) }
        }
        paginated.openForPlayer(player)
    }

    private fun makeEntrySlot(player: Player, entry: ConfigEntry<*>, qualifier: Any?): GuiSlot {
        val item = ConfigRenderer.entryItem(
            entry = entry,
            qualifier = qualifier,
            displayName = runCatching { player.translateRaw(entry.displayNameKey) }.getOrDefault(entry.key),
            description = try { player.translateRaw(entry.descriptionKey) } catch (_: Throwable) { null },
        )
        return GuiSlot(
            item = item,
            onClick = { ctx -> handleClick(ctx, entry, qualifier) },
        )
    }

    private fun handleClick(ctx: ClickContext, entry: ConfigEntry<*>, qualifier: Any?) {
        when (ctx.clickType) {
            GuiClickType.LEFT -> ConfigEntryEditMenu.open(ctx.player, entry, qualifier)
            GuiClickType.RIGHT -> ConfigHistoryMenu.openForEntry(ctx.player, entry, qualifier)
            GuiClickType.SHIFT_LEFT, GuiClickType.SHIFT_RIGHT -> promptReset(ctx.player, entry, qualifier)
            else -> Unit
        }
    }

    private fun promptReset(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        val title = player.translateRaw("orbit.config.reset.title".asTranslationKey())
        val message = player.translateRaw(
            "orbit.config.reset.message".asTranslationKey(),
            "entry" to entry.key,
        )
        val confirm = confirmGui(
            title = title,
            message = message,
            onConfirm = { p ->
                @Suppress("UNCHECKED_CAST")
                val typed = entry as ConfigEntry<Any?>
                ConfigStore.reset(typed, qualifier, actor = p.uuid, actorName = p.username, reason = "GUI reset")
                open(p, entry.scope, qualifier)
            },
            onCancel = { p -> open(p, entry.scope, qualifier) },
        )
        player.openGui(confirm)
    }
}
