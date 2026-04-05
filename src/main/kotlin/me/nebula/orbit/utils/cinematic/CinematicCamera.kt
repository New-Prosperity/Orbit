package me.nebula.orbit.utils.cinematic

import me.nebula.orbit.utils.modelengine.animation.KeyframeInterpolator
import me.nebula.orbit.utils.modelengine.blueprint.InterpolationType
import me.nebula.orbit.utils.modelengine.blueprint.Keyframe
import me.nebula.orbit.utils.modelengine.math.Quat
import me.nebula.orbit.utils.modelengine.math.eulerToQuat
import me.nebula.orbit.utils.modelengine.math.quatSlerp
import me.nebula.orbit.utils.modelengine.math.quatToEuler
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.network.packet.server.play.CameraPacket
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket
import net.minestom.server.network.packet.server.play.EntityHeadLookPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.EntityTeleportPacket
import net.minestom.server.network.packet.server.play.SpawnEntityPacket
import net.minestom.server.timer.Task
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.atan2
import kotlin.math.sqrt

private val nextCameraEntityId = AtomicInteger(-4_000_000)
private const val META_FLAGS = 0
private const val META_NO_GRAVITY = 5
private const val META_ARMORSTAND_FLAGS = 15

data class CinematicNode(
    val time: Float,
    val position: Vec,
    val yaw: Float,
    val pitch: Float,
    val interpolation: InterpolationType = InterpolationType.CATMULLROM,
)

data class CinematicSequence(
    val nodes: List<CinematicNode>,
    val loop: Boolean,
    val lookAt: (() -> Vec)?,
    val onComplete: (() -> Unit)?,
    val onTick: ((Player, Float) -> Unit)?,
    val speed: Float,
    val hideHud: Boolean,
    val fadeIn: Int,
    val fadeOut: Int,
) {
    val duration: Float get() = nodes.lastOrNull()?.time ?: 0f
}

class CinematicSession(
    val playerUuid: UUID,
    val sequence: CinematicSequence,
    val previousGameMode: GameMode,
    val startPosition: Pos,
    val positionInterpolator: KeyframeInterpolator,
    val rotationQuats: List<Pair<Float, Quat>>,
    val cameraEntityId: Int,
    val cameraEntityUuid: UUID,
) {
    @Volatile var time: Float = 0f
    @Volatile var task: Task? = null
}

object CinematicCamera {

    private val sessions = ConcurrentHashMap<UUID, CinematicSession>()
    private const val TICK_SECONDS = 1f / 20f
    private val installed = AtomicBoolean(false)

    fun play(player: Player, sequence: CinematicSequence) {
        require(sequence.nodes.size >= 2) { "Cinematic requires at least 2 nodes" }

        if (installed.compareAndSet(false, true)) {
            install(MinecraftServer.getGlobalEventHandler())
        }

        stop(player)

        val posKeyframes = sequence.nodes.map { node ->
            Keyframe(time = node.time, value = node.position, interpolation = node.interpolation)
        }

        val rotQuats = sequence.nodes.map { node ->
            node.time to eulerToQuat(node.pitch, node.yaw, 0f)
        }

        val startNode = sequence.nodes.first()
        val startPos = Pos(
            startNode.position.x(), startNode.position.y(), startNode.position.z(),
            startNode.yaw, startNode.pitch,
        )

        val entityId = nextCameraEntityId.getAndDecrement()
        val entityUuid = UUID.randomUUID()

        val session = CinematicSession(
            playerUuid = player.uuid,
            sequence = sequence,
            previousGameMode = player.gameMode,
            startPosition = player.position,
            positionInterpolator = KeyframeInterpolator(posKeyframes),
            rotationQuats = rotQuats,
            cameraEntityId = entityId,
            cameraEntityUuid = entityUuid,
        )

        sessions[player.uuid] = session

        player.sendPacket(SpawnEntityPacket(
            entityId, entityUuid, EntityType.ARMOR_STAND,
            startPos, startNode.yaw, 0, Vec.ZERO,
        ))
        player.sendPacket(EntityMetaDataPacket(entityId, mapOf(
            META_FLAGS to Metadata.Byte(0x20),
            META_NO_GRAVITY to Metadata.Boolean(true),
            META_ARMORSTAND_FLAGS to Metadata.Byte(0x10),
        )))

        player.gameMode = GameMode.SPECTATOR

        if (sequence.hideHud) {
            player.setReducedDebugScreenInformation(true)
        }

        if (sequence.fadeIn > 0) {
            player.sendTitlePart(TitlePart.TIMES, Title.Times.times(
                Duration.ofMillis(sequence.fadeIn.toLong() * 50),
                Duration.ZERO,
                Duration.ZERO,
            ))
            player.sendTitlePart(TitlePart.TITLE, Component.empty())
        }

        player.sendPacket(CameraPacket(entityId))

        session.task = repeat(1) {
            val current = sessions[player.uuid] ?: return@repeat
            tick(current, player)
        }
    }

    fun stop(player: Player) {
        val session = sessions.remove(player.uuid) ?: return
        session.task?.cancel()

        if (session.sequence.fadeOut > 0) {
            player.sendTitlePart(TitlePart.TIMES, Title.Times.times(
                Duration.ZERO,
                Duration.ZERO,
                Duration.ofMillis(session.sequence.fadeOut.toLong() * 50),
            ))
            player.sendTitlePart(TitlePart.TITLE, Component.empty())
        }

        player.sendPacket(CameraPacket(player.entityId))
        player.sendPacket(DestroyEntitiesPacket(listOf(session.cameraEntityId)))
        player.gameMode = session.previousGameMode
        player.teleport(session.startPosition)

        if (session.sequence.hideHud) {
            player.setReducedDebugScreenInformation(false)
        }

        session.sequence.onComplete?.invoke()
    }

