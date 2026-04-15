package me.nebula.orbit.mode.game.battleroyale

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.entitymount.EntityMountManager
import me.nebula.orbit.utils.particle.spawnParticle
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerFlag
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.other.ArmorStandMeta
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerPacketEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.client.play.ClientInputPacket
import net.minestom.server.particle.Particle
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class SpawnMode {
    HUNGER_GAMES,
    EXTENDED_HUNGER_GAMES,
    RANDOM,
    BATTLE_ROYALE,
}

data class SpawnModeConfig(
    val mode: SpawnMode = SpawnMode.HUNGER_GAMES,
    val ringRadius: Double = 80.0,
    val extendedRingRadius: Double = 200.0,
    val busHeight: Double = 150.0,
    val busSpeed: Double = 1.5,
    val parachuteDurationTicks: Int = 400,
    val randomMinDistance: Double = 20.0,
    val edgeMargin: Double = 10.0,
    val randomEdgeMargin: Double = 20.0,
    val maxSpawnAttempts: Int = 200,
    val fallbackSurfaceY: Int = 64,
    val spawnProtectionTicks: Int = 0,
    val busEjectArmTicks: Int = 60,
    val busRoutePreviewSteps: Int = 32,
)

data class SpawnModeResult(
    val pvpBlocked: Boolean,
    val busEntity: Entity? = null,
    val busTask: Task? = null,
    val dismountNode: EventNode<*>? = null,
    val ejectedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet(),
)

object SpawnModeExecutor {

    private val logger = logger("SpawnModeExecutor")

    fun execute(
        config: SpawnModeConfig,
        players: List<Player>,
        instance: Instance,
        center: Pos,
        mapRadius: Int,
        onPlayerReady: (Player, Pos) -> Unit,
        onBusComplete: (() -> Unit)? = null,
    ): SpawnModeResult = when (config.mode) {
        SpawnMode.HUNGER_GAMES -> executeRing(config, config.ringRadius, players, instance, center, mapRadius, onPlayerReady)
        SpawnMode.EXTENDED_HUNGER_GAMES -> executeRing(config, config.extendedRingRadius, players, instance, center, mapRadius, onPlayerReady)
        SpawnMode.RANDOM -> executeRandom(config, players, instance, center, mapRadius, onPlayerReady)
        SpawnMode.BATTLE_ROYALE -> executeBus(config, players, instance, center, mapRadius, onPlayerReady, onBusComplete)
    }

    private fun executeRing(
        config: SpawnModeConfig,
        radius: Double,
        players: List<Player>,
        instance: Instance,
        center: Pos,
        mapRadius: Int,
        onPlayerReady: (Player, Pos) -> Unit,
    ): SpawnModeResult {
        if (players.isEmpty()) return SpawnModeResult(pvpBlocked = false)
        val effectiveRadius = radius.coerceAtMost(mapRadius - config.edgeMargin)
        val angleStep = 2 * Math.PI / players.size
        val shuffled = players.shuffled()

        shuffled.forEachIndexed { index, player ->
            val angle = angleStep * index
            val x = center.x() + cos(angle) * effectiveRadius
            val z = center.z() + sin(angle) * effectiveRadius
            val height = findSurfaceHeight(instance, x.toInt(), z.toInt())
            val yaw = Math.toDegrees(-angle + Math.PI).toFloat()
            val pos = Pos(x, height + 1.0, z, yaw, 0f)
            onPlayerReady(player, pos)
        }

        return SpawnModeResult(pvpBlocked = false)
    }

    private fun executeRandom(
        config: SpawnModeConfig,
        players: List<Player>,
        instance: Instance,
        center: Pos,
        mapRadius: Int,
        onPlayerReady: (Player, Pos) -> Unit,
    ): SpawnModeResult {
        val rng = ThreadLocalRandom.current()
        val effectiveRadius = mapRadius - config.randomEdgeMargin.toInt()
        val placed = mutableListOf<Pos>()
        val shuffled = players.shuffled()

        for (player in shuffled) {
            var pos: Pos? = null
            for (attempt in 0 until config.maxSpawnAttempts) {
                val angle = rng.nextDouble(2 * Math.PI)
                val dist = rng.nextDouble(config.randomMinDistance, effectiveRadius.toDouble())
                val x = center.x() + cos(angle) * dist
                val z = center.z() + sin(angle) * dist
                val height = findSurfaceHeight(instance, x.toInt(), z.toInt())
                if (height < 1) continue
                val candidate = Pos(x, height + 1.0, z)
                val tooClose = placed.any { it.distance(candidate) < config.randomMinDistance }
                if (tooClose) continue
                val yaw = Math.toDegrees(-angle + Math.PI).toFloat()
                pos = candidate.withView(yaw, 0f)
                break
            }
            val spawnPos = pos ?: run {
                val fallbackAngle = rng.nextDouble(2 * Math.PI)
                val fallbackDist = rng.nextDouble(config.randomMinDistance, effectiveRadius.toDouble())
                val x = center.x() + cos(fallbackAngle) * fallbackDist
                val z = center.z() + sin(fallbackAngle) * fallbackDist
                val height = findSurfaceHeight(instance, x.toInt(), z.toInt()).coerceAtLeast(config.fallbackSurfaceY)
                Pos(x, height + 1.0, z, Math.toDegrees(-fallbackAngle + Math.PI).toFloat(), 0f)
            }
            placed.add(spawnPos)
            onPlayerReady(player, spawnPos)
        }

        return SpawnModeResult(pvpBlocked = false)
    }

