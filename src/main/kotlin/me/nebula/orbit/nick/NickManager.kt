package me.nebula.orbit.nick

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.nick.NickData
import me.nebula.gravity.nick.NickPoolManager
import me.nebula.gravity.nick.NickStore
import me.nebula.gravity.nick.VirtualIdentity
import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.vanish.VanishManager
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket
import net.minestom.server.network.packet.server.play.EntityHeadLookPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import net.minestom.server.network.packet.server.play.SpawnEntityPacket
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.tag.Tag
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private const val META_SKIN_PARTS = 17

class SessionDelta(
    val kills: AtomicInteger = AtomicInteger(0),
    val deaths: AtomicInteger = AtomicInteger(0),
    val wins: AtomicInteger = AtomicInteger(0),
    val gamesPlayed: AtomicInteger = AtomicInteger(0),
    val coinsEarned: AtomicLong = AtomicLong(0),
    val xpEarned: AtomicLong = AtomicLong(0),
)

object NickManager {

    private val NICK_NAME_TAG = Tag.String("nebula:nick_name")
    private val NICK_SKIN_TEX_TAG = Tag.String("nebula:nick_skin_tex")
    private val NICK_SKIN_SIG_TAG = Tag.String("nebula:nick_skin_sig")

    private val sessionDeltas = ConcurrentHashMap<UUID, SessionDelta>()
    private val logger = logger("NickManager")
    private var eventNode: EventNode<Event>? = null

    fun applyNick(player: Player, nickData: NickData) {
        player.setTag(NICK_NAME_TAG, nickData.nickName)
        player.setTag(NICK_SKIN_TEX_TAG, nickData.skinTextures)
        player.setTag(NICK_SKIN_SIG_TAG, nickData.skinSignature)
        player.displayName = miniMessage.deserialize("<white>${nickData.nickName}")
        sessionDeltas[player.uuid] = SessionDelta()
        logger.info { "Nick applied: ${player.username} -> ${nickData.nickName}" }

        val instance = player.instance ?: return
        for (other in instance.players) {
            if (other === player) continue
            if (VanishManager.isVanished(player) && !VanishManager.canSee(other, player)) continue
            sendNickedInfoTo(other, player, nickData)
        }
    }

    fun removeNick(player: Player) {
        val previousNick = displayName(player)
        player.removeTag(NICK_NAME_TAG)
        player.removeTag(NICK_SKIN_TEX_TAG)
        player.removeTag(NICK_SKIN_SIG_TAG)
        player.displayName = null
        sessionDeltas.remove(player.uuid)
        logger.info { "Nick removed: ${player.username} (was $previousNick)" }

        val instance = player.instance ?: return
        for (other in instance.players) {
            if (other === player) continue
            if (VanishManager.isVanished(player) && !VanishManager.canSee(other, player)) continue
            sendRealInfoTo(other, player)
        }
    }

    fun sendNickedInfoTo(viewer: Player, target: Player, nickData: NickData) {
        viewer.sendPacket(PlayerInfoRemovePacket(listOf(target.uuid)))

        val property = PlayerInfoUpdatePacket.Property(
            "textures", nickData.skinTextures, nickData.skinSignature
        )
        viewer.sendPacket(PlayerInfoUpdatePacket(
            EnumSet.of(
                PlayerInfoUpdatePacket.Action.ADD_PLAYER,
                PlayerInfoUpdatePacket.Action.UPDATE_LISTED,
            ),
            listOf(
                PlayerInfoUpdatePacket.Entry(
                    target.uuid, nickData.nickName,
                    listOf(property),
                    true, 0, target.gameMode, null, null, 0, true,
                )
            ),
        ))

        respawnEntityFor(viewer, target)
    }

    fun sendRealInfoTo(viewer: Player, target: Player) {
        viewer.sendPacket(PlayerInfoRemovePacket(listOf(target.uuid)))

        val skin = target.skin
        val properties = if (skin != null) {
            listOf(PlayerInfoUpdatePacket.Property("textures", skin.textures(), skin.signature()))
        } else {
            emptyList()
        }
        viewer.sendPacket(PlayerInfoUpdatePacket(
            EnumSet.of(
                PlayerInfoUpdatePacket.Action.ADD_PLAYER,
                PlayerInfoUpdatePacket.Action.UPDATE_LISTED,
            ),
            listOf(
                PlayerInfoUpdatePacket.Entry(
                    target.uuid, target.username,
                    properties,
                    true, 0, target.gameMode, null, null, 0, true,
                )
            ),
        ))

        respawnEntityFor(viewer, target)
    }

