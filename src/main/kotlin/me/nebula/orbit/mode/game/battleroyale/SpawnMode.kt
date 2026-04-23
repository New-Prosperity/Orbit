package me.nebula.orbit.mode.game.battleroyale

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.mode.game.battleroyale.spawn.SpawnContext
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class SpawnModeConfig(
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
    val podHeight: Double = 180.0,
    val podSpacingRadius: Double = 140.0,
    val teamClusterSpacing: Double = 4.0,
    val teamClusterRadius: Double = 160.0,
    val themedRingFacingInward: Boolean = true,
)

data class SpawnModeResult(
    val pvpBlocked: Boolean,
    val busEntity: Entity? = null,
    val busTask: Task? = null,
    val dismountNode: EventNode<*>? = null,
    val ejectedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet(),
    val immunityPlayers: Set<UUID> = emptySet(),
)

object SpawnModeExecutor {

    private val logger = logger("SpawnModeExecutor")

    fun ring(ctx: SpawnContext, radius: Double): SpawnModeResult {
        val players = ctx.players
        if (players.isEmpty()) return SpawnModeResult(pvpBlocked = false)
        val config = ctx.config
        val effectiveRadius = radius.coerceAtMost(ctx.mapRadius - config.edgeMargin)
        val angleStep = 2 * Math.PI / players.size
        val shuffled = players.shuffled(ctx.random.asJavaRandom())
        val immunities = mutableSetOf<UUID>()

        shuffled.forEachIndexed { index, player ->
            val angle = angleStep * index
            val x = ctx.center.x() + cos(angle) * effectiveRadius
            val z = ctx.center.z() + sin(angle) * effectiveRadius
            val height = findSurfaceHeight(ctx.instance, x.toInt(), z.toInt())
            val yaw = Math.toDegrees(-angle + Math.PI).toFloat()
            val pos = Pos(x, height + 1.0, z, yaw, 0f)
            ctx.onPlayerReady(player, pos)
            ctx.onImmunityGrant(player.uuid, pos)
            immunities += player.uuid
        }

        return SpawnModeResult(pvpBlocked = false, immunityPlayers = immunities)
    }

    fun random(ctx: SpawnContext): SpawnModeResult {
        val config = ctx.config
        val effectiveRadius = ctx.mapRadius - config.randomEdgeMargin.toInt()
        val placed = mutableListOf<Pos>()
        val shuffled = ctx.players.shuffled(ctx.random.asJavaRandom())
        val immunities = mutableSetOf<UUID>()

        for (player in shuffled) {
            var pos: Pos? = null
            for (attempt in 0 until config.maxSpawnAttempts) {
                val angle = ctx.random.nextDouble() * 2 * Math.PI
                val dist = config.randomMinDistance + ctx.random.nextDouble() * (effectiveRadius - config.randomMinDistance)
                val x = ctx.center.x() + cos(angle) * dist
                val z = ctx.center.z() + sin(angle) * dist
                val height = findSurfaceHeight(ctx.instance, x.toInt(), z.toInt())
                if (height < 1) continue
                val candidate = Pos(x, height + 1.0, z)
                val tooClose = placed.any { it.distance(candidate) < config.randomMinDistance }
                if (tooClose) continue
                val yaw = Math.toDegrees(-angle + Math.PI).toFloat()
                pos = candidate.withView(yaw, 0f)
                break
            }
            val spawnPos = pos ?: run {
                val fallbackAngle = ctx.random.nextDouble() * 2 * Math.PI
                val fallbackDist = config.randomMinDistance + ctx.random.nextDouble() * (effectiveRadius - config.randomMinDistance)
                val x = ctx.center.x() + cos(fallbackAngle) * fallbackDist
                val z = ctx.center.z() + sin(fallbackAngle) * fallbackDist
                val height = findSurfaceHeight(ctx.instance, x.toInt(), z.toInt()).coerceAtLeast(config.fallbackSurfaceY)
                Pos(x, height + 1.0, z, Math.toDegrees(-fallbackAngle + Math.PI).toFloat(), 0f)
            }
            placed.add(spawnPos)
            ctx.onPlayerReady(player, spawnPos)
            ctx.onImmunityGrant(player.uuid, spawnPos)
            immunities += player.uuid
        }

        return SpawnModeResult(pvpBlocked = false, immunityPlayers = immunities)
    }

    fun bus(ctx: SpawnContext): SpawnModeResult {
        val config = ctx.config
        val angle = ctx.random.nextDouble() * 2 * Math.PI
        val effectiveRadius = ctx.mapRadius - config.edgeMargin
        val startX = ctx.center.x() + cos(angle) * effectiveRadius
        val startZ = ctx.center.z() + sin(angle) * effectiveRadius
        val endX = ctx.center.x() - cos(angle) * effectiveRadius
        val endZ = ctx.center.z() - sin(angle) * effectiveRadius

        val busPos = Pos(startX, config.busHeight, startZ)
        val bus = Entity(EntityType.ARMOR_STAND)
        val meta = bus.entityMeta as ArmorStandMeta
        meta.setNotifyAboutChanges(false)
        meta.isInvisible = true
        meta.isSmall = true
        meta.isHasNoGravity = true
        meta.setNotifyAboutChanges(true)
        bus.setInstance(ctx.instance, busPos).join()

        val ejected = mutableSetOf<UUID>()

        for (player in ctx.players) {
            EntityMountManager.mount(player, bus)
            ctx.onPlayerReady(player, busPos)
        }

        val dx = endX - startX
        val dz = endZ - startZ
        val totalDist = sqrt(dx * dx + dz * dz)
        val dirX = dx / totalDist * config.busSpeed
        val dirZ = dz / totalDist * config.busSpeed
        val busYaw = Math.toDegrees(-atan2(dx, dz)).toFloat()
        var traveled = 0.0
        var tickCount = 0

        drawRoutePreview(ctx.instance, startX, startZ, endX, endZ, config)

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
            ctx.onImmunityGrant(player.uuid, player.position)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)

        val busTask = repeat(1) {
            tickCount++
            traveled += config.busSpeed
            if (traveled >= totalDist) {
                for (player in ctx.players) {
                    if (player.uuid !in ejected && EntityMountManager.isMounted(player)) {
                        ejectFromBus(player, config, ejected)
                        ctx.onImmunityGrant(player.uuid, player.position)
                    }
                }
                bus.remove()
                ctx.onComplete?.invoke()
                return@repeat
            }

            if (ejected.size >= ctx.players.size) {
                bus.remove()
                ctx.onComplete?.invoke()
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

            for (player in ctx.players) {
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

    fun findSurfaceHeight(instance: Instance, x: Int, z: Int): Int {
        val dim = instance.cachedDimensionType
        for (y in (dim.maxY() - 1) downTo dim.minY()) {
            val block = instance.getBlock(x, y, z)
            if (!block.isAir && block != Block.WATER && block != Block.LAVA) return y
        }
        return 64
    }
}

internal fun Random.asJavaRandom(): java.util.Random = object : java.util.Random() {
    override fun next(bits: Int): Int = this@asJavaRandom.nextBits(bits)
}
