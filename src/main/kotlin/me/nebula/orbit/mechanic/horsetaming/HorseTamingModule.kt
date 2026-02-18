package me.nebula.orbit.mechanic.horsetaming

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val TAMED_TAG = Tag.Boolean("mechanic:horse_taming:tamed").defaultValue(false)
private val TAME_PROGRESS_TAG = Tag.Integer("mechanic:horse_taming:tame_progress").defaultValue(0)
private val OWNER_UUID_TAG = Tag.String("mechanic:horse_taming:owner")
private val SADDLED_TAG = Tag.Boolean("mechanic:horse_taming:saddled").defaultValue(false)

private const val MAX_TAME_PROGRESS = 10
private const val BASE_TAME_CHANCE = 0.05
private const val TAME_CHANCE_PER_PROGRESS = 0.05
private const val RIDE_SPEED = 15.0

private val HORSE_TYPES = setOf(EntityType.HORSE, EntityType.DONKEY, EntityType.MULE)

class HorseTamingModule : OrbitModule("horse-taming") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            val entity = event.target
            if (entity.entityType !in HORSE_TYPES) return@addListener
            val player = event.player

            if (entity.getTag(TAMED_TAG)) {
                if (entity.getTag(OWNER_UUID_TAG) != player.uuid.toString()) return@addListener

                val heldItem = player.getItemInMainHand()
                if (!entity.getTag(SADDLED_TAG) && heldItem.material() == Material.SADDLE) {
                    entity.setTag(SADDLED_TAG, true)
                    if (heldItem.amount() > 1) {
                        player.setItemInMainHand(heldItem.withAmount(heldItem.amount() - 1))
                    } else {
                        player.setItemInMainHand(net.minestom.server.item.ItemStack.AIR)
                    }
                    return@addListener
                }

                if (entity.getTag(SADDLED_TAG) && player !in entity.passengers) {
                    entity.addPassenger(player)
                }
                return@addListener
            }

            if (player in entity.passengers) return@addListener
            entity.addPassenger(player)

            val progress = entity.getTag(TAME_PROGRESS_TAG) + 1
            entity.setTag(TAME_PROGRESS_TAG, min(progress, MAX_TAME_PROGRESS))

            val chance = BASE_TAME_CHANCE + (progress * TAME_CHANCE_PER_PROGRESS)
            if (Random.nextDouble() < chance) {
                entity.setTag(TAMED_TAG, true)
                entity.setTag(OWNER_UUID_TAG, player.uuid.toString())
            } else {
                entity.scheduler().buildTask {
                    entity.removePassenger(player)
                }.delay(net.minestom.server.timer.TaskSchedule.tick(20)).schedule()
            }
        }

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val vehicle = player.vehicle ?: return@addListener
            if (vehicle.entityType !in HORSE_TYPES) return@addListener
            if (!vehicle.getTag(TAMED_TAG)) return@addListener
            if (!vehicle.getTag(SADDLED_TAG)) return@addListener

            val yaw = Math.toRadians(player.position.yaw().toDouble())
            val dirX = -sin(yaw)
            val dirZ = cos(yaw)
            vehicle.velocity = Vec(dirX * RIDE_SPEED, vehicle.velocity.y(), dirZ * RIDE_SPEED)
        }
    }
}
