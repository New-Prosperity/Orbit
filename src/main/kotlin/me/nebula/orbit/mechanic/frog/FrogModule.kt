package me.nebula.orbit.mechanic.frog

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

private val FROG_VARIANT_TAG = Tag.String("mechanic:frog:variant").defaultValue("temperate")
private val LAST_EAT_TAG = Tag.Long("mechanic:frog:last_eat").defaultValue(0L)

private const val EAT_RANGE = 3.0
private const val EAT_COOLDOWN_MS = 2000L

private val PREY_TYPES = setOf(EntityType.SLIME, EntityType.MAGMA_CUBE)

private val VARIANT_FROGLIGHT = mapOf(
    "warm" to Block.PEARLESCENT_FROGLIGHT,
    "temperate" to Block.OCHRE_FROGLIGHT,
    "cold" to Block.VERDANT_FROGLIGHT,
)

class FrogModule : OrbitModule("frog") {

    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(20))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        super.onDisable()
    }

    private fun tick() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach

            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.FROG) return@entityLoop

                val now = System.currentTimeMillis()
                val lastEat = entity.getTag(LAST_EAT_TAG)
                if (now - lastEat < EAT_COOLDOWN_MS) return@entityLoop

                val prey = findNearbyPrey(entity) ?: return@entityLoop

                entity.setTag(LAST_EAT_TAG, now)

                val isMagmaCube = prey.entityType == EntityType.MAGMA_CUBE
                prey.remove()

                if (isMagmaCube) {
                    val variant = entity.getTag(FROG_VARIANT_TAG)
                    val froglightBlock = VARIANT_FROGLIGHT[variant] ?: Block.OCHRE_FROGLIGHT
                    val dropPos = entity.position
                    instance.setBlock(
                        dropPos.blockX(),
                        dropPos.blockY(),
                        dropPos.blockZ(),
                        froglightBlock,
                    )
                }
            }
        }
    }

    private fun findNearbyPrey(frog: Entity): Entity? {
        val instance = frog.instance ?: return null
        return instance.getNearbyEntities(frog.position, EAT_RANGE)
            .firstOrNull { it.entityType in PREY_TYPES && it != frog }
    }
}
