package me.nebula.orbit.mode.hub

import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.PartyQueueNotificationMessage
import me.nebula.gravity.party.PartyManager
import me.nebula.gravity.config.isGameModeInMaintenance
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
import kotlin.math.min
import me.nebula.gravity.translation.Keys
import me.nebula.ether.utils.translation.asTranslationKey

object SelectorMenu {

    private val queuedGameModes = ConcurrentHashMap<UUID, MutableSet<String>>()

    fun isQueued(playerId: UUID, gameMode: String): Boolean =
        queuedGameModes[playerId]?.contains(gameMode) == true

    fun removeQueued(playerId: UUID, gameMode: String) {
        queuedGameModes[playerId]?.remove(gameMode)
    }

    fun removeAllQueued(playerId: UUID) {
        queuedGameModes.remove(playerId)
    }

    fun open(player: Player) {
        val config = HubDefinitions.CONFIG.selector
        val poolConfigs = PoolConfigStore.all().associateBy { it.gameMode }
        syncPlayerQueues(player.uuid, poolConfigs.keys)
        val playerQueues = queuedGameModes[player.uuid] ?: emptySet<String>()

        val gui = gui(player.translateRaw(Keys.Orbit.Selector.Title), config.rows) {
            border(Material.fromKey(config.border) ?: Material.GRAY_STAINED_GLASS_PANE)
            for (itemConfig in config.items) {
                val poolConfig = poolConfigs[itemConfig.gameMode] ?: continue
                val material = Material.fromKey(itemConfig.material) ?: Material.IRON_SWORD
                val maintenance = isGameModeInMaintenance(poolConfig.gameMode)
                val queued = poolConfig.gameMode in playerQueues

                slot(itemConfig.slot, itemStack(material) {
                    name(player.translateRaw("orbit.gamemode.${poolConfig.gameMode}".asTranslationKey()))
                    lore("")
                    when {
                        maintenance -> lore(player.translateRaw(Keys.Orbit.Selector.Maintenance))
                        queued -> {
                            glowing()
                            lore(player.translateRaw(Keys.Orbit.Selector.Queued))
                        }
                        else -> lore(player.translateRaw(Keys.Orbit.Selector.Click))
                    }
                    clean()
                }) { p ->
                    if (maintenance) {
                        p.sendMessage(p.translate(Keys.Orbit.Queue.Error.Maintenance))
                        return@slot
                    }
                    if (queued) {
                        leaveQueue(p, poolConfig)
                    } else {
                        joinQueue(p, poolConfig)
                    }
                }
            }
        }
        player.openGui(gui)
    }

    private fun joinQueue(player: Player, config: PoolConfig) {
        player.closeInventory()

        val members = PartyManager.collectMembers(player.uuid)

        if (members.size > config.maxPartySize) {
            player.sendMessage(player.translate(Keys.Orbit.Queue.Error.PartyTooLarge,
                "max" to config.maxPartySize.toString()))
            return
        }

        queuedGameModes.computeIfAbsent(player.uuid) { ConcurrentHashMap.newKeySet() }.add(config.gameMode)

        QueueStore.enqueue(config.gameMode, player.uuid, members)

        val displayName = player.translateRaw("orbit.gamemode.${config.gameMode}".asTranslationKey())
        player.sendMessage(player.translate(Keys.Orbit.Queue.Joined, "gamemode" to displayName))

        if (members.size > 1) {
            val bonusPercent = min((members.size - 1) * 5, 20)
            player.sendMessage(player.translate(Keys.Orbit.Party.QueueBonus,
                "bonus" to bonusPercent.toString()))

            val nonLeader = members.filter { it != player.uuid }
            if (nonLeader.isNotEmpty()) {
                NetworkMessenger.publish(PartyQueueNotificationMessage(
                    partyMemberIds = nonLeader,
                    leaderName = player.username,
                    gameMode = config.gameMode
                ))
            }
        }
    }

    private fun leaveQueue(player: Player, config: PoolConfig) {
        player.closeInventory()

        queuedGameModes[player.uuid]?.remove(config.gameMode)
        QueueStore.dequeuePlayer(player.uuid)

        val displayName = player.translateRaw("orbit.gamemode.${config.gameMode}".asTranslationKey())
        player.sendMessage(player.translate(Keys.Orbit.Queue.Left, "gamemode" to displayName))
    }

    private fun syncPlayerQueues(playerId: UUID, gameModes: Set<String>) {
        val actualQueues = ConcurrentHashMap.newKeySet<String>()
        for (gameMode in gameModes) {
            val queue = QueueStore.load(gameMode) ?: continue
            if (queue.entries.any { playerId in it.members }) {
                actualQueues.add(gameMode)
            }
        }
        if (actualQueues.isEmpty()) {
            queuedGameModes.remove(playerId)
        } else {
            queuedGameModes[playerId] = actualQueues
        }
    }

}
