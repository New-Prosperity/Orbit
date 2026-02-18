package me.nebula.orbit.mechanic.itemframerotation

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.tag.Tag
import java.time.Duration

private val ROTATION_TAG = Tag.Integer("mechanic:itemframerotation:rotation").defaultValue(0)
private val STORED_ITEM_TAG = Tag.ItemStack("mechanic:itemframerotation:item").defaultValue(ItemStack.AIR)

private val FRAME_TYPES = setOf(EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME)

class ItemFrameRotationModule : OrbitModule("item-frame-rotation") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val target = event.target
            if (target.entityType !in FRAME_TYPES) return@addListener

            val storedItem = target.getTag(STORED_ITEM_TAG)
            if (storedItem.isAir) return@addListener

            val currentRotation = target.getTag(ROTATION_TAG)
            if (currentRotation >= 7) {
                val instance = target.instance ?: return@addListener
                val drop = ItemEntity(storedItem)
                drop.setInstance(instance, target.position)
                drop.setPickupDelay(Duration.ofMillis(500))

                target.setTag(STORED_ITEM_TAG, ItemStack.AIR)
                target.setTag(ROTATION_TAG, 0)
            } else {
                target.setTag(ROTATION_TAG, currentRotation + 1)
            }
        }
    }
}
