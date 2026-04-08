package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Pos
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import kotlin.random.Random

class ExploreGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float = 0.1f

    override fun shouldActivate(brain: BotBrain): Boolean = true

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val interest = brain.exploration.findInterest()
        if (interest != null && interest !is ExplorationInterest.UnexploredDirection) {
            return listOf(WalkTo(Pos(interest.pos.x(), interest.pos.y(), interest.pos.z())))
        }

        val dir = brain.exploration.exploreDirection()
        val dangerZones = brain.memory.recallLocations("danger_zone")
        var targetX = player.position.x() + dir.x() * 32.0
        var targetZ = player.position.z() + dir.z() * 32.0

        var attempts = 0
        while (attempts < 5 && dangerZones.any { zone ->
            val dx = targetX - zone.x()
            val dz = targetZ - zone.z()
            dx * dx + dz * dz < 100.0
        }) {
            targetX += Random.nextDouble(-8.0, 8.0)
            targetZ += Random.nextDouble(-8.0, 8.0)
            attempts++
        }

        val target = Pos(targetX, player.position.y(), targetZ)
        return listOf(WalkTo(target))
    }
}

class InventoryManagementGoal : BotGoal() {

    private val priorityOrder = listOf(
        Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT,
        Material.RAW_GOLD, Material.RAW_IRON, Material.RAW_COPPER, Material.LAPIS_LAZULI,
        Material.REDSTONE, Material.COAL, Material.COPPER_INGOT,
        Material.DIAMOND_PICKAXE, Material.IRON_PICKAXE, Material.STONE_PICKAXE,
        Material.DIAMOND_SWORD, Material.IRON_SWORD, Material.STONE_SWORD,
        Material.COOKED_BEEF, Material.BREAD, Material.APPLE,
        Material.OAK_LOG, Material.OAK_PLANKS, Material.STICK,
        Material.COBBLESTONE, Material.DIRT, Material.GRAVEL,
    )

    override fun calculateUtility(brain: BotBrain): Float {
        val fillRatio = inventoryFillRatio(brain)
        if (fillRatio < 0.8f) return 0f
        return 0.55f
    }

    override fun shouldActivate(brain: BotBrain): Boolean = inventoryFillRatio(brain) >= 0.8f

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val slotsToDrop = mutableListOf<Int>()
        val inventory = player.inventory
        for (i in 0 until inventory.size) {
            val stack = inventory.getItemStack(i)
            if (stack.isAir) continue
            val priority = priorityOrder.indexOf(stack.material())
            if (priority < 0 || priority >= priorityOrder.size - 3) {
                slotsToDrop.add(i)
            }
        }
        slotsToDrop.sortByDescending { slot ->
            val mat = inventory.getItemStack(slot).material()
            val idx = priorityOrder.indexOf(mat)
            if (idx < 0) Int.MAX_VALUE else idx
        }
        val toDrop = slotsToDrop.take(9)
        if (toDrop.isEmpty()) return listOf(Wait(20))
        for (slot in toDrop) {
            inventory.setItemStack(slot, ItemStack.AIR)
        }
        return listOf(Wait(5))
    }

    private fun inventoryFillRatio(brain: BotBrain): Float {
        var filled = 0
        val inventory = brain.player.inventory
        for (i in 0 until inventory.size) {
            if (!inventory.getItemStack(i).isAir) filled++
        }
        return filled.toFloat() / inventory.size.toFloat()
    }
}
