package me.nebula.orbit.commands

import me.nebula.gravity.cache.CacheSlots
import me.nebula.gravity.cache.PlayerCache
import me.nebula.gravity.player.PreferenceData
import me.nebula.gravity.player.PreferenceStore
import me.nebula.gravity.player.SetPreferenceProcessor
import me.nebula.gravity.player.TogglePreferenceProcessor
import me.nebula.gravity.rank.RankManager
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import me.nebula.gravity.translation.Keys
import me.nebula.ether.utils.translation.asTranslationKey

private val PROFILE_VISIBILITY_OPTIONS = listOf("PUBLIC", "FRIENDS", "PRIVATE")
private val COSMETIC_DISPLAY_OPTIONS = listOf("FULL", "REDUCED", "NONE")

fun settingsCommand(): Command = command("settings") {
    onPlayerExecute { openSettingsGui(player) }
}

private fun openSettingsGui(player: Player) {
    val prefs = PlayerCache.get(player.uuid)?.get(CacheSlots.PREFERENCES) ?: PreferenceStore.load(player.uuid) ?: PreferenceData()

    gui(player.translateRaw(Keys.Orbit.Settings.Title), rows = 6) {
        fillDefault()
        clickSound(SoundEvent.UI_BUTTON_CLICK)
        closeButton(49)

        slot(1, categoryHeader(player, "orbit.settings.category.social", Material.PLAYER_HEAD))
        slot(10, toggleSlot(player, "orbit.settings.friend_requests", "orbit.settings.friend_requests.desc", Material.SKELETON_SKULL, prefs.friendRequests)) { p -> toggle(p, "friendRequests") }
        slot(11, toggleSlot(player, "orbit.settings.private_messages", "orbit.settings.private_messages.desc", Material.WRITABLE_BOOK, prefs.privateMessages)) { p -> toggle(p, "privateMessages") }
        slot(12, toggleSlot(player, "orbit.settings.party_invites", "orbit.settings.party_invites.desc", Material.FIREWORK_ROCKET, prefs.partyInvites)) { p -> toggle(p, "partyInvites") }
        slot(13, toggleSlot(player, "orbit.settings.duel_requests", "orbit.settings.duel_requests.desc", Material.IRON_SWORD, prefs.duelRequests)) { p -> toggle(p, "duelRequests") }
        slot(14, toggleSlot(player, "orbit.settings.trade_requests", "orbit.settings.trade_requests.desc", Material.EMERALD, prefs.tradeRequests)) { p -> toggle(p, "tradeRequests") }

        slot(3, categoryHeader(player, "orbit.settings.category.privacy", Material.ENDER_EYE))
        slot(19, toggleSlot(player, "orbit.settings.appear_offline", "orbit.settings.appear_offline.desc", Material.BARRIER, prefs.appearOffline)) { p -> toggle(p, "appearOffline") }
        slot(20, toggleSlot(player, "orbit.settings.last_seen", "orbit.settings.last_seen.desc", Material.CLOCK, prefs.lastSeenVisible)) { p -> toggle(p, "lastSeenVisible") }
        slot(21, toggleSlot(player, "orbit.settings.stats_visible", "orbit.settings.stats_visible.desc", Material.BOOK, prefs.statsVisible)) { p -> toggle(p, "statsVisible") }
        slot(22, cycleSlot(player, "orbit.settings.profile_visibility", "orbit.settings.profile_visibility.desc", Material.SHIELD, prefs.profileVisibility, PROFILE_VISIBILITY_OPTIONS)) { p ->
            cycle(p, "profileVisibility", PROFILE_VISIBILITY_OPTIONS)
        }

        slot(5, categoryHeader(player, "orbit.settings.category.display", Material.PAINTING))
        slot(28, cycleSlot(player, "orbit.settings.cosmetic_display", "orbit.settings.cosmetic_display.desc", Material.LEATHER_CHESTPLATE, prefs.cosmeticDisplay, COSMETIC_DISPLAY_OPTIONS)) { p ->
            cycle(p, "cosmeticDisplay", COSMETIC_DISPLAY_OPTIONS)
        }

        if (RankManager.hasPermission(player.uuid, "nebula.streamer")) {
            slot(7, categoryHeader(player, "orbit.settings.category.special", Material.NETHER_STAR))
            slot(34, toggleSlot(player, "orbit.settings.streamer_mode", "orbit.settings.streamer_mode.desc", Material.SPYGLASS, prefs.streamerMode)) { p -> toggle(p, "streamerMode") }
        }

        if (RankManager.hasPermission(player.uuid, "staff.vanish")) {
            if (!RankManager.hasPermission(player.uuid, "nebula.streamer")) {
                slot(7, categoryHeader(player, "orbit.settings.category.special", Material.NETHER_STAR))
            }
            slot(35, toggleSlot(player, "orbit.settings.staff_auto_vanish", "orbit.settings.staff_auto_vanish.desc", Material.POTION, prefs.staffAutoVanish)) { p -> toggle(p, "staffAutoVanish") }
        }
    }.open(player)
}

