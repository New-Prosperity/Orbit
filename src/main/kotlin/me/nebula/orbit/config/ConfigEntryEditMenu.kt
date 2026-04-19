package me.nebula.orbit.config

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.config.ConfigEntry
import me.nebula.gravity.config.ConfigStore
import me.nebula.gravity.config.ConfigValueType
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.AnvilResult
import me.nebula.orbit.utils.gui.AnvilInput
import me.nebula.orbit.utils.gui.EnumPicker
import me.nebula.orbit.utils.gui.NumberInput
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import java.util.Locale

object ConfigEntryEditMenu {

    fun open(player: Player, entry: ConfigEntry<*>, qualifier: Any? = null) {
        when {
            entry.deprecated != null -> openDeprecated(player, entry, qualifier)
            entry.secret -> openSecret(player, entry, qualifier)
            entry.tags.contains("computed") || entry.type == ConfigValueType.COMPUTED -> openComputed(player, entry, qualifier)
            else -> dispatchByType(player, entry, qualifier)
        }
    }

    private fun dispatchByType(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        when (entry.type) {
            ConfigValueType.BOOL -> openBool(player, entry, qualifier)
            ConfigValueType.INT -> openIntEdit(player, entry, qualifier)
            ConfigValueType.LONG -> openLongEdit(player, entry, qualifier)
            ConfigValueType.DOUBLE -> openDoubleEdit(player, entry, qualifier)
            ConfigValueType.STRING -> openStringEdit(player, entry, qualifier)
            ConfigValueType.ENUM -> openReadOnly(player, entry, qualifier)
            ConfigValueType.DURATION -> openDurationEdit(player, entry, qualifier)
            ConfigValueType.MATERIAL -> openMaterialEdit(player, entry, qualifier)
            ConfigValueType.LOCALE -> openLocaleEdit(player, entry, qualifier)
            ConfigValueType.WEIGHTED_TABLE,
            ConfigValueType.LIST,
            ConfigValueType.MAP,
            ConfigValueType.COMPOSITE,
            ConfigValueType.REFERENCE,
            ConfigValueType.UUID_LIST,
            ConfigValueType.STAT_KEY,
            ConfigValueType.PERMISSION_NODE,
            ConfigValueType.COLOR,
            ConfigValueType.COMPONENT,
            ConfigValueType.LOCALIZED_STRING -> openReadOnly(player, entry, qualifier)
            ConfigValueType.COMPUTED -> openComputed(player, entry, qualifier)
        }
    }

    private fun <T> currentValue(entry: ConfigEntry<T>, qualifier: Any?): T =
        runCatching { ConfigStore.get(entry, qualifier) }.getOrDefault(entry.default)

    @Suppress("UNCHECKED_CAST")
    private fun <T> setValue(entry: ConfigEntry<T>, qualifier: Any?, value: Any?, player: Player) {
        val typed = entry as ConfigEntry<Any?>
        ConfigStore.set(typed, value, qualifier, actor = player.uuid, actorName = player.username, reason = "GUI edit")
    }

    private fun openBool(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        val current = currentValue(entry, qualifier) as? Boolean ?: false
        val title = player.translateRaw("orbit.config.edit.title".asTranslationKey())
        val menu = gui(title, rows = 3) {
            fillDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            slot(11, itemStack(Material.LIME_DYE) {
                name("<green><bold>TRUE")
                if (current) { lore("<gold>★ current"); glowing() }
                clean()
            }) { p ->
                setValue(entry, qualifier, true, p)
                ConfigScopeMenu.open(p, entry.scope, qualifier)
            }
            slot(15, itemStack(Material.GRAY_DYE) {
                name("<red><bold>FALSE")
                if (!current) { lore("<gold>★ current"); glowing() }
                clean()
            }) { p ->
                setValue(entry, qualifier, false, p)
                ConfigScopeMenu.open(p, entry.scope, qualifier)
            }
            slot(13, ConfigRenderer.entryItem(
                entry = entry,
                qualifier = qualifier,
                displayName = player.translateRaw(entry.displayNameKey),
            ))
            slot(22, itemStack(Material.ARROW) { name("<gray>Back"); clean() }) { p ->
                ConfigScopeMenu.open(p, entry.scope, qualifier)
            }
        }
        player.openGui(menu)
    }

    private fun openIntEdit(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        val current = currentValue(entry, qualifier) as? Int ?: 0
        val title = player.translateRaw("orbit.config.edit.anvil_title".asTranslationKey())
        NumberInput.openInt(
            player = player,
            title = title,
            default = current,
            onCancel = { ConfigScopeMenu.open(player, entry.scope, qualifier) },
        ) { value ->
            setValue(entry, qualifier, value, player)
            ConfigScopeMenu.open(player, entry.scope, qualifier)
        }
    }

