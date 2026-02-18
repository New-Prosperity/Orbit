package me.nebula.orbit.mechanic.minecart

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

private val MINECART_MATERIALS = mapOf(
    Material.MINECART to EntityType.MINECART,
    Material.CHEST_MINECART to EntityType.CHEST_MINECART,
    Material.FURNACE_MINECART to EntityType.FURNACE_MINECART,
    Material.TNT_MINECART to EntityType.TNT_MINECART,
    Material.HOPPER_MINECART to EntityType.HOPPER_MINECART,
)

private val MINECART_ENTITY_TYPES = MINECART_MATERIALS.values.toSet()

private val RAIL_BLOCKS = setOf(
    "minecraft:rail", "minecraft:powered_rail", "minecraft:detector_rail", "minecraft:activator_rail",
)

private val minecartMaterialTag = Tag.String("mechanic:minecart:material")

class MinecartModule : OrbitModule("minecart") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val held = event.player.getItemInMainHand()
            val entityType = MINECART_MATERIALS[held.material()] ?: return@addListener

            val instance = event.player.instance ?: return@addListener
            val block = event.block

            if (block.name() !in RAIL_BLOCKS) return@addListener

            val pos = event.blockPosition
            val minecart = Entity(entityType)
            minecart.setTag(minecartMaterialTag, held.material().key().asString())
            minecart.setInstance(instance, Pos(pos.x() + 0.5, pos.y() + 0.0625, pos.z() + 0.5))

            val slot = event.player.heldSlot.toInt()
            if (held.amount() > 1) {
                event.player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
            } else {
                event.player.inventory.setItemStack(slot, ItemStack.AIR)
            }
        }

        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            val entity = event.target
            if (entity.entityType !in MINECART_ENTITY_TYPES) return@addListener
            if (entity.passengers.isNotEmpty()) return@addListener
            entity.addPassenger(event.player)
        }

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val target = event.target
            if (target.entityType !in MINECART_ENTITY_TYPES) return@addListener

            target.passengers.toList().forEach { target.removePassenger(it) }

            val materialKey = target.getTag(minecartMaterialTag)
            target.remove()

            if (event.entity is Player && materialKey != null) {
                val material = Material.fromKey(materialKey) ?: return@addListener
                (event.entity as Player).inventory.addItemStack(ItemStack.of(material))
            }
        }
    }
}