private fun toggle(player: Player, field: String) {
    PreferenceStore.executeOnKey(player.uuid, TogglePreferenceProcessor(field))
    openSettingsGui(player)
}

private fun cycle(player: Player, field: String, options: List<String>) {
    val current = when (field) {
        "profileVisibility" -> PlayerCache.get(player.uuid)?.get(CacheSlots.PREFERENCES)?.profileVisibility ?: "PUBLIC"
        "cosmeticDisplay" -> PlayerCache.get(player.uuid)?.get(CacheSlots.PREFERENCES)?.cosmeticDisplay ?: "FULL"
        else -> options.firstOrNull() ?: return
    }
    val index = options.indexOf(current)
    val next = options[(index + 1) % options.size]
    PreferenceStore.executeOnKey(player.uuid, SetPreferenceProcessor(field, next))
    openSettingsGui(player)
}

private fun categoryHeader(player: Player, key: String, material: Material): ItemStack =
    itemStack(material) {
        name("<gold>${player.translateRaw(key.asTranslationKey())}")
        clean()
    }

private fun toggleSlot(player: Player, nameKey: String, descKey: String, icon: Material, enabled: Boolean): ItemStack {
    val statusColor = if (enabled) "<green>" else "<red>"
    val statusKey = if (enabled) "orbit.settings.enabled" else "orbit.settings.disabled"
    return itemStack(icon) {
        name("$statusColor${player.translateRaw(nameKey.asTranslationKey())}")
        lore("<gray>${player.translateRaw(descKey.asTranslationKey())}")
        emptyLoreLine()
        lore("$statusColor${player.translateRaw(statusKey.asTranslationKey())}")
        lore("<yellow>${player.translateRaw(Keys.Orbit.Settings.ClickToggle)}")
        if (enabled) glowing()
        clean()
    }
}

private fun cycleSlot(player: Player, nameKey: String, descKey: String, icon: Material, current: String, options: List<String>): ItemStack {
    val index = options.indexOf(current)
    val next = options[(index + 1) % options.size]
    return itemStack(icon) {
        name("<white>${player.translateRaw(nameKey.asTranslationKey())}")
        lore("<gray>${player.translateRaw(descKey.asTranslationKey())}")
        emptyLoreLine()
        options.forEach { option ->
            val prefix = if (option == current) "<green>\u25b6 " else "<dark_gray>  "
            lore("$prefix${player.translateRaw("orbit.settings.option.$option".asTranslationKey())}")
        }
        emptyLoreLine()
        lore("<yellow>${player.translateRaw(Keys.Orbit.Settings.ClickCycle, "next" to player.translateRaw("orbit.settings.option.$next".asTranslationKey()))}")
        clean()
    }
}