    private fun executeBus(
        config: SpawnModeConfig,
        players: List<Player>,
        instance: Instance,
        center: Pos,
        mapRadius: Int,
        onPlayerReady: (Player, Pos) -> Unit,
        onBusComplete: (() -> Unit)?,
    ): SpawnModeResult {
        val rng = ThreadLocalRandom.current()
        val angle = rng.nextDouble(2 * Math.PI)
        val effectiveRadius = mapRadius - config.edgeMargin
        val startX = center.x() + cos(angle) * effectiveRadius
        val startZ = center.z() + sin(angle) * effectiveRadius
        val endX = center.x() - cos(angle) * effectiveRadius
        val endZ = center.z() - sin(angle) * effectiveRadius

        val busPos = Pos(startX, config.busHeight, startZ)
        val bus = Entity(EntityType.ARMOR_STAND)
        val meta = bus.entityMeta as ArmorStandMeta
        meta.setNotifyAboutChanges(false)
        meta.isInvisible = true
        meta.isSmall = true
        meta.isHasNoGravity = true
        meta.setNotifyAboutChanges(true)
        bus.setInstance(instance, busPos).join()

        val ejected = mutableSetOf<UUID>()

        for (player in players) {
            EntityMountManager.mount(player, bus)
            onPlayerReady(player, busPos)
        }

        val dx = endX - startX
        val dz = endZ - startZ
        val totalDist = sqrt(dx * dx + dz * dz)
        val dirX = dx / totalDist * config.busSpeed
        val dirZ = dz / totalDist * config.busSpeed
        val busYaw = Math.toDegrees(-atan2(dx, dz)).toFloat()
        var traveled = 0.0
        var tickCount = 0

        drawRoutePreview(instance, startX, startZ, endX, endZ, config)

        val node = EventNode.all("br-bus-dismount")
        node.addListener(PlayerPacketEvent::class.java) { event ->
            val packet = event.packet
            if (packet !is ClientInputPacket || !packet.shift()) return@addListener
            val player = event.player
            if (player.uuid in ejected) return@addListener
            if (!EntityMountManager.isMounted(player)) return@addListener
            if (tickCount < config.busEjectArmTicks) {
                player.sendActionBar(miniMessage.deserialize("<red>Eject is not yet armed"))
                return@addListener
            }
            ejectFromBus(player, config, ejected)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)

        val busTask = repeat(1) {
            tickCount++
            traveled += config.busSpeed
            if (traveled >= totalDist) {
                for (player in players) {
                    if (player.uuid !in ejected && EntityMountManager.isMounted(player)) {
                        ejectFromBus(player, config, ejected)
                    }
                }
                bus.remove()
                onBusComplete?.invoke()
                return@repeat
            }

            if (ejected.size >= players.size) {
                bus.remove()
                onBusComplete?.invoke()
                return@repeat
            }

            val progress = (traveled / totalDist * 100).toInt().coerceIn(0, 100)
            val newPos = Pos(
                startX + dirX * (traveled / config.busSpeed),
                config.busHeight,
                startZ + dirZ * (traveled / config.busSpeed),
                busYaw,
                0f,
            )
            bus.refreshPosition(newPos)
            bus.velocity = Vec(dirX * ServerFlag.SERVER_TICKS_PER_SECOND, 0.0, dirZ * ServerFlag.SERVER_TICKS_PER_SECOND)

            for (player in players) {
                if (player.uuid !in ejected) {
                    val hint = if (tickCount < config.busEjectArmTicks) {
                        val secondsLeft = ((config.busEjectArmTicks - tickCount) / 20) + 1
                        "<gray>Eject arms in <white>${secondsLeft}s <dark_gray>| <white>$progress%"
                    } else {
                        "<yellow><bold>SHIFT</bold> <gray>to jump <dark_gray>| <white>$progress%"
                    }
                    player.sendActionBar(miniMessage.deserialize(hint))
                }
            }
        }

        return SpawnModeResult(
            pvpBlocked = true,
            busEntity = bus,
            busTask = busTask,
            dismountNode = node,
            ejectedPlayers = ejected,
        )
    }

    private fun drawRoutePreview(
        instance: Instance,
        startX: Double,
        startZ: Double,
        endX: Double,
        endZ: Double,
        config: SpawnModeConfig,
    ) {
        val steps = config.busRoutePreviewSteps.coerceAtLeast(2)
        val dx = (endX - startX) / (steps - 1)
        val dz = (endZ - startZ) / (steps - 1)
        for (player in instance.players) {
            for (i in 0 until steps) {
                val x = startX + dx * i
                val z = startZ + dz * i
                player.spawnParticle(Particle.END_ROD, Pos(x, config.busHeight, z), count = 1, spread = 0f, speed = 0f)
            }
        }
    }

    private fun ejectFromBus(player: Player, config: SpawnModeConfig, ejected: MutableSet<UUID>) {
        ejected.add(player.uuid)
        EntityMountManager.dismount(player)
        player.addEffect(Potion(PotionEffect.SLOW_FALLING, 0, config.parachuteDurationTicks))
        if (config.spawnProtectionTicks > 0) {
            player.addEffect(Potion(PotionEffect.RESISTANCE, 4, config.spawnProtectionTicks))
            player.addEffect(Potion(PotionEffect.GLOWING, 0, config.spawnProtectionTicks))
        }
    }

    fun cleanup(result: SpawnModeResult) {
        result.busTask?.cancel()
        result.busEntity?.remove()
        result.dismountNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        result.ejectedPlayers.clear()
    }

    private fun findSurfaceHeight(instance: Instance, x: Int, z: Int): Int {
        val dim = instance.cachedDimensionType
        for (y in (dim.maxY() - 1) downTo dim.minY()) {
            val block = instance.getBlock(x, y, z)
            if (!block.isAir && block != Block.WATER && block != Block.LAVA) return y
        }
        return 64
    }
}
