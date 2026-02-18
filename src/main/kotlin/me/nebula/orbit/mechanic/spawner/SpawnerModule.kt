package me.nebula.orbit.mechanic.spawner

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.utils.blockindex.BlockPositionIndex
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private data class SpawnerKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val SPAWNABLE_TYPES = listOf(
    EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
    EntityType.CAVE_SPIDER, EntityType.BLAZE, EntityType.SILVERFISH,
)

class SpawnerModule : OrbitModule("spawner") {

    private val index = BlockPositionIndex(setOf("minecraft:spawner"), eventNode).install()
    private val spawnerEntities = ConcurrentHashMap<SpawnerKey, MutableList<Entity>>()

    override fun onEnable() {
        super.onEnable()

        index.instancePositions.cleanOnInstanceRemove { it }
        spawnerEntities.cleanOnInstanceRemove { it.instanceHash }

        MinecraftServer.getSchedulerManager().buildTask {
            for (instance in MinecraftServer.getInstanceManager().instances) {
                val hash = System.identityHashCode(instance)

                for (player in instance.players) {
                    val nearby = index.positionsNear(instance, player.position.asVec(), 16.0)

                    for (vec in nearby) {
                        val bx = vec.x().toInt()
                        val by = vec.y().toInt()
                        val bz = vec.z().toInt()
                        val key = SpawnerKey(hash, bx, by, bz)

                        val entities = spawnerEntities.getOrPut(key) { mutableListOf() }
                        entities.removeAll { it.isRemoved }
                        if (entities.size >= 4) continue

                        val entityType = SPAWNABLE_TYPES[
                            ((bx * 31 + by * 17 + bz * 13) and Int.MAX_VALUE) % SPAWNABLE_TYPES.size
                        ]

                        val spawnPos = Vec(
                            bx + 0.5 + (Math.random() - 0.5) * 4.0,
                            by.toDouble(),
                            bz + 0.5 + (Math.random() - 0.5) * 4.0,
                        )

                        val entity = Entity(entityType)
                        entity.setInstance(instance, spawnPos)
                        entities.add(entity)
                    }
                }
            }
        }.repeat(TaskSchedule.tick(400)).schedule()
    }

    override fun onDisable() {
        spawnerEntities.values.forEach { entities ->
            entities.forEach { if (!it.isRemoved) it.remove() }
        }
        spawnerEntities.clear()
        index.clear()
        super.onDisable()
    }
}
