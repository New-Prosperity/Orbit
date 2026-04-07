package me.nebula.orbit.utils.gametest

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.play.ActionBarPacket
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.network.packet.server.play.SetTitleTextPacket
import net.minestom.server.network.packet.server.play.SoundEffectPacket
import net.minestom.server.network.packet.server.play.SystemChatPacket
import net.minestom.server.network.player.PlayerConnection
import net.minestom.server.sound.SoundEvent
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class PacketInterceptor {

    private val captured = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SendablePacket>>()

    fun intercept(playerUuid: UUID, packet: SendablePacket) {
        captured.getOrPut(playerUuid) { CopyOnWriteArrayList() }.add(packet)
    }

    fun packetsFor(player: Player): List<SendablePacket> =
        captured[player.uuid]?.toList() ?: emptyList()

    inline fun <reified T : SendablePacket> packetsOf(player: Player): List<T> =
        packetsFor(player).filterIsInstance<T>()

    fun packetCount(player: Player): Int =
        captured[player.uuid]?.size ?: 0

    inline fun <reified T : SendablePacket> packetCountOf(player: Player): Int =
        packetsOf<T>(player).size

    fun clear(player: Player) {
        captured[player.uuid]?.clear()
    }

    fun clearAll() {
        captured.clear()
    }

    fun remove(playerUuid: UUID) {
        captured.remove(playerUuid)
    }
}

class TestPlayerConnection(
    private val interceptor: PacketInterceptor,
    private val playerUuid: UUID,
) : PlayerConnection() {

    private val fakeAddress: SocketAddress = InetSocketAddress("127.0.0.1", 25565)

    override fun sendPacket(packet: SendablePacket) {
        interceptor.intercept(playerUuid, packet)
    }

    override fun getRemoteAddress(): SocketAddress = fakeAddress

    override fun disconnect() {
        runCatching {
            val field = PlayerConnection::class.java.getDeclaredField("online")
            field.isAccessible = true
            field.setBoolean(this, false)
        }
    }
}

private val plainSerializer = PlainTextComponentSerializer.plainText()

fun GameTestContext.assertAnyPacketSent(player: Player, message: String = "") {
    val count = packets.packetCount(player)
    if (count == 0) {
        val detail = if (message.isNotEmpty()) "$message: " else ""
        throw GameTestFailure("${detail}expected at least one packet sent to ${player.username} but none were captured")
    }
}

inline fun <reified T : SendablePacket> GameTestContext.assertPacketSent(player: Player, message: String = "") {
    val matched = packets.packetsOf<T>(player)
    if (matched.isEmpty()) {
        val detail = if (message.isNotEmpty()) "$message: " else ""
        throw GameTestFailure("${detail}expected ${T::class.simpleName} sent to ${player.username} but none were captured")
    }
}

inline fun <reified T : SendablePacket> GameTestContext.assertPacketNotSent(player: Player, message: String = "") {
    val matched = packets.packetsOf<T>(player)
    if (matched.isNotEmpty()) {
        val detail = if (message.isNotEmpty()) "$message: " else ""
        throw GameTestFailure("${detail}expected no ${T::class.simpleName} sent to ${player.username} but ${matched.size} were captured")
    }
}

inline fun <reified T : SendablePacket> GameTestContext.assertPacketCount(player: Player, expected: Int, message: String = "") {
    val actual = packets.packetCountOf<T>(player)
    if (actual != expected) {
        val detail = if (message.isNotEmpty()) "$message: " else ""
        throw GameTestFailure("${detail}expected $expected ${T::class.simpleName} sent to ${player.username} but $actual were captured")
    }
}

inline fun <reified T : SendablePacket> Player.receivedPackets(context: GameTestContext): List<T> =
    context.packets.packetsOf<T>(this)

fun GameTestContext.assertReceivedMessage(player: Player, containing: String) {
    val chatPackets = packets.packetsOf<SystemChatPacket>(player)
    val found = chatPackets.any { plainSerializer.serialize(it.message()).contains(containing, ignoreCase = true) }
    if (!found) {
        throw GameTestFailure("expected ${player.username} to receive a chat message containing '$containing' but none matched (${chatPackets.size} chat packets captured)")
    }
}

fun GameTestContext.assertReceivedTitle(player: Player) {
    val titles = packets.packetsOf<SetTitleTextPacket>(player)
    if (titles.isEmpty()) {
        throw GameTestFailure("expected ${player.username} to receive a title but no SetTitleTextPacket was captured")
    }
}

fun GameTestContext.assertReceivedActionBar(player: Player) {
    val bars = packets.packetsOf<ActionBarPacket>(player)
    if (bars.isEmpty()) {
        throw GameTestFailure("expected ${player.username} to receive an action bar but no ActionBarPacket was captured")
    }
}

fun GameTestContext.assertReceivedSound(player: Player, sound: SoundEvent) {
    val soundPackets = packets.packetsOf<SoundEffectPacket>(player)
    val found = soundPackets.any { it.soundEvent() == sound }
    if (!found) {
        throw GameTestFailure("expected ${player.username} to receive sound ${sound.key()} but none matched (${soundPackets.size} sound packets captured)")
    }
}

fun GameTestContext.assertReceivedParticle(player: Player) {
    val particles = packets.packetsOf<ParticlePacket>(player)
    if (particles.isEmpty()) {
        throw GameTestFailure("expected ${player.username} to receive a particle effect but no ParticlePacket was captured")
    }
}
