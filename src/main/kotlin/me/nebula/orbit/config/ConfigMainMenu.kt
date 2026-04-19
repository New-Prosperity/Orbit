package me.nebula.orbit.config

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.config.ConfigScope
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

object ConfigMainMenu {

    private val scopeLayout: List<Pair<Int, ConfigScope>> = listOf(
        10 to ConfigScope.NETWORK,
        11 to ConfigScope.GAME_MODE,
        12 to ConfigScope.SERVER,
        13 to ConfigScope.POOL,
        14 to ConfigScope.PLAYER,
        15 to ConfigScope.GATEWAY,
        16 to ConfigScope.FEATURE_FLAG,
        19 to ConfigScope.DISCORD_GUILD,
    )

    fun open(player: Player) {
        val title = player.translateRaw("orbit.config.menu.title".asTranslationKey())
        val menu = gui(title, rows = 3) {
            borderDefault()
            clickSound(SoundEvent.UI_BUTTON_CLICK)
            for ((slot, scope) in scopeLayout) {
                val count = ConfigRenderer.countEntries(scope)
                slot(slot, ConfigRenderer.scopeItem(scope, count)) { p ->
                    ConfigScopeMenu.open(p, scope)
                }
            }
            slot(22, itemStack(Material.WRITABLE_BOOK) {
                name("<light_purple><bold>${player.translateRaw("orbit.config.menu.drafts".asTranslationKey())}")
                lore("<gray>${player.translateRaw("orbit.config.menu.drafts_desc".asTranslationKey())}")
                clean()
            }) { p -> ConfigDraftsMenu.open(p) }
            slot(21, itemStack(Material.CLOCK) {
                name("<gold><bold>${player.translateRaw("orbit.config.menu.schedules".asTranslationKey())}")
                lore("<gray>${player.translateRaw("orbit.config.menu.schedules_desc".asTranslationKey())}")
                clean()
            }) { p -> ConfigSchedulesMenu.open(p) }
            slot(23, itemStack(Material.BOOK) {
                name("<green><bold>${player.translateRaw("orbit.config.menu.history".asTranslationKey())}")
                lore("<gray>${player.translateRaw("orbit.config.menu.history_desc".asTranslationKey())}")
                clean()
            }) { p -> ConfigHistoryMenu.openGlobal(p) }
            closeButton(26)
        }
        player.openGui(menu)
    }
}
