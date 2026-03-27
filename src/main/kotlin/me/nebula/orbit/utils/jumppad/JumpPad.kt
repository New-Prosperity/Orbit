package me.nebula.orbit.utils.jumppad

import me.nebula.orbit.utils.cooldown.Cooldown
import me.nebula.orbit.utils.sound.playSound
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

sealed interface LaunchMode {
    data class Fixed(val velocity: Vec) : LaunchMode
    data class Forward(val power: Double, val upward: Double) : LaunchMode
}

data class JumpPad(
    val position: Pos,
    val launchMode: LaunchMode,
    val sound: SoundEvent = SoundEvent.ENTITY_FIREWORK_ROCKET_LAUNCH,
    val particle: Particle = Particle.FIREWORK,
    val cooldownTicks: Int = 10,
    val triggerRadius: Double = 0.8,
)

object JumpPadManager {

    private val pads = ConcurrentHashMap<String, JumpPadEntry>()
    private val instanceListeners = ConcurrentHashMap<Int, EventNode<*>>()

    private data class JumpPadEntry(
        val pad: JumpPad,
        val instanceHash: Int,
        val cooldown: Cooldown<UUID>,
    )

    fun register(pad: JumpPad, instance: Instance): String {
        val hash = System.identityHashCode(instance)
        val id = "$hash-${pad.position.x()}-${pad.position.y()}-${pad.position.z()}"
        val cooldown = Cooldown<UUID>(Duration.ofMillis(pad.cooldownTicks * 50L))
        pads[id] = JumpPadEntry(pad, hash, cooldown)
        ensureListener(instance)
        return id
    }

    fun unregister(id: String) {
        pads.remove(id)
    }

    fun unregisterAll(instance: Instance) {
        val hash = System.identityHashCode(instance)
        val prefix = "$hash-"
        pads.keys.removeAll { it.startsWith(prefix) }
        instanceListeners.remove(hash)?.let { node ->
            MinecraftServer.getGlobalEventHandler().removeChild(node)
        }
    }

    fun clear() {
        instanceListeners.values.forEach { node ->
            MinecraftServer.getGlobalEventHandler().removeChild(node)
        }
        pads.clear()
        instanceListeners.clear()
    }

    private fun ensureListener(instance: Instance) {
        val hash = System.identityHashCode(instance)
        instanceListeners.computeIfAbsent(hash) {
            val node = EventNode.all("jumppad-$hash")
            node.addListener(PlayerMoveEvent::class.java) { event ->
                handleMove(event.player)
            }
            MinecraftServer.getGlobalEventHandler().addChild(node)
            node
        }
    }

    private fun handleMove(player: Player) {
        val playerPos = player.position
        val playerInstanceHash = player.instance?.let { System.identityHashCode(it) } ?: return
        for ((_, entry) in pads) {
            if (entry.instanceHash != playerInstanceHash) continue
            val pad = entry.pad
            val dx = playerPos.x() - pad.position.x()
            val dy = playerPos.y() - pad.position.y()
            val dz = playerPos.z() - pad.position.z()
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq > pad.triggerRadius * pad.triggerRadius) continue
            if (!entry.cooldown.tryUse(player.uuid)) continue
            launch(player, pad)
            return
        }
    }

    private fun launch(player: Player, pad: JumpPad) {
        val velocity = when (val mode = pad.launchMode) {
            is LaunchMode.Fixed -> mode.velocity
            is LaunchMode.Forward -> {
                val yaw = Math.toRadians(player.position.yaw().toDouble())
                Vec(
                    -sin(yaw) * mode.power,
                    mode.upward,
                    cos(yaw) * mode.power,
                )
            }
        }
        player.velocity = velocity.mul(8000.0 / 20.0)
        player.playSound(pad.sound)
        player.instance?.sendGroupedPacket(
            ParticlePacket(
                pad.particle, pad.position.x(), pad.position.y() + 0.5, pad.position.z(),
                0.3f, 0.2f, 0.3f, 0.05f, 10
            )
        )
    }
}

class JumpPadBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var position: Pos = Pos.ZERO
    @PublishedApi internal var launchMode: LaunchMode? = null
    @PublishedApi internal var sound: SoundEvent = SoundEvent.ENTITY_FIREWORK_ROCKET_LAUNCH
    @PublishedApi internal var particle: Particle = Particle.FIREWORK
    @PublishedApi internal var cooldownTicks: Int = 10
    @PublishedApi internal var triggerRadius: Double = 0.8

    fun position(pos: Pos) { position = pos }
    fun velocity(vec: Vec) { launchMode = LaunchMode.Fixed(vec) }
    fun launchForward(power: Double = 2.0, upward: Double = 1.0) { launchMode = LaunchMode.Forward(power, upward) }
    fun sound(event: SoundEvent) { sound = event }
    fun particle(p: Particle) { particle = p }
    fun cooldown(ticks: Int) { cooldownTicks = ticks }
    fun triggerRadius(radius: Double) { triggerRadius = radius }

    @PublishedApi internal fun build(): JumpPad {
        val mode = requireNotNull(launchMode) { "JumpPad requires a velocity or launchForward" }
        return JumpPad(
            position = position,
            launchMode = mode,
            sound = sound,
            particle = particle,
            cooldownTicks = cooldownTicks,
            triggerRadius = triggerRadius,
        )
    }
}

inline fun jumpPad(block: JumpPadBuilder.() -> Unit): JumpPad =
    JumpPadBuilder().apply(block).build()
