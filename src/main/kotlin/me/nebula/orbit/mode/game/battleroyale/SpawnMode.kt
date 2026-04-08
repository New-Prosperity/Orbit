package me.nebula.orbit.mode.game.battleroyale

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.entitymount.EntityMountManager
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
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
        SpawnMode.HUNGER_GAMES -> executeRing(config.ringRadius, players, instance, center, mapRadius, onPlayerReady)
        SpawnMode.EXTENDED_HUNGER_GAMES -> executeRing(config.extendedRingRadius, players, instance, center, mapRadius, onPlayerReady)
        SpawnMode.RANDOM -> executeRandom(config, players, instance, center, mapRadius, onPlayerReady)
        SpawnMode.BATTLE_ROYALE -> executeBus(config, players, instance, center, mapRadius, onPlayerReady, onBusComplete)
    }

    private fun executeRing(
        radius: Double,
        players: List<Player>,
        instance: Instance,
        center: Pos,
        mapRadius: Int,
        onPlayerReady: (Player, Pos) -> Unit,
    ): SpawnModeResult {
        if (players.isEmpty()) return SpawnModeResult(pvpBlocked = false)
        val effectiveRadius = radius.coerceAtMost(mapRadius - 10.0)
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
        val effectiveRadius = mapRadius - 20
        val placed = mutableListOf<Pos>()
        val shuffled = players.shuffled()

        for (player in shuffled) {
            var pos: Pos? = null
            for (attempt in 0 until 200) {
                val angle = rng.nextDouble(2 * Math.PI)
                val dist = rng.nextDouble(30.0, effectiveRadius.toDouble())
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
                val fallbackDist = rng.nextDouble(30.0, effectiveRadius.toDouble())
                val x = center.x() + cos(fallbackAngle) * fallbackDist
                val z = center.z() + sin(fallbackAngle) * fallbackDist
                val height = findSurfaceHeight(instance, x.toInt(), z.toInt()).coerceAtLeast(64)
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
        val effectiveRadius = mapRadius - 10.0
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
        var traveled = 0.0

        val node = EventNode.all("br-bus-dismount")
        node.addListener(PlayerPacketEvent::class.java) { event ->
            val packet = event.packet
            if (packet !is ClientInputPacket || !packet.shift()) return@addListener
            val player = event.player
            if (player.uuid in ejected) return@addListener
            if (!EntityMountManager.isMounted(player)) return@addListener
            ejectFromBus(player, config, ejected)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)

        val busTask = repeat(1) {
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

            val newPos = Pos(
                startX + dirX * (traveled / config.busSpeed),
                config.busHeight,
                startZ + dirZ * (traveled / config.busSpeed),
            )
            bus.teleport(newPos)
        }

        return SpawnModeResult(
            pvpBlocked = true,
            busEntity = bus,
            busTask = busTask,
            dismountNode = node,
            ejectedPlayers = ejected,
        )
    }

    private fun ejectFromBus(player: Player, config: SpawnModeConfig, ejected: MutableSet<UUID>) {
        ejected.add(player.uuid)
        EntityMountManager.dismount(player)
        player.addEffect(Potion(PotionEffect.SLOW_FALLING, 0, config.parachuteDurationTicks))
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
