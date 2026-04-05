package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.packBlockPos
import net.kyori.adventure.text.Component
import net.minestom.server.component.DataComponents
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.time.Duration
import kotlin.random.Random

private val ANVIL_BLOCKS = setOf("minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil")

private val REPAIR_MATERIALS = mapOf(
    Material.IRON_SWORD to Material.IRON_INGOT, Material.IRON_PICKAXE to Material.IRON_INGOT,
    Material.IRON_AXE to Material.IRON_INGOT, Material.IRON_SHOVEL to Material.IRON_INGOT,
    Material.IRON_HOE to Material.IRON_INGOT,
    Material.IRON_HELMET to Material.IRON_INGOT, Material.IRON_CHESTPLATE to Material.IRON_INGOT,
    Material.IRON_LEGGINGS to Material.IRON_INGOT, Material.IRON_BOOTS to Material.IRON_INGOT,
    Material.DIAMOND_SWORD to Material.DIAMOND, Material.DIAMOND_PICKAXE to Material.DIAMOND,
    Material.DIAMOND_AXE to Material.DIAMOND, Material.DIAMOND_SHOVEL to Material.DIAMOND,
    Material.DIAMOND_HOE to Material.DIAMOND,
    Material.DIAMOND_HELMET to Material.DIAMOND, Material.DIAMOND_CHESTPLATE to Material.DIAMOND,
    Material.DIAMOND_LEGGINGS to Material.DIAMOND, Material.DIAMOND_BOOTS to Material.DIAMOND,
    Material.GOLDEN_SWORD to Material.GOLD_INGOT, Material.GOLDEN_PICKAXE to Material.GOLD_INGOT,
    Material.GOLDEN_AXE to Material.GOLD_INGOT, Material.GOLDEN_SHOVEL to Material.GOLD_INGOT,
    Material.GOLDEN_HOE to Material.GOLD_INGOT,
    Material.GOLDEN_HELMET to Material.GOLD_INGOT, Material.GOLDEN_CHESTPLATE to Material.GOLD_INGOT,
    Material.GOLDEN_LEGGINGS to Material.GOLD_INGOT, Material.GOLDEN_BOOTS to Material.GOLD_INGOT,
    Material.NETHERITE_SWORD to Material.NETHERITE_INGOT, Material.NETHERITE_PICKAXE to Material.NETHERITE_INGOT,
    Material.NETHERITE_AXE to Material.NETHERITE_INGOT, Material.NETHERITE_SHOVEL to Material.NETHERITE_INGOT,
    Material.NETHERITE_HOE to Material.NETHERITE_INGOT,
    Material.NETHERITE_HELMET to Material.NETHERITE_INGOT, Material.NETHERITE_CHESTPLATE to Material.NETHERITE_INGOT,
    Material.NETHERITE_LEGGINGS to Material.NETHERITE_INGOT, Material.NETHERITE_BOOTS to Material.NETHERITE_INGOT,
    Material.WOODEN_SWORD to Material.OAK_PLANKS, Material.WOODEN_PICKAXE to Material.OAK_PLANKS,
    Material.WOODEN_AXE to Material.OAK_PLANKS, Material.WOODEN_SHOVEL to Material.OAK_PLANKS,
    Material.WOODEN_HOE to Material.OAK_PLANKS, Material.SHIELD to Material.OAK_PLANKS,
    Material.STONE_SWORD to Material.COBBLESTONE, Material.STONE_PICKAXE to Material.COBBLESTONE,
    Material.STONE_AXE to Material.COBBLESTONE, Material.STONE_SHOVEL to Material.COBBLESTONE,
    Material.STONE_HOE to Material.COBBLESTONE,
    Material.LEATHER_HELMET to Material.LEATHER, Material.LEATHER_CHESTPLATE to Material.LEATHER,
    Material.LEATHER_LEGGINGS to Material.LEATHER, Material.LEATHER_BOOTS to Material.LEATHER,
    Material.CHAINMAIL_HELMET to Material.IRON_INGOT, Material.CHAINMAIL_CHESTPLATE to Material.IRON_INGOT,
    Material.CHAINMAIL_LEGGINGS to Material.IRON_INGOT, Material.CHAINMAIL_BOOTS to Material.IRON_INGOT,
    Material.TURTLE_HELMET to Material.TURTLE_SCUTE,
    Material.ELYTRA to Material.PHANTOM_MEMBRANE,
)

object AnvilModule : VanillaModule {

    override val id = "anvil"
    override val description = "Anvil repair with materials, combine identical items with durability bonus, rename items"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-anvil")

