package me.nebula.orbit.utils.hud

import net.minestom.server.entity.Player

fun Player.showHud(layoutId: String) = HudManager.show(this, layoutId)
fun Player.hideHud(layoutId: String) = HudManager.hide(this, layoutId)
fun Player.hideAllHuds() = HudManager.hideAll(this)
fun Player.isHudShowing(layoutId: String): Boolean = HudManager.isShowing(this, layoutId)
fun Player.updateHud(elementId: String, value: Any) = HudManager.update(this, elementId, value)
fun Player.updateHud(layoutId: String, elementId: String, value: Any) = HudManager.update(this, layoutId, elementId, value)
fun Player.addHudIcon(groupId: String, spriteId: String) = HudManager.addToGroup(this, groupId, spriteId)
fun Player.removeHudIcon(groupId: String, spriteId: String) = HudManager.removeFromGroup(this, groupId, spriteId)

fun Player.setHudCondition(elementId: String, predicate: (Player) -> Boolean) {
    for (hud in HudManager.playerHuds(uuid)) {
        if (hud.layout.elements.any { it.id == elementId }) {
            hud.conditions[elementId] = predicate
            return
        }
    }
}

fun Player.setHudCondition(layoutId: String, elementId: String, predicate: (Player) -> Boolean) {
    HudManager.playerHud(uuid, layoutId)?.conditions?.put(elementId, predicate)
}

fun Player.clearHudCondition(elementId: String) {
    for (hud in HudManager.playerHuds(uuid)) {
        hud.conditions.remove(elementId)
    }
}

fun Player.hud(layoutId: String): PlayerHud? = HudManager.playerHud(uuid, layoutId)
val Player.huds: Collection<PlayerHud> get() = HudManager.playerHuds(uuid)
val Player.activeHudIds: Set<String> get() = HudManager.activeLayoutIds(uuid)