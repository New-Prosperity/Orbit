package me.nebula.orbit.mode.hub

import me.nebula.gravity.party.PartyLookupStore
import me.nebula.gravity.party.PartyStore
import me.nebula.gravity.property.isGameModeInMaintenance
import me.nebula.gravity.queue.PoolConfig
import me.nebula.gravity.queue.PoolConfigStore
import me.nebula.gravity.queue.QueueStore
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SelectorMenu {

    private val queuedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun removeQueued(playerId: UUID) {
        queuedPlayers.remove(playerId)
    }

    fun open(player: Player) {
        val config = HubDefinitions.CONFIG.selector
        val poolConfigs = PoolConfigStore.all().associateBy { it.gameMode }

        val gui = gui(player.translateRaw("orbit.selector.title"), config.rows) {
            border(Material.fromKey(config.border) ?: Material.GRAY_STAINED_GLASS_PANE)
            for (itemConfig in config.items) {
                val poolConfig = poolConfigs[itemConfig.gameMode] ?: continue
                val material = Material.fromKey(itemConfig.material) ?: Material.IRON_SWORD
                val maintenance = isGameModeInMaintenance(poolConfig.gameMode)
                slot(itemConfig.slot, itemStack(material) {
                    name(player.translateRaw("orbit.gamemode.${poolConfig.gameMode}"))
                    lore("")
                    if (maintenance) {
                        lore(player.translateRaw("orbit.selector.maintenance"))
                    } else {
                        lore(player.translateRaw("orbit.selector.click"))
                    }
                }) { p ->
                    if (maintenance) {
                        p.sendMessage(p.translate("orbit.queue.error.maintenance"))
                        return@slot
                    }
                    joinQueue(p, poolConfig)
                }
            }
        }
        player.openGui(gui)
    }

    private fun joinQueue(player: Player, config: PoolConfig) {
        player.closeInventory()

        if (!queuedPlayers.add(player.uuid)) {
            player.sendMessage(player.translate("orbit.queue.error.already_queued"))
            return
        }

        val members = collectMembers(player.uuid)

        if (members.size > config.maxPartySize) {
            queuedPlayers.remove(player.uuid)
            player.sendMessage(player.translate("orbit.queue.error.party_too_large",
                "max" to config.maxPartySize.toString()))
            return
        }

        QueueStore.enqueue(config.gameMode, player.uuid, members)

        val displayName = config.gameMode.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        player.sendMessage(player.translate("orbit.queue.joined", "gamemode" to displayName))
    }

    private fun collectMembers(playerUuid: UUID): List<UUID> {
        val partyId = PartyLookupStore.load(playerUuid) ?: return listOf(playerUuid)
        val party = PartyStore.load(partyId) ?: return listOf(playerUuid)
        return party.members.toList()
    }
}