        val TAG_ANVIL_X = Tag.Integer("nebula:anvil_x")
        val TAG_ANVIL_Y = Tag.Integer("nebula:anvil_y")
        val TAG_ANVIL_Z = Tag.Integer("nebula:anvil_z")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() !in ANVIL_BLOCKS) return@addListener
            event.player.setTag(TAG_ANVIL_X, event.blockPosition.blockX())
            event.player.setTag(TAG_ANVIL_Y, event.blockPosition.blockY())
            event.player.setTag(TAG_ANVIL_Z, event.blockPosition.blockZ())
            val inv = Inventory(InventoryType.ANVIL, Component.text("Repair & Name"))
            event.player.openInventory(inv)
        }

        node.addListener(InventoryPreClickEvent::class.java) { event ->
            val inv = event.inventory as? Inventory ?: return@addListener
            if (inv.inventoryType != InventoryType.ANVIL) return@addListener
            if (event.slot != 2) {
                val inst = event.player.instance ?: return@addListener
                inst.scheduler().buildTask {
                    computeAnvilResult(inv)
                }.delay(Duration.ofMillis(50)).schedule()
                return@addListener
            }

            val output = inv.getItemStack(2)
            if (output.isAir) return@addListener

            val target = inv.getItemStack(0)
            val sacrifice = inv.getItemStack(1)

            val repairMat = REPAIR_MATERIALS[target.material()]
            val unitsUsed = if (repairMat != null && sacrifice.material() == repairMat) {
                computeUnitsNeeded(target)
            } else 0

            inv.setItemStack(0, ItemStack.AIR)
            if (unitsUsed > 0 && sacrifice.amount() > unitsUsed) {
                inv.setItemStack(1, sacrifice.withAmount(sacrifice.amount() - unitsUsed))
            } else {
                inv.setItemStack(1, ItemStack.AIR)
            }

            val anvilX = event.player.getTag(TAG_ANVIL_X) ?: return@addListener
            val anvilY = event.player.getTag(TAG_ANVIL_Y) ?: return@addListener
            val anvilZ = event.player.getTag(TAG_ANVIL_Z) ?: return@addListener
            degradeAnvil(event.player.instance ?: return@addListener, anvilX, anvilY, anvilZ)

            val inst = event.player.instance ?: return@addListener
            inst.scheduler().buildTask {
                computeAnvilResult(inv)
            }.delay(Duration.ofMillis(50)).schedule()
        }

        return node
    }

    private fun computeAnvilResult(inv: Inventory) {
        val target = inv.getItemStack(0)
        val sacrifice = inv.getItemStack(1)

        if (target.isAir) {
            inv.setItemStack(2, ItemStack.AIR)
            return
        }

        if (sacrifice.isAir) {
            inv.setItemStack(2, ItemStack.AIR)
            return
        }

        val targetMat = target.material()
        val sacrificeMat = sacrifice.material()

        val repairMat = REPAIR_MATERIALS[targetMat]
        if (repairMat != null && sacrificeMat == repairMat) {
            val result = repairWithMaterial(target, sacrifice)
            inv.setItemStack(2, result)
            return
        }

        if (targetMat == sacrificeMat) {
            val result = combineItems(target, sacrifice)
            inv.setItemStack(2, result)
            return
        }

        inv.setItemStack(2, ItemStack.AIR)
    }

    private fun repairWithMaterial(target: ItemStack, material: ItemStack): ItemStack {
        val maxDamage = target.get(DataComponents.MAX_DAMAGE) ?: return target
        val currentDamage = target.get(DataComponents.DAMAGE) ?: 0
        if (currentDamage <= 0) return target

        val repairPerUnit = maxDamage / 4
        val unitsNeeded = computeUnitsNeeded(target)
        val unitsAvailable = material.amount().coerceAtMost(unitsNeeded)
        val repairAmount = repairPerUnit * unitsAvailable
        val newDamage = (currentDamage - repairAmount).coerceAtLeast(0)

        return target.with(DataComponents.DAMAGE, newDamage)
    }

    private fun computeUnitsNeeded(target: ItemStack): Int {
        val maxDamage = target.get(DataComponents.MAX_DAMAGE) ?: return 0
        val currentDamage = target.get(DataComponents.DAMAGE) ?: 0
        if (currentDamage <= 0) return 0
        val repairPerUnit = maxDamage / 4
        if (repairPerUnit <= 0) return 1
        return ((currentDamage + repairPerUnit - 1) / repairPerUnit).coerceIn(1, 4)
    }

    private fun combineItems(target: ItemStack, sacrifice: ItemStack): ItemStack {
        val maxDamage = target.get(DataComponents.MAX_DAMAGE) ?: return target
        val targetDamage = target.get(DataComponents.DAMAGE) ?: 0
        val sacrificeDamage = sacrifice.get(DataComponents.DAMAGE) ?: 0

        val targetDurability = maxDamage - targetDamage
        val sacrificeDurability = maxDamage - sacrificeDamage
        val bonus = (maxDamage * 0.12).toInt()
        val combinedDurability = (targetDurability + sacrificeDurability + bonus).coerceAtMost(maxDamage)
        val newDamage = (maxDamage - combinedDurability).coerceAtLeast(0)

        return target.with(DataComponents.DAMAGE, newDamage)
    }

    private fun degradeAnvil(instance: Instance, x: Int, y: Int, z: Int) {
        if (Random.nextInt(100) >= 12) return
        val block = instance.getBlock(x, y, z)
        val name = block.name()
        if (name !in ANVIL_BLOCKS) return
        val newBlock = when (name) {
            "minecraft:anvil" -> Block.CHIPPED_ANVIL
            "minecraft:chipped_anvil" -> Block.DAMAGED_ANVIL
            else -> null
        }
        if (newBlock != null) {
            instance.setBlock(x, y, z, newBlock)
        } else {
            instance.setBlock(x, y, z, Block.AIR)
        }
    }
}