    private fun openLongEdit(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        val current = currentValue(entry, qualifier) as? Long ?: 0L
        val title = player.translateRaw("orbit.config.edit.anvil_title".asTranslationKey())
        NumberInput.openLong(
            player = player,
            title = title,
            default = current,
            onCancel = { ConfigScopeMenu.open(player, entry.scope, qualifier) },
        ) { value ->
            setValue(entry, qualifier, value, player)
            ConfigScopeMenu.open(player, entry.scope, qualifier)
        }
    }

    private fun openDoubleEdit(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        val current = currentValue(entry, qualifier) as? Double ?: 0.0
        val title = player.translateRaw("orbit.config.edit.anvil_title".asTranslationKey())
        NumberInput.openDouble(
            player = player,
            title = title,
            default = current,
            onCancel = { ConfigScopeMenu.open(player, entry.scope, qualifier) },
        ) { value ->
            setValue(entry, qualifier, value, player)
            ConfigScopeMenu.open(player, entry.scope, qualifier)
        }
    }

    private fun openStringEdit(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        val current = currentValue(entry, qualifier)?.toString() ?: ""
        val title = player.translateRaw("orbit.config.edit.anvil_title".asTranslationKey())
        AnvilInput.open(
            player = player,
            title = title,
            default = current,
            maxLength = 64,
        ) { result ->
            when (result) {
                is AnvilResult.Submitted -> {
                    setValue(entry, qualifier, result.text, player)
                    ConfigScopeMenu.open(player, entry.scope, qualifier)
                }
                AnvilResult.Cancelled -> ConfigScopeMenu.open(player, entry.scope, qualifier)
            }
        }
    }

    private fun openDurationEdit(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        val presets = listOf(
            "1 minute" to Duration.ofMinutes(1).toMillis(),
            "5 minutes" to Duration.ofMinutes(5).toMillis(),
            "30 minutes" to Duration.ofMinutes(30).toMillis(),
            "1 hour" to Duration.ofHours(1).toMillis(),
            "1 day" to Duration.ofDays(1).toMillis(),
        )
        val current = currentValue(entry, qualifier)
        val title = player.translateRaw("orbit.config.edit.duration_title".asTranslationKey())
        val menu = gui(title, rows = 3) {
            fillDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            presets.forEachIndexed { i, (label, ms) ->
                slot(10 + i, itemStack(Material.CLOCK) {
                    name("<yellow>$label")
                    lore("<gray>${ms}ms")
                    clean()
                }) { p ->
                    setValue(entry, qualifier, ms, p)
                    ConfigScopeMenu.open(p, entry.scope, qualifier)
                }
            }
            slot(15, itemStack(Material.ANVIL) {
                name("<aqua>Custom value (ms)")
                lore("<gray>Current: <white>$current")
                clean()
            }) { p ->
                val currentMs = (current as? Number)?.toLong() ?: 0L
                NumberInput.openLong(
                    player = p,
                    title = "Duration (ms)",
                    default = currentMs,
                    min = 0L,
                    onCancel = { ConfigScopeMenu.open(p, entry.scope, qualifier) },
                ) { ms ->
                    setValue(entry, qualifier, ms, p)
                    ConfigScopeMenu.open(p, entry.scope, qualifier)
                }
            }
            slot(22, itemStack(Material.ARROW) { name("<gray>Back"); clean() }) { p ->
                ConfigScopeMenu.open(p, entry.scope, qualifier)
            }
        }
        player.openGui(menu)
    }

    private fun openMaterialEdit(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        val current = currentValue(entry, qualifier) as? Material
        val title = player.translateRaw("orbit.config.edit.material_title".asTranslationKey())
        val candidates = commonMaterialPalette()
        val paginated = paginatedGui(title, rows = 6) {
            borderDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            key("config-material-${entry.key}")
            items<Material>(candidates, transform = { mat ->
                itemStack(mat) {
                    name("<white>${mat.name()}")
                    if (mat == current) { lore("<gold>★ current"); glowing() } else {
                        lore("<yellow>Click to select")
                    }
                    clean()
                }
            }) { p, mat ->
                setValue(entry, qualifier, mat, p)
                ConfigScopeMenu.open(p, entry.scope, qualifier)
            }
            staticSlot(49, itemStack(Material.ARROW) { name("<gray>Back"); clean() }) { p ->
                ConfigScopeMenu.open(p, entry.scope, qualifier)
            }
        }
        paginated.openForPlayer(player)
    }