    fun isPlaying(player: Player): Boolean = sessions.containsKey(player.uuid)

    fun install(eventNode: EventNode<Event>) {
        installed.set(true)
        eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
            stop(event.player)
        }
    }

    fun stopAll() {
        for ((uuid, session) in sessions) {
            session.task?.cancel()
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            if (player != null) {
                player.sendPacket(CameraPacket(player.entityId))
                player.sendPacket(DestroyEntitiesPacket(listOf(session.cameraEntityId)))
                player.gameMode = session.previousGameMode
                player.teleport(session.startPosition)
                if (session.sequence.hideHud) player.setReducedDebugScreenInformation(false)
            }
        }
        sessions.clear()
    }

    private fun tick(session: CinematicSession, player: Player) {
        session.time += TICK_SECONDS * session.sequence.speed

        val pos = session.positionInterpolator.evaluate(session.time) ?: return

        val yaw: Float
        val pitch: Float

        val lookAt = session.sequence.lookAt
        if (lookAt != null) {
            val target = lookAt()
            val dx = target.x() - pos.x()
            val dy = target.y() - pos.y()
            val dz = target.z() - pos.z()
            yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
            pitch = Math.toDegrees(-atan2(dy, sqrt(dx * dx + dz * dz))).toFloat()
        } else {
            val (p, y, _) = evaluateRotation(session.rotationQuats, session.time)
            yaw = y
            pitch = p
        }

        player.sendPacket(EntityTeleportPacket(
            session.cameraEntityId,
            Pos(pos.x(), pos.y(), pos.z(), yaw, pitch),
            Vec.ZERO, 0, false,
        ))
        player.sendPacket(EntityHeadLookPacket(session.cameraEntityId, yaw))

        session.sequence.onTick?.invoke(player, session.time / session.sequence.duration)

        if (session.time >= session.sequence.duration) {
            if (session.sequence.loop) {
                session.time = 0f
            } else {
                stop(player)
            }
        }
    }

    private fun evaluateRotation(quats: List<Pair<Float, Quat>>, time: Float): Triple<Float, Float, Float> {
        if (quats.size == 1) return quatToEuler(quats[0].second)
        if (time <= quats.first().first) return quatToEuler(quats.first().second)
        if (time >= quats.last().first) return quatToEuler(quats.last().second)

        val index = findSegmentIndex(quats, time)
        val (t0, q0) = quats[index]
        val (t1, q1) = quats[index + 1]
        val dt = t1 - t0
        val t = if (dt > 0f) ((time - t0) / dt).coerceIn(0f, 1f) else 0f

        return quatToEuler(quatSlerp(q0, q1, t))
    }

    private fun findSegmentIndex(quats: List<Pair<Float, Quat>>, time: Float): Int {
        var low = 0
        var high = quats.size - 2
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (quats[mid].first <= time) low = mid else high = mid - 1
        }
        return low
    }
}

class CinematicBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val nodes = mutableListOf<CinematicNode>()
    @PublishedApi internal var loop = false
    @PublishedApi internal var lookAt: (() -> Vec)? = null
    @PublishedApi internal var onComplete: (() -> Unit)? = null
    @PublishedApi internal var onTick: ((Player, Float) -> Unit)? = null
    @PublishedApi internal var speed: Float = 1f
    @PublishedApi internal var hideHud: Boolean = false
    @PublishedApi internal var fadeIn: Int = 0
    @PublishedApi internal var fadeOut: Int = 0

    fun node(time: Float, position: Pos, interpolation: InterpolationType = InterpolationType.CATMULLROM) {
        nodes += CinematicNode(time, position.asVec(), position.yaw(), position.pitch(), interpolation)
    }

    fun node(
        time: Float,
        x: Double, y: Double, z: Double,
        yaw: Float = 0f, pitch: Float = 0f,
        interpolation: InterpolationType = InterpolationType.CATMULLROM,
    ) {
        nodes += CinematicNode(time, Vec(x, y, z), yaw, pitch, interpolation)
    }

    fun loop() { loop = true }
    fun speed(value: Float) { speed = value }
    fun hideHud() { hideHud = true }
    fun fadeIn(ticks: Int) { fadeIn = ticks }
    fun fadeOut(ticks: Int) { fadeOut = ticks }
    fun fade(inTicks: Int, outTicks: Int) { fadeIn = inTicks; fadeOut = outTicks }

    fun lookAt(position: Vec) { lookAt = { position } }
    fun lookAt(entity: Entity) { lookAt = { entity.position.asVec() } }
    fun lookAt(supplier: () -> Vec) { lookAt = supplier }

    fun onComplete(action: () -> Unit) { onComplete = action }
    fun onTick(action: (Player, Float) -> Unit) { onTick = action }

    @PublishedApi internal fun build(): CinematicSequence {
        require(nodes.size >= 2) { "Cinematic requires at least 2 nodes" }
        return CinematicSequence(
            nodes = nodes.sortedBy { it.time },
            loop = loop,
            lookAt = lookAt,
            onComplete = onComplete,
            onTick = onTick,
            speed = speed,
            hideHud = hideHud,
            fadeIn = fadeIn,
            fadeOut = fadeOut,
        )
    }
}

inline fun cinematic(player: Player, block: CinematicBuilder.() -> Unit) {
    CinematicCamera.play(player, CinematicBuilder().apply(block).build())
}

fun Player.playCinematic(block: CinematicBuilder.() -> Unit) = cinematic(this, block)
fun Player.stopCinematic() = CinematicCamera.stop(this)
val Player.isInCinematic: Boolean get() = CinematicCamera.isPlaying(this)
