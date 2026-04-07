package me.nebula.orbit.mode.hub

import me.nebula.ether.utils.hazelcast.HazelcastStructureProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.host.HostRequestLookupStore
import me.nebula.gravity.host.HostTicketData
import me.nebula.gravity.host.HostTicketStore
import me.nebula.gravity.messaging.HostProvisionRequestMessage
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.party.PartyManager
import me.nebula.gravity.queue.PoolConfig
import me.nebula.gravity.queue.PoolConfigStore
import me.nebula.gravity.rank.RankManager
import me.nebula.orbit.mutator.MutatorRegistry
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.confirmGui
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object HostMenu {

    private val logger = logger("HostMenu")
    private val idGenerator by lazy { HazelcastStructureProvider.flakeIdGenerator("host-request-ids") }
    private val pendingPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun removePending(playerId: UUID) {
        pendingPlayers.remove(playerId)
    }

    fun openGameModeMenu(player: Player) {
        val ticketData = HostTicketStore.load(player.uuid) ?: HostTicketData()
        if (ticketData.tickets <= 0 && !RankManager.hasPermission(player.uuid, "*")) {
            player.sendMessage(player.translate("orbit.host.error.no_tickets"))
            return
        }

        val hostableModes = PoolConfigStore.all().filter { it.hostable }
        if (hostableModes.isEmpty()) {
            player.sendMessage(player.translate("orbit.host.error.no_modes"))
            return
        }

        val gui = gui(player.translateRaw("orbit.host.menu.title"), rows = 3) {
            var slotIndex = 10
            for (config in hostableModes) {
                if (slotIndex > 16) break
                slot(slotIndex, itemStack(Material.DIAMOND_SWORD) {
                    name(player.translateRaw("orbit.host.gamemode.name", "gamemode" to config.gameMode))
                    lore(player.translateRaw("orbit.host.gamemode.players", "max" to config.maxPlayersPerServer.toString()))
                    lore("")
                    lore(player.translateRaw("orbit.host.tickets.count", "count" to ticketData.tickets.toString()))
                    clean()
                }) { p ->
                    val freshTickets = HostTicketStore.load(p.uuid) ?: HostTicketData()
                    if (freshTickets.tickets <= 0 && !RankManager.hasPermission(p.uuid, "*")) {
                        p.sendMessage(p.translate("orbit.host.error.no_tickets"))
                        return@slot
                    }
                    if (config.maps.size > 1) {
                        openMapMenu(p, config)
                    } else {
                        openMutatorMenu(p, config, config.maps.firstOrNull())
                    }
                }
                slotIndex++
            }
            fillDefault()
        }
        player.openGui(gui)
    }

    private fun openMapMenu(player: Player, config: PoolConfig) {
        val gui = gui(player.translateRaw("orbit.host.map.title"), rows = 3) {
            var slotIndex = 10
            for (map in config.maps) {
                if (slotIndex > 16) break
                slot(slotIndex, itemStack(Material.FILLED_MAP) {
                    name(player.translateRaw("orbit.host.map.name", "map" to map))
                    clean()
                }) { p -> openMutatorMenu(p, config, map) }
                slotIndex++
            }
            slot(18, itemStack(Material.ARROW) {
                name(player.translateRaw("orbit.host.back"))
                clean()
            }) { p -> openGameModeMenu(p) }
            fillDefault()
        }
        player.openGui(gui)
    }

    private fun openMutatorMenu(player: Player, config: PoolConfig, map: String?, selected: MutableSet<String> = mutableSetOf()) {
        val mutators = MutatorRegistry.all().filter { !it.random }

        val gui = gui(player.translateRaw("orbit.host.mutators.title"), rows = 5) {
            fillDefault()
            var slotIndex = 10
            for (mutator in mutators) {
                if (slotIndex > 34) break
                if (slotIndex % 9 == 0) slotIndex++
                if (slotIndex % 9 == 8) slotIndex += 2

                val active = mutator.id in selected
                val material = Material.fromKey(mutator.material) ?: Material.PAPER
                slot(slotIndex, itemStack(material) {
                    name(player.translateRaw(mutator.nameKey))
                    lore(player.translateRaw(mutator.descriptionKey))
                    if (active) {
                        lore("")
                        lore("<green>${player.translateRaw("orbit.host.mutators.active")}")
                        glowing()
                    }
                    clean()
                }) { p ->
                    if (active) {
                        selected.remove(mutator.id)
                    } else {
                        if (mutator.conflictGroup != null) {
                            selected.removeAll { id ->
                                MutatorRegistry[id]?.conflictGroup == mutator.conflictGroup
                            }
                        }
                        selected.add(mutator.id)
                    }
                    openMutatorMenu(p, config, map, selected)
                }
                slotIndex++
            }

            slot(40, itemStack(Material.EMERALD_BLOCK) {
                name("<green>${player.translateRaw("orbit.host.mutators.confirm")}")
                if (selected.isNotEmpty()) {
                    lore(player.translateRaw("orbit.host.mutators.selected", "count" to selected.size.toString()))
                }
                clean()
            }) { p -> openConfirmMenu(p, config, map, selected.toList()) }

            slot(36, itemStack(Material.ARROW) {
                name(player.translateRaw("orbit.host.back"))
                clean()
            }) { p ->
                if (config.maps.size > 1) openMapMenu(p, config) else openGameModeMenu(p)
            }
        }
        player.openGui(gui)
    }

    private fun openConfirmMenu(player: Player, config: PoolConfig, map: String?, mutators: List<String>) {
        val confirm = confirmGui(
            title = player.translateRaw("orbit.host.confirm.title"),
            confirmItem = itemStack(Material.EMERALD_BLOCK) {
                name(player.translateRaw("orbit.host.confirm.accept"))
                lore(player.translateRaw("orbit.host.confirm.gamemode", "gamemode" to config.gameMode))
                map?.let { lore(player.translateRaw("orbit.host.confirm.map", "map" to it)) }
                if (mutators.isNotEmpty()) {
                    lore(player.translateRaw("orbit.host.confirm.mutators", "count" to mutators.size.toString()))
                }
                lore(player.translateRaw("orbit.host.confirm.cost"))
                clean()
            },
            cancelItem = itemStack(Material.REDSTONE_BLOCK) {
                name(player.translateRaw("orbit.host.confirm.cancel"))
                clean()
            },
            onConfirm = { p -> confirm(p, config, map, mutators) },
            onCancel = { p -> openGameModeMenu(p) },
        )
        player.openGui(confirm)
    }

    private fun confirm(player: Player, config: PoolConfig, map: String?, mutators: List<String> = emptyList()) {
        player.closeInventory()

        if (!pendingPlayers.add(player.uuid)) {
            player.sendMessage(player.translate("orbit.host.error.already_pending"))
            return
        }

        val ticketData = HostTicketStore.load(player.uuid) ?: HostTicketData()
        if (ticketData.tickets <= 0 && !RankManager.hasPermission(player.uuid, "*")) {
            pendingPlayers.remove(player.uuid)
            player.sendMessage(player.translate("orbit.host.error.no_tickets"))
            return
        }

        if (HostRequestLookupStore.exists(player.uuid)) {
            pendingPlayers.remove(player.uuid)
            player.sendMessage(player.translate("orbit.host.error.duplicate"))
            return
        }

        val members = PartyManager.collectMembers(player.uuid)
        val requestId = idGenerator.newId()

        NetworkMessenger.publish(HostProvisionRequestMessage(
            requestId = requestId,
            hostOwner = player.uuid,
            gameMode = config.gameMode,
            map = map,
            members = members,
            mutators = mutators,
        ))

        player.sendMessage(player.translate("orbit.host.status.requested"))
        logger.info { "Host request published: id=$requestId, host=${player.uuid}, gameMode=${config.gameMode}, map=$map, mutators=$mutators, members=${members.size}" }
    }

}
