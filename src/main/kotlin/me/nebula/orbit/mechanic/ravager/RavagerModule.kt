package me.nebula.orbit.mechanic.ravager

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val LAST_CHARGE_TAG = Tag.Long("mechanic:ravager:last_charge").defaultValue(0L)

private const val SEARCH_RANGE = 12.0
private const val CHARGE_COOLDOWN_MS = 4000L
private const val CHARGE_DAMAGE = 12f
private const val MELEE_RANGE = 3.0
private const val CHARGE_SPEED = 25.0

private val LEAF_BLOCKS = setOf(
    "minecraft:oak_leaves", "minecraft:spruce_leaves", "minecraft:birch_leaves",
    "minecraft:jungle_leaves", "minecraft:acacia_leaves", "minecraft:dark_oak_leaves",
    "minecraft:azalea_leaves", "minecraft:flowering_azalea_leaves",
    "minecraft:mangrove_leaves", "minecraft:cherry_leaves",
)

class RavagerModule : OrbitModule("ravager") {

    private var tickTask: Task? = null
    private val trackedRavagers: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(10))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedRavagers.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedRavagers.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.RAVAGER) return@entityLoop
                trackedRavagers.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedRavagers.forEach { ravager ->
            if (ravager.isRemoved) return@forEach
            val lastCharge = ravager.getTag(LAST_CHARGE_TAG)
            if (now - lastCharge < CHARGE_COOLDOWN_MS) return@forEach

            val instance = ravager.instance ?: return@forEach
            val target = findNearestPlayer(ravager, instance) ?: return@forEach

            val distance = ravager.position.distance(target.position)

            if (distance <= MELEE_RANGE) {
                ravager.setTag(LAST_CHARGE_TAG, now)
                target.damage(DamageType.MOB_ATTACK, CHARGE_DAMAGE)
                breakLeaves(ravager, instance)
            } else {
                val direction = target.position.asVec()
                    .sub(ravager.position.asVec())
                    .normalize()
                ravager.velocity = direction.mul(CHARGE_SPEED)
                breakLeaves(ravager, instance)
            }
        }
    }

    private fun breakLeaves(ravager: Entity, instance: Instance) {
        val pos = ravager.position
        for (dx in -1..1) {
            for (dy in 0..2) {
                for (dz in -1..1) {
                    val bx = pos.blockX() + dx
                    val by = pos.blockY() + dy
                    val bz = pos.blockZ() + dz
                    val block = instance.getBlock(bx, by, bz)
                    if (block.name() in LEAF_BLOCKS) {
                        instance.setBlock(bx, by, bz, Block.AIR)
                    }
                }
            }
        }
    }

    private fun findNearestPlayer(ravager: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = SEARCH_RANGE * SEARCH_RANGE

        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            val dist = ravager.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }
}