    private fun commonMaterialPalette(): List<Material> = listOf(
        Material.STONE, Material.DIRT, Material.GRASS_BLOCK, Material.SAND, Material.GRAVEL,
        Material.OAK_LOG, Material.OAK_PLANKS, Material.GLASS, Material.COBBLESTONE,
        Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.NETHERITE_INGOT,
        Material.REDSTONE, Material.EMERALD, Material.LAPIS_LAZULI, Material.COAL,
        Material.PAPER, Material.BOOK, Material.WRITABLE_BOOK, Material.COMPASS, Material.CLOCK,
        Material.BARRIER, Material.BEACON, Material.ENDER_EYE, Material.NETHER_STAR,
        Material.CHEST, Material.ENDER_CHEST, Material.CRAFTING_TABLE, Material.FURNACE,
    )

    private fun openLocaleEdit(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        val current = currentValue(entry, qualifier) as? Locale
        val locales = listOf("en", "en-US", "en-GB", "de", "fr", "es", "it", "pt", "ru", "zh", "ja", "ko")
            .map { Locale.forLanguageTag(it) }
        EnumPicker.openGeneric(
            player = player,
            title = player.translateRaw("orbit.config.edit.locale_title".asTranslationKey()),
            values = locales,
            current = current,
            label = { it.toLanguageTag() },
            icon = { Material.WRITABLE_BOOK },
        ) { picked ->
            setValue(entry, qualifier, picked, player)
            ConfigScopeMenu.open(player, entry.scope, qualifier)
        }
    }

    private fun openReadOnly(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        val title = player.translateRaw("orbit.config.edit.readonly_title".asTranslationKey())
        val menu = gui(title, rows = 3) {
            fillDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            slot(13, ConfigRenderer.entryItem(
                entry = entry,
                qualifier = qualifier,
                displayName = player.translateRaw(entry.displayNameKey),
            ))
            slot(11, itemStack(Material.PAPER) {
                name("<yellow>Not editable in GUI yet")
                lore("<gray>Type: <white>${entry.type}")
                lore("<gray>Edit via <yellow>/config <white>command instead")
                clean()
            })
            slot(22, itemStack(Material.ARROW) { name("<gray>Back"); clean() }) { p ->
                ConfigScopeMenu.open(p, entry.scope, qualifier)
            }
        }
        player.openGui(menu)
    }

    private fun openDeprecated(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        val title = player.translateRaw("orbit.config.edit.deprecated_title".asTranslationKey())
        val menu = gui(title, rows = 3) {
            fillDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            val banner = ConfigRenderer.deprecationBanner(entry) ?: itemStack(Material.BARRIER) {
                name("<red>DEPRECATED"); clean()
            }
            slot(4, banner)
            slot(13, ConfigRenderer.entryItem(
                entry = entry,
                qualifier = qualifier,
                displayName = player.translateRaw(entry.displayNameKey),
            ))
            slot(22, itemStack(Material.ARROW) { name("<gray>Back"); clean() }) { p ->
                ConfigScopeMenu.open(p, entry.scope, qualifier)
            }
        }
        player.openGui(menu)
    }

    private fun openSecret(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        val title = player.translateRaw("orbit.config.edit.secret_title".asTranslationKey())
        val menu = gui(title, rows = 3) {
            fillDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            slot(13, itemStack(Material.BARRIER) {
                name("<dark_red><bold>SECRET")
                lore("<gray>Value redacted: ***")
                lore("<gray>Edit via <yellow>/config <white>command")
                clean()
            })
            slot(22, itemStack(Material.ARROW) { name("<gray>Back"); clean() }) { p ->
                ConfigScopeMenu.open(p, entry.scope, qualifier)
            }
        }
        player.openGui(menu)
    }

    private fun openComputed(player: Player, entry: ConfigEntry<*>, qualifier: Any?) {
        val value = currentValue(entry, qualifier)
        val title = player.translateRaw("orbit.config.edit.computed_title".asTranslationKey())
        val menu = gui(title, rows = 3) {
            fillDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            slot(13, itemStack(Material.COMPARATOR) {
                name("<aqua><bold>COMPUTED")
                lore("<gray>Result: <yellow>${value}")
                if (entry.tags.isNotEmpty()) {
                    lore("<gray>Tags: <white>${entry.tags.joinToString(", ")}")
                }
                lore("<gray>Dependencies are inferred from formula")
                clean()
            })
            slot(22, itemStack(Material.ARROW) { name("<gray>Back"); clean() }) { p ->
                ConfigScopeMenu.open(p, entry.scope, qualifier)
            }
        }
        player.openGui(menu)
    }
}
