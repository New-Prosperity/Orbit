package me.nebula.orbit.config

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.config.ConfigCatalog
import me.nebula.gravity.config.ConfigEntry
import me.nebula.gravity.config.ConfigScope
import me.nebula.gravity.config.ConfigStore
import me.nebula.gravity.config.ConfigValueType
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.AnvilInput
import me.nebula.orbit.utils.gui.AnvilResult
import me.nebula.orbit.utils.gui.GuiSlot
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

object PreferencesMenu {

    fun open(player: Player) {
        val playerEntries = ConfigCatalog.byScope(ConfigScope.PLAYER).sortedBy { (it.subCategory ?: "") + it.key }
        val title = player.translateRaw("orbit.preferences.title".asTranslationKey())

        val paginated = paginatedGui(title, rows = 6) {
            borderDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            key("preferences")

            var lastSub: String? = "__none__"
            for (entry in playerEntries) {
                val sub = entry.subCategory ?: "default"
                if (sub != lastSub) {
                    item(GuiSlot(headerItem(sub, playerEntries.count { (it.subCategory ?: "default") == sub })))
                    lastSub = sub
                }
                item(preferenceSlot(player, entry))
            }

            staticSlot(49, itemStack(Material.BARRIER) { name("<red>Close"); clean() }) { p -> p.closeInventory() }
        }
        paginated.openForPlayer(player)
    }

    fun headerItem(subCategory: String, count: Int): ItemStack =
        itemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE) {
            name("<aqua><bold>${subCategory.uppercase()}")
            lore("<gray>$count preferences")
            clean()
        }

    fun preferenceSlot(player: Player, entry: ConfigEntry<*>): GuiSlot {
        val item = preferenceItem(player, entry)
        return GuiSlot(
            item = item,
            onClick = { ctx -> handlePreferenceClick(ctx.player, entry) },
        )
    }

    fun preferenceItem(player: Player, entry: ConfigEntry<*>): ItemStack {
        val current = try { ConfigStore.get(entry.asAny(), player.uuid) } catch (_: Throwable) { null }
        val icon = ConfigRenderer.typeIcon(entry.type)
        return itemStack(icon) {
            val displayName = runCatching { player.translateRaw(entry.displayNameKey) }.getOrDefault(entry.key)
            name("<white>$displayName")
            val desc = try { player.translateRaw(entry.descriptionKey) } catch (_: Throwable) { null }
            if (desc != null) lore("<gray>$desc")
            emptyLoreLine()
            when (entry.type) {
                ConfigValueType.BOOL -> {
                    val b = current as? Boolean ?: false
                    lore(if (b) "<green>ENABLED" else "<red>DISABLED")
                    lore("<yellow>Click to toggle")
                    if (b) glowing()
                }
                ConfigValueType.STRING, ConfigValueType.ENUM -> {
                    lore("<gray>Value: <white>${current ?: "(default)"}")
                    lore("<yellow>Click to edit")
                }
                else -> {
                    lore("<gray>Value: <white>${current ?: "(default)"}")
                    lore("<gray>Read-only in GUI")
                }
            }
            clean()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun ConfigEntry<*>.asAny(): ConfigEntry<Any?> = this as ConfigEntry<Any?>

    fun handlePreferenceClick(player: Player, entry: ConfigEntry<*>) {
        when (entry.type) {
            ConfigValueType.BOOL -> togglePlayerBool(player, entry)
            ConfigValueType.STRING -> editPlayerString(player, entry)
            else -> Unit
        }
    }

    fun togglePlayerBool(player: Player, entry: ConfigEntry<*>) {
        val current = (try { ConfigStore.get(entry.asAny(), player.uuid) } catch (_: Throwable) { null }) as? Boolean ?: false
        val typed = entry.asAny()
        ConfigStore.set(
            typed,
            !current,
            player.uuid,
            actor = player.uuid,
            actorName = player.username,
            reason = "Preferences GUI",
        )
        open(player)
    }

    fun editPlayerString(player: Player, entry: ConfigEntry<*>) {
        val current = runCatching { ConfigStore.get(entry.asAny(), player.uuid) }.getOrDefault(null)?.toString() ?: ""
        AnvilInput.open(
            player = player,
            title = player.translateRaw("orbit.preferences.edit_title".asTranslationKey()),
            default = current,
            maxLength = 64,
        ) { result ->
            when (result) {
                is AnvilResult.Submitted -> {
                    val typed = entry.asAny()
                    ConfigStore.set(
                        typed,
                        result.text,
                        player.uuid,
                        actor = player.uuid,
                        actorName = player.username,
                        reason = "Preferences GUI",
                    )
                    open(player)
                }
                AnvilResult.Cancelled -> open(player)
            }
        }
    }
}
