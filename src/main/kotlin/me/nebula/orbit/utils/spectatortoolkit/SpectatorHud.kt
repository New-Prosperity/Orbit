package me.nebula.orbit.utils.spectatortoolkit

import me.nebula.orbit.utils.hud.HudAnchor
import me.nebula.orbit.utils.hud.HudManager
import me.nebula.orbit.utils.hud.hudLayout

internal object SpectatorHud {

    const val LAYOUT_ID = "spectator-hud"

    const val ELEMENT_TIMER = "timer"
    const val ELEMENT_ALIVE = "alive"
    const val ELEMENT_TARGET_NAME = "target_name"
    const val ELEMENT_TARGET_HEALTH = "target_health"
    const val ELEMENT_TARGET_ARMOR = "target_armor"
    const val ELEMENT_TARGET_KILLS = "target_kills"
    const val ELEMENT_FREECAM_INDICATOR = "freecam_indicator"
    const val ELEMENT_KILLFEED = "killfeed"

    @Volatile private var registered = false

    fun ensureRegistered(maxHealth: Int, maxArmor: Int) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            val layout = hudLayout(LAYOUT_ID) {
                text(ELEMENT_TIMER) {
                    anchor(HudAnchor.TOP_CENTER)
                    offset(-0.04f, 0.02f)
                }
                text(ELEMENT_ALIVE) {
                    anchor(HudAnchor.TOP_RIGHT)
                    offset(-0.10f, 0.02f)
                }
                text(ELEMENT_TARGET_NAME) {
                    anchor(HudAnchor.BOTTOM_CENTER)
                    offset(-0.12f, -0.24f)
                }
                bar(ELEMENT_TARGET_HEALTH) {
                    anchor(HudAnchor.BOTTOM_CENTER)
                    offset(-0.12f, -0.20f)
                    sprites(bg = "bar_bg", fill = "bar_fill_red", empty = "bar_empty")
                    segments(maxHealth)
                }
                bar(ELEMENT_TARGET_ARMOR) {
                    anchor(HudAnchor.BOTTOM_CENTER)
                    offset(-0.12f, -0.16f)
                    sprites(bg = "bar_bg", fill = "bar_fill_blue", empty = "bar_empty")
                    segments(maxArmor)
                }
                text(ELEMENT_TARGET_KILLS) {
                    anchor(HudAnchor.BOTTOM_CENTER)
                    offset(-0.02f, -0.12f)
                }
                sprite(ELEMENT_FREECAM_INDICATOR) {
                    anchor(HudAnchor.TOP_LEFT)
                    offset(0.02f, 0.02f)
                    sprite("icon_speed")
                }
                text(ELEMENT_KILLFEED) {
                    anchor(HudAnchor.TOP_LEFT)
                    offset(0.02f, 0.06f)
                }
            }
            HudManager.register(layout)
            registered = true
        }
    }
}
