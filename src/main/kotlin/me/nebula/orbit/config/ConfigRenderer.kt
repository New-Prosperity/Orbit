package me.nebula.orbit.config

import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.config.ConfigCatalog
import me.nebula.gravity.config.ConfigEntry
import me.nebula.gravity.config.ConfigScope
import me.nebula.gravity.config.ConfigStore
import me.nebula.gravity.config.ConfigValueType
import me.nebula.gravity.config.storageKey
import me.nebula.gravity.config.storageKeyOrNull
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

object ConfigRenderer {

    fun scopeIcon(scope: ConfigScope): Material = when (scope) {
        ConfigScope.NETWORK -> Material.BEACON
        ConfigScope.GAME_MODE -> Material.DIAMOND_SWORD
        ConfigScope.SERVER -> Material.COMMAND_BLOCK
        ConfigScope.POOL -> Material.WATER_BUCKET
        ConfigScope.PLAYER -> Material.PLAYER_HEAD
        ConfigScope.GATEWAY -> Material.ENDER_EYE
        ConfigScope.FEATURE_FLAG -> Material.LEVER
        ConfigScope.DISCORD_GUILD -> Material.ENCHANTED_BOOK
        ConfigScope.SECRETS -> Material.BARRIER
    }

    fun scopeColor(scope: ConfigScope): String = when (scope) {
        ConfigScope.NETWORK -> "<aqua>"
        ConfigScope.GAME_MODE -> "<gold>"
        ConfigScope.SERVER -> "<green>"
        ConfigScope.POOL -> "<blue>"
        ConfigScope.PLAYER -> "<yellow>"
        ConfigScope.GATEWAY -> "<light_purple>"
        ConfigScope.FEATURE_FLAG -> "<dark_aqua>"
        ConfigScope.DISCORD_GUILD -> "<dark_purple>"
        ConfigScope.SECRETS -> "<dark_red>"
    }

    fun typeIcon(type: ConfigValueType): Material = when (type) {
        ConfigValueType.BOOL -> Material.LEVER
        ConfigValueType.INT -> Material.REDSTONE
        ConfigValueType.LONG -> Material.REDSTONE_BLOCK
        ConfigValueType.DOUBLE -> Material.GLOWSTONE_DUST
        ConfigValueType.STRING -> Material.PAPER
        ConfigValueType.ENUM -> Material.BOOKSHELF
        ConfigValueType.DURATION -> Material.CLOCK
        ConfigValueType.COMPONENT -> Material.WRITTEN_BOOK
        ConfigValueType.COLOR -> Material.ORANGE_DYE
        ConfigValueType.MATERIAL -> Material.GRASS_BLOCK
        ConfigValueType.LOCALE -> Material.WRITABLE_BOOK
        ConfigValueType.UUID_LIST -> Material.PLAYER_HEAD
        ConfigValueType.STAT_KEY -> Material.COMPASS
        ConfigValueType.PERMISSION_NODE -> Material.IRON_DOOR
        ConfigValueType.LOCALIZED_STRING -> Material.OAK_SIGN
        ConfigValueType.WEIGHTED_TABLE -> Material.CHEST
        ConfigValueType.LIST -> Material.BOOK
        ConfigValueType.MAP -> Material.FILLED_MAP
        ConfigValueType.COMPOSITE -> Material.SHULKER_BOX
        ConfigValueType.COMPUTED -> Material.COMPARATOR
        ConfigValueType.REFERENCE -> Material.NAME_TAG
    }

    data class RenderedEntry(
        val entryKey: String,
        val displayName: String,
        val currentRaw: String?,
        val defaultRaw: String,
        val isTuned: Boolean,
        val isSecret: Boolean,
        val isDeprecated: Boolean,
        val isComputed: Boolean,
        val icon: Material,
        val scopeTag: String,
    )

    fun <T> render(entry: ConfigEntry<T>, qualifier: Any? = null): RenderedEntry {
        val currentRaw = rawFor(entry, qualifier)
        val defaultRaw = runCatching { entry.serializer.encode(entry.default) }.getOrDefault("?")
        val isComputed = entry.tags.contains("computed") || entry.type == ConfigValueType.COMPUTED
        return RenderedEntry(
            entryKey = entry.key,
            displayName = entry.displayNameKey.value,
            currentRaw = currentRaw,
            defaultRaw = defaultRaw,
            isTuned = currentRaw != null && currentRaw != defaultRaw,
            isSecret = entry.secret,
            isDeprecated = entry.deprecated != null,
            isComputed = isComputed,
            icon = typeIcon(entry.type),
            scopeTag = scopeColor(entry.scope),
        )
    }

