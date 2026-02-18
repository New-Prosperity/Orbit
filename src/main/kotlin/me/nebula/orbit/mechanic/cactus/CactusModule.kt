package me.nebula.orbit.mechanic.cactus

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class CactusKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private const val MAX_CACTUS_HEIGHT = 3

class CactusModule : OrbitModule("cactus") {

    private val cactusBases = ConcurrentHashMap.newKeySet<CactusKey>()
    private val lastDamageTag = Tag.Long("mechanic:cactus:last_damage")

    override fun onEnable() {
        super.onEnable()
        cactusBases.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val pos = player.position

            val checkPositions = listOf(
                pos, pos.add(0.3, 0.0, 0.0), pos.add(-0.3, 0.0, 0.0),
                pos.add(0.0, 0.0, 0.3), pos.add(0.0, 0.0, -0.3),
                pos.add(0.0, 1.0, 0.0),
            )

            val touchingCactus = checkPositions.any { instance.getBlock(it).name() == "minecraft:cactus" }
            if (!touchingCactus) return@addListener

            val now = System.currentTimeMillis()
            val lastDamage = player.getTag(lastDamageTag) ?: 0L
            if (now - lastDamage < 500L) return@addListener

            player.damage(Damage(DamageType.CACTUS, null, null, null, 1f))
            player.setTag(lastDamageTag, now)
        }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:cactus") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val below = instance.getBlock(pos.add(0.0, -1.0, 0.0))
            if (below.name() != "minecraft:cactus" && below.name() != "minecraft:sand" && below.name() != "minecraft:red_sand") {
                event.isCancelled = true
                return@addListener
            }
            val key = CactusKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            cactusBases.add(key)
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = cactusBases.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (Random.nextFloat() > 0.01f) continue

                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }

                var topY = key.y
                while (instance.getBlock(key.x, topY + 1, key.z).name() == "minecraft:cactus") topY++

                if (topY - key.y >= MAX_CACTUS_HEIGHT - 1) continue
                if (instance.getBlock(key.x, topY + 1, key.z) != Block.AIR) continue

                val adjacentClear = listOf(
                    instance.getBlock(key.x + 1, topY + 1, key.z),
                    instance.getBlock(key.x - 1, topY + 1, key.z),
                    instance.getBlock(key.x, topY + 1, key.z + 1),
                    instance.getBlock(key.x, topY + 1, key.z - 1),
                ).all { !it.isSolid }

                if (adjacentClear) {
                    instance.setBlock(key.x, topY + 1, key.z, Block.CACTUS)
                }
            }
        }.repeat(TaskSchedule.tick(60)).schedule()
    }

    override fun onDisable() {
        cactusBases.clear()
        super.onDisable()
    }
}
