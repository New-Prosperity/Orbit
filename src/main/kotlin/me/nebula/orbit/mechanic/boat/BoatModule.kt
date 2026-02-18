package me.nebula.orbit.mechanic.boat

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

private val BOAT_MATERIALS = mapOf(
    Material.OAK_BOAT to EntityType.OAK_BOAT,
    Material.SPRUCE_BOAT to EntityType.SPRUCE_BOAT,
    Material.BIRCH_BOAT to EntityType.BIRCH_BOAT,
    Material.JUNGLE_BOAT to EntityType.JUNGLE_BOAT,
    Material.ACACIA_BOAT to EntityType.ACACIA_BOAT,
    Material.DARK_OAK_BOAT to EntityType.DARK_OAK_BOAT,
    Material.CHERRY_BOAT to EntityType.CHERRY_BOAT,
    Material.MANGROVE_BOAT to EntityType.MANGROVE_BOAT,
    Material.BAMBOO_RAFT to EntityType.BAMBOO_RAFT,
    Material.OAK_CHEST_BOAT to EntityType.OAK_CHEST_BOAT,
    Material.SPRUCE_CHEST_BOAT to EntityType.SPRUCE_CHEST_BOAT,
    Material.BIRCH_CHEST_BOAT to EntityType.BIRCH_CHEST_BOAT,
    Material.JUNGLE_CHEST_BOAT to EntityType.JUNGLE_CHEST_BOAT,
    Material.ACACIA_CHEST_BOAT to EntityType.ACACIA_CHEST_BOAT,
    Material.DARK_OAK_CHEST_BOAT to EntityType.DARK_OAK_CHEST_BOAT,
    Material.CHERRY_CHEST_BOAT to EntityType.CHERRY_CHEST_BOAT,
    Material.MANGROVE_CHEST_BOAT to EntityType.MANGROVE_CHEST_BOAT,
    Material.BAMBOO_CHEST_RAFT to EntityType.BAMBOO_CHEST_RAFT,
)

private val BOAT_ENTITY_TYPES = BOAT_MATERIALS.values.toSet()

private val CHEST_BOAT_TYPES = setOf(
    EntityType.OAK_CHEST_BOAT, EntityType.SPRUCE_CHEST_BOAT, EntityType.BIRCH_CHEST_BOAT,
    EntityType.JUNGLE_CHEST_BOAT, EntityType.ACACIA_CHEST_BOAT, EntityType.DARK_OAK_CHEST_BOAT,
    EntityType.CHERRY_CHEST_BOAT, EntityType.MANGROVE_CHEST_BOAT, EntityType.BAMBOO_CHEST_RAFT,
)

private val WATER_BLOCKS = setOf("minecraft:water")

private val boatMaterialTag = Tag.String("mechanic:boat:material")

class BoatModule : OrbitModule("boat") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val held = event.player.getItemInMainHand()
            val entityType = BOAT_MATERIALS[held.material()] ?: return@addListener

            val instance = event.player.instance ?: return@addListener
            val targetPos = event.blockPosition

            val isWater = instance.getBlock(targetPos).name() in WATER_BLOCKS ||
                    instance.getBlock(targetPos.add(0, 1, 0)).name() in WATER_BLOCKS

            if (!isWater) return@addListener

            val boat = Entity(entityType)
            boat.setTag(boatMaterialTag, held.material().key().asString())
            boat.setInstance(
                instance,
                Pos(targetPos.x() + 0.5, targetPos.y() + 0.5625, targetPos.z() + 0.5)
            )

            val slot = event.player.heldSlot.toInt()
            if (held.amount() > 1) {
                event.player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
            } else {
                event.player.inventory.setItemStack(slot, ItemStack.AIR)
            }
        }

        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            val entity = event.target
            if (entity.entityType !in BOAT_ENTITY_TYPES) return@addListener
            if (entity.passengers.isNotEmpty()) return@addListener
            entity.addPassenger(event.player)
        }

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val target = event.target
            if (target.entityType !in BOAT_ENTITY_TYPES) return@addListener

            target.passengers.toList().forEach { target.removePassenger(it) }

            val materialKey = target.getTag(boatMaterialTag)
            target.remove()

            if (event.entity is Player && materialKey != null) {
                val material = Material.fromKey(materialKey) ?: return@addListener
                (event.entity as Player).inventory.addItemStack(ItemStack.of(material))
            }
        }
    }
}
