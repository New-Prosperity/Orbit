package me.nebula.orbit.mechanic.allay

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.entity.EntitySpawnEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val ASSIGNED_MATERIAL_TAG = Tag.String("mechanic:allay:material").defaultValue("")
private val HOME_X_TAG = Tag.Double("mechanic:allay:home_x").defaultValue(0.0)
private val HOME_Y_TAG = Tag.Double("mechanic:allay:home_y").defaultValue(0.0)
private val HOME_Z_TAG = Tag.Double("mechanic:allay:home_z").defaultValue(0.0)
private val HAS_HOME_TAG = Tag.Boolean("mechanic:allay:has_home").defaultValue(false)

private const val COLLECTION_RANGE = 32.0
private const val DELIVERY_RANGE = 2.0
private const val MOVE_SPEED = 8.0

class AllayModule : OrbitModule("allay") {

    private val trackedAllays: MutableSet<Entity> = ConcurrentHashMap.newKeySet()
    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntitySpawnEvent::class.java) { event ->
            if (event.entity.entityType == EntityType.ALLAY) {
                trackedAllays.add(event.entity)
            }
        }

        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            if (event.target.entityType != EntityType.ALLAY) return@addListener

            val allay = event.target
            trackedAllays.add(allay)
            val player = event.player
            val held = player.getItemInMainHand()

            if (held.isAir) {
                allay.setTag(ASSIGNED_MATERIAL_TAG, "")
                return@addListener
            }

            allay.setTag(ASSIGNED_MATERIAL_TAG, held.material().name())

            val playerPos = player.position
            scanForNoteBlock(allay, playerPos)
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(20))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedAllays.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedAllays.removeIf { it.isRemoved }

        trackedAllays.forEach { allay ->
            val materialName = allay.getTag(ASSIGNED_MATERIAL_TAG)
            if (materialName.isEmpty()) return@forEach

            collectNearbyItems(allay, materialName)
        }
    }

    private fun collectNearbyItems(allay: Entity, materialName: String) {
        val instance = allay.instance ?: return

        val matchingItems = instance.getNearbyEntities(allay.position, COLLECTION_RANGE)
            .filterIsInstance<ItemEntity>()
            .filter { it.itemStack.material().name() == materialName }

        if (matchingItems.isEmpty()) return

        val target = matchingItems.first()
        val direction = target.position.asVec().sub(allay.position.asVec())
        val distance = direction.length()

        if (distance < DELIVERY_RANGE) {
            val collectedItem = target.itemStack
            target.remove()

            if (allay.getTag(HAS_HOME_TAG)) {
                val homePos = Pos(
                    allay.getTag(HOME_X_TAG),
                    allay.getTag(HOME_Y_TAG),
                    allay.getTag(HOME_Z_TAG),
                )
                val drop = ItemEntity(collectedItem)
                drop.setInstance(instance, homePos)
                drop.setPickupDelay(java.time.Duration.ofMillis(500))
            }
        } else {
            val velocity = direction.normalize().mul(MOVE_SPEED)
            allay.velocity = velocity
        }
    }

    private fun scanForNoteBlock(allay: Entity, nearPos: Pos) {
        val instance = allay.instance ?: return
        val bx = nearPos.blockX()
        val by = nearPos.blockY()
        val bz = nearPos.blockZ()

        for (x in (bx - 8)..(bx + 8)) {
            for (y in (by - 4)..(by + 4)) {
                for (z in (bz - 8)..(bz + 8)) {
                    if (instance.getBlock(x, y, z).name() == "minecraft:note_block") {
                        allay.setTag(HAS_HOME_TAG, true)
                        allay.setTag(HOME_X_TAG, x + 0.5)
                        allay.setTag(HOME_Y_TAG, y + 1.0)
                        allay.setTag(HOME_Z_TAG, z + 0.5)
                        return
                    }
                }
            }
        }
    }
}