    fun isNicked(player: Player): Boolean = player.getTag(NICK_NAME_TAG) != null

    fun displayName(player: Player): String = player.getTag(NICK_NAME_TAG) ?: player.username

    fun sessionDelta(player: Player): SessionDelta? = sessionDeltas[player.uuid]

    fun incrementKill(player: Player) { sessionDeltas[player.uuid]?.kills?.incrementAndGet() }
    fun incrementDeath(player: Player) { sessionDeltas[player.uuid]?.deaths?.incrementAndGet() }
    fun incrementWin(player: Player) { sessionDeltas[player.uuid]?.wins?.incrementAndGet() }
    fun incrementGamesPlayed(player: Player) { sessionDeltas[player.uuid]?.gamesPlayed?.incrementAndGet() }
    fun addCoins(player: Player, amount: Long) { sessionDeltas[player.uuid]?.coinsEarned?.addAndGet(amount) }
    fun addXp(player: Player, amount: Long) { sessionDeltas[player.uuid]?.xpEarned?.addAndGet(amount) }

    fun virtualLevel(player: Player, identity: VirtualIdentity): Int = identity.level
    fun virtualPrestige(player: Player, identity: VirtualIdentity): Int = identity.prestige
    fun virtualRank(player: Player, identity: VirtualIdentity): String = identity.rank
    fun virtualCoins(player: Player, identity: VirtualIdentity): Long = identity.coins + (sessionDeltas[player.uuid]?.coinsEarned?.get() ?: 0)
    fun virtualKills(player: Player, identity: VirtualIdentity): Int = identity.kills + (sessionDeltas[player.uuid]?.kills?.get() ?: 0)
    fun virtualDeaths(player: Player, identity: VirtualIdentity): Int = identity.deaths + (sessionDeltas[player.uuid]?.deaths?.get() ?: 0)
    fun virtualWins(player: Player, identity: VirtualIdentity): Int = identity.wins + (sessionDeltas[player.uuid]?.wins?.get() ?: 0)
    fun virtualGamesPlayed(player: Player, identity: VirtualIdentity): Int = identity.gamesPlayed + (sessionDeltas[player.uuid]?.gamesPlayed?.get() ?: 0)
    fun virtualFirstLogin(player: Player, identity: VirtualIdentity): Long = identity.firstLoginEpoch
    fun virtualLastLogin(player: Player, identity: VirtualIdentity): Long = identity.lastLoginEpoch

    fun cleanupPlayer(uuid: UUID) {
        sessionDeltas.remove(uuid)
    }

    fun installListeners() {
        val node = EventNode.all("nick-manager")

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            val player = event.player
            if (isNicked(player)) {
                val nickData = NickStore.load(player.uuid)
                if (nickData != null) {
                    NickPoolManager.release(nickData.nickName)
                    NickStore.delete(player.uuid)
                }
                removeNick(player)
            }
            cleanupPlayer(player.uuid)
        }

        node.addListener(PlayerSpawnEvent::class.java) { event ->
            if (event.isFirstSpawn) return@addListener
            val player = event.player
            if (!isNicked(player)) return@addListener
            val nickData = NickStore.load(player.uuid) ?: return@addListener
            for (other in event.spawnInstance.players) {
                if (other === player) continue
                if (VanishManager.isVanished(player) && !VanishManager.canSee(other, player)) continue
                sendNickedInfoTo(other, player, nickData)
            }
        }

        eventNode = node
        MinecraftServer.getGlobalEventHandler().addChild(node)
    }

    fun uninstallListeners() {
        val node = eventNode ?: return
        MinecraftServer.getGlobalEventHandler().removeChild(node)
        eventNode = null
    }

    private fun respawnEntityFor(viewer: Player, target: Player) {
        viewer.sendPacket(DestroyEntitiesPacket(listOf(target.entityId)))

        val pos = target.position
        viewer.sendPacket(SpawnEntityPacket(
            target.entityId, target.uuid, EntityType.PLAYER,
            pos, pos.yaw(), 0, Vec.ZERO,
        ))

        viewer.sendPacket(EntityHeadLookPacket(target.entityId, pos.yaw()))

        viewer.sendPacket(EntityMetaDataPacket(target.entityId, mapOf(
            META_SKIN_PARTS to Metadata.Byte(0x7F),
        )))

        val equipment = buildMap {
            for (slot in EquipmentSlot.entries) {
                val item = target.getEquipment(slot)
                if (!item.isAir) put(slot, item)
            }
        }
        if (equipment.isNotEmpty()) {
            viewer.sendPacket(EntityEquipmentPacket(target.entityId, equipment))
        }
    }
}
