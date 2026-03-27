package me.nebula.orbit.utils.vanish

import me.nebula.ether.utils.hazelcast.HazelcastStructureProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.nick.NickStore
import me.nebula.gravity.rank.RankManager
import me.nebula.orbit.nick.NickManager
import me.nebula.orbit.rankWeight
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.item.PickupItemEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import net.minestom.server.tag.Tag
import java.util.EnumSet
import java.util.UUID

object VanishManager {

    private val TAG = Tag.Boolean("nebula:vanished")
    private val vanishedMap by lazy { HazelcastStructureProvider.replicatedMap<UUID, Boolean>("vanished-players") }
    private val logger = logger("VanishManager")
    private var eventNode: EventNode<Event>? = null

    fun canSee(viewer: Player, target: Player): Boolean {
        if (!isVanished(target)) return true
        if (viewer.uuid == target.uuid) return true
        if (!RankManager.hasPermission(viewer.uuid, "staff.vanish.see")) return false
        return viewer.rankWeight <= target.rankWeight
    }

    fun vanish(player: Player) {
        player.setTag(TAG, true)
        vanishedMap[player.uuid] = true
        logger.info { "Vanish: ${player.username} vanished" }

        val instance = player.instance ?: return
        for (other in instance.players) {
            if (other === player) continue
            if (!canSee(other, player)) {
                other.sendPacket(DestroyEntitiesPacket(listOf(player.entityId)))
                other.sendPacket(PlayerInfoRemovePacket(listOf(player.uuid)))
            }
        }
    }

    var gameParticipantCheck: ((Player) -> Boolean) = { false }

    fun unvanish(player: Player) {
        player.removeTag(TAG)
        vanishedMap.remove(player.uuid)
        logger.info { "Vanish: ${player.username} unvanished" }

        val instance = player.instance ?: return
        val nickData = NickStore.load(player.uuid)
        for (other in instance.players) {
            if (other === player) continue
            if (nickData != null) {
                NickManager.sendNickedInfoTo(other, player, nickData)
            } else {
                player.updateNewViewer(other)
                sendPlayerInfoTo(other, player)
            }
        }
    }

    fun isVanished(player: Player): Boolean = player.getTag(TAG) == true

    fun isVanished(uuid: UUID): Boolean = vanishedMap.containsKey(uuid)

    fun toggle(player: Player): Boolean {
        return if (isVanished(player)) {
            unvanish(player)
            false
        } else {
            vanish(player)
            true
        }
    }

    fun visiblePlayerCount(viewer: Player? = null): Int {
        val online = MinecraftServer.getConnectionManager().onlinePlayers
        if (viewer == null) return online.count { !isVanished(it) }
        return online.count { canSee(viewer, it) }
    }

    fun installListeners() {
        val node = EventNode.all("vanish-manager")

        node.addListener(PlayerSpawnEvent::class.java) { event ->
            val joiner = event.player

            if (vanishedMap.containsKey(joiner.uuid)) {
                joiner.setTag(TAG, true)
                for (other in event.spawnInstance.players) {
                    if (other === joiner) continue
                    if (!canSee(other, joiner)) {
                        other.sendPacket(DestroyEntitiesPacket(listOf(joiner.entityId)))
                        other.sendPacket(PlayerInfoRemovePacket(listOf(joiner.uuid)))
                    }
                }
            }

            for (other in event.spawnInstance.players) {
                if (other === joiner) continue
                if (isVanished(other) && !canSee(joiner, other)) {
                    joiner.sendPacket(DestroyEntitiesPacket(listOf(other.entityId)))
                    joiner.sendPacket(PlayerInfoRemovePacket(listOf(other.uuid)))
                }
            }
        }

        node.addListener(EntityDamageEvent::class.java) { event ->
            val target = event.entity as? Player ?: return@addListener
            val damage = event.damage as? EntityDamage ?: return@addListener
            val attacker = damage.source as? Player ?: return@addListener
            if (isVanished(target) && !canSee(attacker, target)) event.isCancelled = true
        }

        node.addListener(PickupItemEvent::class.java) { event ->
            val player = event.livingEntity as? Player ?: return@addListener
            if (isVanished(player)) event.isCancelled = true
        }

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            vanishedMap.remove(event.player.uuid)
        }

        eventNode = node
        MinecraftServer.getGlobalEventHandler().addChild(node)
    }

    fun uninstallListeners() {
        val node = eventNode ?: return
        MinecraftServer.getGlobalEventHandler().removeChild(node)
        eventNode = null
    }

    private fun sendPlayerInfoTo(viewer: Player, target: Player) {
        val skin = target.skin
        val properties = if (skin != null) {
            listOf(PlayerInfoUpdatePacket.Property("textures", skin.textures(), skin.signature()))
        } else emptyList()
        viewer.sendPacket(PlayerInfoUpdatePacket(
            EnumSet.of(PlayerInfoUpdatePacket.Action.ADD_PLAYER, PlayerInfoUpdatePacket.Action.UPDATE_LISTED),
            listOf(PlayerInfoUpdatePacket.Entry(
                target.uuid, target.username, properties,
                true, 0, target.gameMode, null, null, 0, true,
            )),
        ))
    }
}

val Player.isVanished: Boolean get() = VanishManager.isVanished(this)
fun Player.canSee(other: Player): Boolean = VanishManager.canSee(this, other)
fun Player.vanish() = VanishManager.vanish(this)
fun Player.unvanish() = VanishManager.unvanish(this)
fun Player.toggleVanish(): Boolean = VanishManager.toggle(this)
