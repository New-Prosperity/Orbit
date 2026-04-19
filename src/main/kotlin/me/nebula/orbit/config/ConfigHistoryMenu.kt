package me.nebula.orbit.config

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.config.ConfigEntry
import me.nebula.gravity.config.ConfigRevision
import me.nebula.gravity.config.ConfigRevisionStore
import me.nebula.gravity.config.ConfigStore
import me.nebula.gravity.config.storageKey
import me.nebula.gravity.config.storageKeyOrNull
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object ConfigHistoryMenu {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
        .withZone(ZoneOffset.UTC)

    fun openForEntry(player: Player, entry: ConfigEntry<*>, qualifier: Any? = null) {
        val storageKey = (entry.storageKeyOrNull(qualifier) ?: entry.key)
        val revisions = runCatching { ConfigRevisionStore.forStorageKey(storageKey, limit = 100) }.getOrDefault(emptyList())
        renderPage(player, entry, qualifier, storageKey, revisions)
    }

    fun openGlobal(player: Player) {
        val revisions = runCatching { ConfigRevisionStore.recent(200) }.getOrDefault(emptyList())
        val title = player.translateRaw("orbit.config.history.title".asTranslationKey())
        val paginated = paginatedGui(title, rows = 6) {
            borderDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            key("config-history-global")

            items<ConfigRevision>(revisions, transform = ::revisionItem) { p, rev ->
                p.sendMessage(Component.text("Revision ${rev.revisionId} — ${rev.entryKey}"))
            }

            staticSlot(49, itemStack(Material.ARROW) { name("<gray>Back"); clean() }) { p ->
                ConfigMainMenu.open(p)
            }
        }
        paginated.openForPlayer(player)
    }

    private fun renderPage(
        player: Player,
        entry: ConfigEntry<*>,
        qualifier: Any?,
        storageKey: String,
        revisions: List<ConfigRevision>,
    ) {
        val title = player.translateRaw(
            "orbit.config.history.entry_title".asTranslationKey(),
            "key" to entry.key,
        )
        val paginated = paginatedGui(title, rows = 6) {
            borderDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            key("config-history-$storageKey")

            items<ConfigRevision>(revisions, transform = ::revisionItem) { p, rev ->
                @Suppress("UNCHECKED_CAST")
                val typed = entry as ConfigEntry<Any?>
                ConfigStore.revert(rev, typed, actor = p.uuid, actorName = p.username, reason = "GUI revert")
                openForEntry(p, entry, qualifier)
            }

            staticSlot(49, itemStack(Material.ARROW) { name("<gray>Back"); clean() }) { p ->
                ConfigScopeMenu.open(p, entry.scope, qualifier)
            }
        }
        paginated.openForPlayer(player)
    }

    private fun revisionItem(rev: ConfigRevision): ItemStack {
        val timestamp = timeFormatter.format(Instant.ofEpochMilli(rev.timestamp))
        val icon = when {
            rev.isRollback -> Material.WRITABLE_BOOK
            rev.isCreation -> Material.WRITABLE_BOOK
            rev.isDeletion -> Material.BARRIER
            else -> Material.PAPER
        }
        return itemStack(icon) {
            name("<gold>#${rev.revisionId} <dark_gray>· <gray>$timestamp")
            lore("<gray>Entry: <white>${rev.entryKey}")
            rev.actorName?.let { lore("<gray>Actor: <yellow>$it") }
            rev.reason?.let { lore("<gray>Reason: <white>$it") }
            if (rev.previousRaw != null) lore("<gray>From: <red>${rev.previousRaw!!.take(64)}")
            if (rev.newRaw != null) lore("<gray>To: <green>${rev.newRaw!!.take(64)}")
            if (rev.isRollback) lore("<light_purple>↩ Rollback")
            emptyLoreLine()
            lore("<yellow>Click to rollback")
            clean()
        }
    }
}
