package me.nebula.orbit.utils.customcontent.event

import me.nebula.orbit.utils.customcontent.block.CustomBlock
import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.minestom.server.collision.BoundingBox
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack

object CustomBlockPlaceHandler {

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.hand != PlayerHand.MAIN) return@addListener
            val player = event.player
            val heldItem = player.itemInMainHand
            val customBlock = blockFromItem(heldItem) ?: return@addListener

            event.isCancelled = true
            event.setBlockingItemUse(true)

            val instance = player.instance ?: return@addListener
            val targetPos = event.blockPosition.relative(event.blockFace)
            if (!instance.isChunkLoaded(targetPos)) return@addListener
            val existing = instance.getBlock(targetPos)
            if (!isReplaceable(existing)) return@addListener
            if (intersectsLivingEntity(instance, targetPos)) return@addListener

            instance.setBlock(targetPos, customBlock.allocatedState)
            CarrierSoundSuppressor.suppressPlace(instance)

            if (player.gameMode != GameMode.CREATIVE) {
                val newItem = if (heldItem.amount() > 1) heldItem.withAmount(heldItem.amount() - 1)
                else ItemStack.AIR
                player.setItemInMainHand(newItem)
            }

            instance.playSound(
                Sound.sound(Key.key("minecraft", customBlock.placeSound), Sound.Source.BLOCK, 1f, 1f),
                targetPos.x() + 0.5, targetPos.y() + 0.5, targetPos.z() + 0.5,
            )
        }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (blockFromItem(event.player.itemInMainHand) != null) {
                event.isCancelled = true
            }
        }
    }

    fun place(instance: Instance, pos: Point, customBlock: CustomBlock): Boolean {
        if (!instance.isChunkLoaded(pos)) return false
        if (!isReplaceable(instance.getBlock(pos))) return false
        if (intersectsLivingEntity(instance, pos)) return false
        instance.setBlock(pos, customBlock.allocatedState)
        return true
    }

    private fun blockFromItem(stack: ItemStack): CustomBlock? {
        val cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA) ?: return null
        val cmdValue = cmd.floats().firstOrNull()?.toInt() ?: return null
        return CustomBlockRegistry.byCustomModelData(cmdValue)
            ?: CustomItemRegistry.byCustomModelData(cmdValue)?.let { CustomBlockRegistry.fromItemId(it.id) }
    }

    private val REPLACEABLE_KEYS = setOf(
        "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
        "minecraft:water", "minecraft:lava", "minecraft:bubble_column",
        "minecraft:short_grass", "minecraft:grass", "minecraft:tall_grass",
        "minecraft:fern", "minecraft:large_fern",
        "minecraft:dead_bush", "minecraft:vine",
        "minecraft:snow", "minecraft:snow_layer", "minecraft:powder_snow",
        "minecraft:fire", "minecraft:soul_fire",
        "minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:kelp", "minecraft:kelp_plant",
        "minecraft:hanging_roots", "minecraft:glow_lichen", "minecraft:moss_carpet",
    )

    private fun isReplaceable(block: Block): Boolean =
        block.isAir || block.key().asString() in REPLACEABLE_KEYS

    private fun intersectsLivingEntity(instance: Instance, pos: Point): Boolean {
        val blockBox = BoundingBox(1.0, 1.0, 1.0)
        val blockCenter = Pos(pos.blockX() + 0.5, pos.blockY().toDouble(), pos.blockZ() + 0.5)
        var hit = false
        instance.entityTracker.nearbyEntities(blockCenter, 2.0, EntityTracker.Target.ENTITIES) { entity ->
            if (hit) return@nearbyEntities
            if (entity is LivingEntity && entity.boundingBox.intersectBox(
                    entity.position.sub(blockCenter), blockBox,
                )) {
                hit = true
            }
        }
        return hit
    }
}
