package me.nebula.orbit.mechanic.fishing

import me.nebula.orbit.mechanic.food.addExhaustion
import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.item.PlayerBeginItemUseEvent
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val BOBBER_TAG = Tag.Integer("mechanic:fishing:bobber_id").defaultValue(0)

private val FISH_LOOT = listOf(
    Material.COD to 60,
    Material.SALMON to 25,
    Material.TROPICAL_FISH to 2,
    Material.PUFFERFISH to 13,
)

class FishingModule : OrbitModule("fishing") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBeginItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.FISHING_ROD) return@addListener
            castLine(event.player)
        }

        eventNode.addListener(PlayerCancelItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.FISHING_ROD) return@addListener
            reelIn(event.player)
        }
    }

    private fun castLine(player: Player) {
        val existingId = player.getTag(BOBBER_TAG)
        if (existingId != 0) return

        val instance = player.instance ?: return
        val yaw = Math.toRadians(player.position.yaw().toDouble())
        val pitch = Math.toRadians(player.position.pitch().toDouble())
        val speed = 15.0

        val bobber = Entity(EntityType.FISHING_BOBBER)
        bobber.velocity = Vec(
            -sin(yaw) * cos(pitch) * speed,
            -sin(pitch) * speed,
            cos(yaw) * cos(pitch) * speed,
        )

        bobber.setInstance(instance, player.position.add(0.0, player.eyeHeight, 0.0))
        player.setTag(BOBBER_TAG, bobber.entityId)

        val biteDelay = Random.nextInt(100, 600)
        bobber.scheduler().buildTask {
            bobber.velocity = Vec(0.0, -2.0, 0.0)
        }.delay(TaskSchedule.tick(biteDelay)).schedule()

        bobber.scheduler().buildTask {
            if (!bobber.isRemoved) {
                bobber.remove()
                player.setTag(BOBBER_TAG, 0)
            }
        }.delay(TaskSchedule.seconds(60)).schedule()
    }

    private fun reelIn(player: Player) {
        val bobberId = player.getTag(BOBBER_TAG)
        if (bobberId == 0) return
        player.setTag(BOBBER_TAG, 0)

        val instance = player.instance ?: return
        val bobber = instance.entities.firstOrNull { it.entityId == bobberId }
        if (bobber == null || bobber.isRemoved) return

        val inWater = instance.getBlock(bobber.position).name() == "minecraft:water"
        bobber.remove()

        if (inWater) {
            val fish = selectLoot()
            val item = ItemEntity(ItemStack.of(fish))
            item.setInstance(instance, player.position.add(0.0, player.eyeHeight, 0.0))

            item.scheduler().buildTask { item.remove() }
                .delay(TaskSchedule.minutes(5))
                .schedule()

            player.addExhaustion(0.025f)
        }
    }

    private fun selectLoot(): Material {
        val total = FISH_LOOT.sumOf { it.second }
        var roll = Random.nextInt(total)
        for ((material, weight) in FISH_LOOT) {
            roll -= weight
            if (roll < 0) return material
        }
        return FISH_LOOT.first().first
    }
}
