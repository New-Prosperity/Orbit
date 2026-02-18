package me.nebula.orbit.mechanic.beacon

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.utils.blockindex.BlockPositionIndex
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val BEACON_BASE_BLOCKS = setOf(
    "minecraft:iron_block",
    "minecraft:gold_block",
    "minecraft:emerald_block",
    "minecraft:diamond_block",
    "minecraft:netherite_block",
)

private val TIER_EFFECTS = listOf(
    listOf(PotionEffect.SPEED, PotionEffect.HASTE),
    listOf(PotionEffect.RESISTANCE, PotionEffect.JUMP_BOOST),
    listOf(PotionEffect.STRENGTH),
    listOf(PotionEffect.REGENERATION),
)

class BeaconModule : OrbitModule("beacon") {

    private val index = BlockPositionIndex(setOf("minecraft:beacon"), eventNode).install()
    private val tierCache = ConcurrentHashMap<Long, Int>()
    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        index.instancePositions.cleanOnInstanceRemove { it }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            invalidateTierCacheNear(event.blockPosition.blockX(), event.blockPosition.blockY(), event.blockPosition.blockZ())
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            invalidateTierCacheNear(event.blockPosition.blockX(), event.blockPosition.blockY(), event.blockPosition.blockZ())
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.seconds(4))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        tierCache.clear()
        index.clear()
        super.onDisable()
    }

    private fun tick() {
        MinecraftServer.getInstanceManager().instances.forEach { instance ->
            instance.players
                .filter { it.gameMode != GameMode.SPECTATOR }
                .forEach { player -> processBeacons(instance, player) }
        }
    }

    private fun processBeacons(instance: Instance, player: Player) {
        val playerPos = player.position.asVec()
        val nearby = index.positionsNear(instance, playerPos, 50.0)
        for (vec in nearby) {
            val bx = vec.x().toInt()
            val by = vec.y().toInt()
            val bz = vec.z().toInt()

            val packed = BlockPositionIndex.pack(bx, by, bz)
            val tier = tierCache.computeIfAbsent(packed) { calculateTier(instance, bx, by, bz) }
            if (tier <= 0) continue

            val range = 10.0 + tier * 10.0
            val beaconCenter = Vec(bx + 0.5, by.toDouble(), bz + 0.5)
            if (playerPos.distance(beaconCenter) <= range) {
                applyEffects(player, tier)
                return
            }
        }
    }

    private fun calculateTier(instance: Instance, bx: Int, by: Int, bz: Int): Int {
        var tier = 0
        for (layer in 1..4) {
            val y = by - layer
            var complete = true
            for (x in bx - layer..bx + layer) {
                for (z in bz - layer..bz + layer) {
                    if (instance.getBlock(x, y, z).name() !in BEACON_BASE_BLOCKS) {
                        complete = false
                        break
                    }
                }
                if (!complete) break
            }
            if (complete) tier = layer else break
        }
        return tier
    }

    private fun invalidateTierCacheNear(x: Int, y: Int, z: Int) {
        tierCache.keys.removeIf { packed ->
            val (bx, by, bz) = BlockPositionIndex.unpack(packed)
            val dx = bx - x
            val dy = by - y
            val dz = bz - z
            dx in -4..4 && dy in -4..4 && dz in -4..4
        }
    }

    private fun applyEffects(player: Player, tier: Int) {
        for (t in 0 until tier.coerceAtMost(TIER_EFFECTS.size)) {
            TIER_EFFECTS[t].forEach { effect ->
                player.addEffect(Potion(effect, 0, 260))
            }
        }
    }
}