    private fun <T> rawFor(entry: ConfigEntry<T>, qualifier: Any?): String? {
        if (entry.scope.qualifierKind != ConfigScope.QualifierKind.NONE && qualifier == null) return null
        return try { ConfigStore.rawGet(entry, qualifier) } catch (_: Throwable) { null }
    }

    fun redactIfSecret(entry: ConfigEntry<*>, raw: String?): String {
        if (entry.secret) return "***"
        return raw ?: "(default)"
    }

    fun entryItem(
        entry: ConfigEntry<*>,
        qualifier: Any? = null,
        displayName: String = entry.displayNameKey.value,
        description: String? = null,
    ): ItemStack {
        val rendered = render(entry, qualifier)
        return itemStack(rendered.icon) {
            val tunedStar = if (rendered.isTuned) "<gold>★ " else ""
            val scopeColor = rendered.scopeTag
            name("$scopeColor$tunedStar$displayName")
            if (rendered.isDeprecated) {
                lore("<red>⚠ DEPRECATED")
            }
            if (rendered.isComputed) {
                lore("<aqua>⚙ COMPUTED")
            }
            if (description != null) {
                emptyLoreLine()
                lore("<gray>$description")
            }
            emptyLoreLine()
            lore("<gray>Type: <white>${entry.type}")
            lore("<gray>Scope: $scopeColor${entry.scope}")
            if (entry.category.isNotBlank()) {
                lore("<gray>Category: <white>${entry.category}")
            }
            lore("<gray>Current: <yellow>${redactIfSecret(entry, rendered.currentRaw)}")
            lore("<gray>Default: <white>${if (entry.secret) "***" else rendered.defaultRaw}")
            if (rendered.isTuned) {
                lore("<gold>★ tuned")
            }
            emptyLoreLine()
            lore("<green>Left-click<dark_gray>: <gray>Edit")
            lore("<yellow>Right-click<dark_gray>: <gray>History")
            lore("<red>Shift-click<dark_gray>: <gray>Reset to default")
            clean()
        }
    }

    fun scopeItem(scope: ConfigScope, entryCount: Int): ItemStack {
        val color = scopeColor(scope)
        return itemStack(scopeIcon(scope)) {
            name("$color<bold>${scope.name}")
            lore("<gray>${entryCount} entries")
            emptyLoreLine()
            lore("<yellow>Click to browse")
            clean()
        }
    }

    fun countEntries(scope: ConfigScope): Int = ConfigCatalog.byScope(scope).size

    fun deprecationBanner(entry: ConfigEntry<*>): ItemStack? {
        val info = entry.deprecated ?: return null
        return itemStack(Material.BARRIER) {
            name("<red><bold>DEPRECATED")
            info.replacementKey?.let { lore("<gray>Use <yellow>$it<gray> instead") }
            info.sinceVersion?.let { lore("<gray>Deprecated since <white>$it") }
            info.removalVersion?.let { lore("<gray>Removal in <red>$it") }
            clean()
        }
    }

    fun entryDescriptionKey(entry: ConfigEntry<*>): TranslationKey = entry.descriptionKey

    fun groupByCategory(entries: List<ConfigEntry<*>>): Map<String, List<ConfigEntry<*>>> =
        entries.groupBy { it.category }

    fun groupBySubCategory(entries: List<ConfigEntry<*>>): Map<String, List<ConfigEntry<*>>> =
        entries.groupBy { it.subCategory ?: "default" }

    fun storageKeyOf(entry: ConfigEntry<*>, qualifier: Any?): String? =
        entry.storageKeyOrNull(qualifier)

    fun defaultEditableScopes(): List<ConfigScope> = listOf(
        ConfigScope.NETWORK,
        ConfigScope.GAME_MODE,
        ConfigScope.SERVER,
        ConfigScope.POOL,
        ConfigScope.PLAYER,
        ConfigScope.GATEWAY,
        ConfigScope.FEATURE_FLAG,
        ConfigScope.DISCORD_GUILD,
    )

    fun guessAnvilTitleKey(entry: ConfigEntry<*>): TranslationKey =
        "orbit.config.edit.anvil_title".asTranslationKey()

    fun isEditableType(type: ConfigValueType): Boolean = when (type) {
        ConfigValueType.BOOL,
        ConfigValueType.INT,
        ConfigValueType.LONG,
        ConfigValueType.DOUBLE,
        ConfigValueType.STRING,
        ConfigValueType.ENUM,
        ConfigValueType.DURATION,
        ConfigValueType.MATERIAL,
        ConfigValueType.LOCALE -> true
        else -> false
    }
}
