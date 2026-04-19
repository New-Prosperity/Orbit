package me.nebula.orbit.marketplace

import me.nebula.gravity.cosmetic.CosmeticPlayerData
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.orbit.cosmetic.CosmeticRegistry
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import me.nebula.gravity.translation.Keys

object TradeMenu {

    private val DIVIDER = itemStack(Material.BLACK_STAINED_GLASS_PANE) { name(" "); hideTooltip() }
    private val FILLER = itemStack(Material.GRAY_STAINED_GLASS_PANE) { name(" "); hideTooltip() }

    fun openForBoth(session: TradeManager.TradeSessionData, initiator: Player, target: Player) {
        openFor(session, initiator)
        openFor(session, target)
    }

    private fun openFor(session: TradeManager.TradeSessionData, player: Player) {
        val otherId = session.otherId(player.uuid)
        val otherName = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(otherId)?.username ?: "?"

        val playerData = CosmeticStore.load(player.uuid) ?: CosmeticPlayerData()
        val myOffers = session.offersOf(player.uuid)
        val theirOffers = session.otherOffersOf(player.uuid)

        val gui = gui(player.translateRaw(Keys.Orbit.Trade.MenuTitle, "player" to otherName), rows = 6) {
            for (i in 0 until 54) {
                val col = i % 9
                if (col == 4) {
                    slot(i, DIVIDER)
                }
            }

            val ownedList = playerData.owned.keys
                .mapNotNull { id -> CosmeticRegistry[id]?.let { id to it } }
                .filter { (_, def) -> def.price > 0 }
                .sortedBy { (_, def) -> def.rarity.ordinal }

            val leftSlots = listOf(0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30, 36, 37, 38, 39)
            ownedList.take(leftSlots.size).forEachIndexed { idx, (cosmeticId, definition) ->
                val slotIdx = leftSlots[idx]
                val offered = cosmeticId in myOffers
                val material = Material.fromKey(definition.material) ?: Material.BARRIER
                slot(slotIdx, itemStack(material) {
                    name("${definition.rarity.colorTag}${player.translateRaw(definition.nameKey)}")
                    if (offered) {
                        lore("<green>${player.translateRaw(Keys.Orbit.Trade.Offered)}")
                        glowing()
                    } else {
                        lore("<yellow>${player.translateRaw(Keys.Orbit.Trade.ClickToOffer)}")
                    }
                    clean()
                }) { p -> TradeManager.toggleOffer(p, cosmeticId) }
            }

            val rightSlots = listOf(5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35, 41, 42, 43, 44)
            theirOffers.mapNotNull { id -> CosmeticRegistry[id]?.let { id to it } }
                .take(rightSlots.size)
                .forEachIndexed { idx, (_, definition) ->
                    val slotIdx = rightSlots[idx]
                    val material = Material.fromKey(definition.material) ?: Material.BARRIER
                    slot(slotIdx, itemStack(material) {
                        name("${definition.rarity.colorTag}${player.translateRaw(definition.nameKey)}")
                        lore("<aqua>${player.translateRaw(Keys.Orbit.Trade.TheirOffer)}")
                        clean()
                    })
                }

            val myConfirmed = session.isConfirmedBy(player.uuid)
            val otherConfirmed = session.isConfirmedBy(otherId)

            slot(48, itemStack(if (myConfirmed) Material.LIME_WOOL else Material.GREEN_WOOL) {
                if (myConfirmed) {
                    name("<green>${player.translateRaw(Keys.Orbit.Trade.Confirmed)}")
                    if (otherConfirmed) {
                        lore("<green>${player.translateRaw(Keys.Orbit.Trade.BothConfirmed)}")
                    } else {
                        lore("<yellow>${player.translateRaw(Keys.Orbit.Trade.Waiting)}")
                    }
                    glowing()
                } else {
                    name("<green>${player.translateRaw(Keys.Orbit.Trade.Confirm)}")
                    if (otherConfirmed) {
                        lore("<green>${player.translateRaw(Keys.Orbit.Trade.OtherConfirmed)}")
                    }
                }
                clean()
            }) { p -> TradeManager.confirm(p) }

            slot(50, itemStack(Material.RED_WOOL) {
                name("<red>${player.translateRaw(Keys.Orbit.Trade.Cancel)}")
                clean()
            }) { p -> TradeManager.cancel(p) }

            for (i in 45 until 54) {
                val col = i % 9
                if (col != 3 && col != 5 && col != 4) {
                    if (i !in slots) slot(i, FILLER)
                }
            }
        }
        player.openGui(gui)
    }
}
