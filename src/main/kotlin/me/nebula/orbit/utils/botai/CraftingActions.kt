package me.nebula.orbit.utils.botai

import me.nebula.orbit.utils.vanilla.modules.FURNACE_NAMES
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

class CraftItem(
    private val result: ItemStack,
    private val recipe: List<Pair<Material, Int>>,
    private val craftingTablePos: Point? = null,
) : BotAction {
    override var isComplete = false
        private set
    private var walkAction: WalkTo? = null
    private var interacted = false

    override fun tick(player: Player) {
        if (isComplete) return
        val instance = player.instance ?: run { isComplete = true; return }
        val tablePos = craftingTablePos

        if (tablePos != null) {
            val walk = walkAction ?: WalkTo(blockCenter(tablePos)).also {
                it.start(player)
                walkAction = it
            }
            if (!walk.isComplete) {
                walk.tick(player)
                return
            }
            if (!interacted) {
                val block = instance.getBlock(tablePos)
                fireInteractEvent(player, tablePos, block)
                player.swingMainHand()
                interacted = true
            }
        }

        for ((material, count) in recipe) {
            var remaining = count
            for (i in 0 until player.inventory.size) {
                val stack = player.inventory.getItemStack(i)
                if (stack.material() == material) {
                    val take = minOf(remaining, stack.amount())
                    player.inventory.setItemStack(i, stack.withAmount(stack.amount() - take))
                    remaining -= take
                    if (remaining <= 0) break
                }
            }
            if (remaining > 0) {
                isComplete = true
                return
            }
        }
        player.inventory.addItemStack(result)
        isComplete = true
    }
}

class SmeltItems(
    private val furnacePos: Point,
    private val input: Material,
    private val fuel: Material = Material.COAL,
) : BotAction {
    override var isComplete = false
        private set

    private enum class Phase { WALKING, OPENING, PLACING, WAITING, COLLECTING, DONE }
    private var phase = Phase.WALKING
    private var subAction: BotAction? = null
    private var furnaceInv: Inventory? = null
    private var waitTicks = 0
    private var itemsToSmelt = 0

    override fun tick(player: Player) {
        if (isComplete) return
        when (phase) {
            Phase.WALKING -> {
                val sub = subAction
                if (sub == null) {
                    val walk = WalkTo(blockCenter(furnacePos))
                    walk.start(player)
                    subAction = walk
                    return
                }
                sub.tick(player)
                if (sub.isComplete) phase = Phase.OPENING
            }
            Phase.OPENING -> {
                val instance = player.instance
                val block = instance?.getBlock(furnacePos)
                if (block == null || !block.name().let { it in FURNACE_NAMES }) {
                    isComplete = true
                    return
                }
                fireInteractEvent(player, furnacePos, block)
                val openInv = player.openInventory
                if (openInv is Inventory && openInv.inventoryType == InventoryType.FURNACE) {
                    furnaceInv = openInv
                    phase = Phase.PLACING
                } else {
                    isComplete = true
                }
            }
            Phase.PLACING -> {
                val inv = furnaceInv ?: run { isComplete = true; return }
                var inputCount = 0
                for (i in 0 until player.inventory.size) {
                    val stack = player.inventory.getItemStack(i)
                    if (stack.material() == input) inputCount += stack.amount()
                }
                var fuelCount = 0
                for (i in 0 until player.inventory.size) {
                    val stack = player.inventory.getItemStack(i)
                    if (stack.material() == fuel) fuelCount += stack.amount()
                }
                val canSmelt = minOf(inputCount, fuelCount * 8)
                if (canSmelt <= 0) {
                    player.closeInventory()
                    isComplete = true
                    return
                }
                itemsToSmelt = canSmelt
                val fuelNeeded = (canSmelt + 7) / 8
                consumeMaterial(player, input, canSmelt)
                consumeMaterial(player, fuel, fuelNeeded)
                inv.setItemStack(0, ItemStack.of(input, canSmelt))
                inv.setItemStack(1, ItemStack.of(fuel, fuelNeeded))
                waitTicks = 0
                phase = Phase.WAITING
            }
            Phase.WAITING -> {
                waitTicks++
                if (waitTicks % 20 != 0 && waitTicks <= itemsToSmelt * 220) return
                val inv = furnaceInv ?: run { isComplete = true; return }
                val output = inv.getItemStack(2)
                if (!output.isAir && output.amount() >= itemsToSmelt) {
                    phase = Phase.COLLECTING
                    return
                }
                if (waitTicks > itemsToSmelt * 220) {
                    phase = Phase.COLLECTING
                }
            }
            Phase.COLLECTING -> {
                val inv = furnaceInv ?: run { isComplete = true; return }
                val output = inv.getItemStack(2)
                if (!output.isAir) {
                    player.inventory.addItemStack(output)
                    inv.setItemStack(2, ItemStack.AIR)
                }
                val leftover0 = inv.getItemStack(0)
                if (!leftover0.isAir) {
                    player.inventory.addItemStack(leftover0)
                    inv.setItemStack(0, ItemStack.AIR)
                }
                val leftover1 = inv.getItemStack(1)
                if (!leftover1.isAir) {
                    player.inventory.addItemStack(leftover1)
                    inv.setItemStack(1, ItemStack.AIR)
                }
                player.closeInventory()
                phase = Phase.DONE
                isComplete = true
            }
            Phase.DONE -> isComplete = true
        }
    }

    private fun consumeMaterial(player: Player, material: Material, amount: Int) {
        var remaining = amount
        for (i in 0 until player.inventory.size) {
            if (remaining <= 0) break
            val stack = player.inventory.getItemStack(i)
            if (stack.material() == material) {
                val take = minOf(remaining, stack.amount())
                player.inventory.setItemStack(i, stack.withAmount(stack.amount() - take))
                remaining -= take
            }
        }
    }
}
