package me.nebula.orbit.mechanic.itemframe

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.other.ItemFrameMeta
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

private val ITEM_TAG = Tag.ItemStack("mechanic:itemframe:item").defaultValue(ItemStack.AIR)
private val ROTATION_TAG = Tag.Integer("mechanic:itemframe:rotation").defaultValue(0)

class ItemFrameModule : OrbitModule("item-frame") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val held = event.player.getItemInMainHand()
            if (held.material() != Material.ITEM_FRAME && held.material() != Material.GLOW_ITEM_FRAME) return@addListener

            val instance = event.player.instance ?: return@addListener
            val face = event.blockFace
            val pos = event.blockPosition
            val targetPos = pos.add(face.toDirection().normalX(), face.toDirection().normalY(), face.toDirection().normalZ())

            val entityType = if (held.material() == Material.GLOW_ITEM_FRAME) EntityType.GLOW_ITEM_FRAME else EntityType.ITEM_FRAME
            val frame = Entity(entityType)
            val spawnPos = Pos(targetPos.x() + 0.5, targetPos.y().toDouble(), targetPos.z() + 0.5, faceToYaw(face), faceToPitch(face))
            frame.setInstance(instance, spawnPos)

            consumeHeldItem(event.player)
        }

        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            if (event.target.entityType != EntityType.ITEM_FRAME && event.target.entityType != EntityType.GLOW_ITEM_FRAME) return@addListener

            val frame = event.target
            val player = event.player
            val currentItem = frame.getTag(ITEM_TAG)

            if (player.isSneaking && !currentItem.isAir) {
                frame.setTag(ITEM_TAG, ItemStack.AIR)
                return@addListener
            }

            if (currentItem.isAir) {
                val held = player.getItemInMainHand()
                if (!held.isAir) {
                    frame.setTag(ITEM_TAG, held.withAmount(1))
                    consumeHeldItem(player)
                }
            } else {
                val rotation = (frame.getTag(ROTATION_TAG) + 1) % 8
                frame.setTag(ROTATION_TAG, rotation)
            }
        }
    }

    private fun consumeHeldItem(player: Player) {
        val slot = player.heldSlot.toInt()
        val item = player.inventory.getItemStack(slot)
        if (item.amount() > 1) {
            player.inventory.setItemStack(slot, item.withAmount(item.amount() - 1))
        } else {
            player.inventory.setItemStack(slot, ItemStack.AIR)
        }
    }

    private fun faceToYaw(face: BlockFace): Float = when (face) {
        BlockFace.SOUTH -> 0f
        BlockFace.WEST -> 90f
        BlockFace.NORTH -> 180f
        BlockFace.EAST -> -90f
        else -> 0f
    }

    private fun faceToPitch(face: BlockFace): Float = when (face) {
        BlockFace.TOP -> -90f
        BlockFace.BOTTOM -> 90f
        else -> 0f
    }
}
