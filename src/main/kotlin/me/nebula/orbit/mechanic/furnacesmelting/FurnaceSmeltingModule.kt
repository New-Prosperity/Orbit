package me.nebula.orbit.mechanic.furnacesmelting

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.inventory.Inventory
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val SMELTING_RECIPES = mapOf(
    Material.IRON_ORE to Material.IRON_INGOT,
    Material.RAW_IRON to Material.IRON_INGOT,
    Material.GOLD_ORE to Material.GOLD_INGOT,
    Material.RAW_GOLD to Material.GOLD_INGOT,
    Material.COPPER_ORE to Material.COPPER_INGOT,
    Material.RAW_COPPER to Material.COPPER_INGOT,
    Material.COBBLESTONE to Material.STONE,
    Material.SAND to Material.GLASS,
    Material.RED_SAND to Material.GLASS,
    Material.CLAY_BALL to Material.BRICK,
    Material.NETHERRACK to Material.NETHER_BRICK,
    Material.COBBLED_DEEPSLATE to Material.DEEPSLATE,
    Material.STONE to Material.SMOOTH_STONE,
    Material.SANDSTONE to Material.SMOOTH_SANDSTONE,
    Material.RED_SANDSTONE to Material.SMOOTH_RED_SANDSTONE,
    Material.CACTUS to Material.GREEN_DYE,
    Material.SEA_PICKLE to Material.LIME_DYE,
    Material.WET_SPONGE to Material.SPONGE,
    Material.BEEF to Material.COOKED_BEEF,
    Material.CHICKEN to Material.COOKED_CHICKEN,
    Material.PORKCHOP to Material.COOKED_PORKCHOP,
    Material.MUTTON to Material.COOKED_MUTTON,
    Material.RABBIT to Material.COOKED_RABBIT,
    Material.COD to Material.COOKED_COD,
    Material.SALMON to Material.COOKED_SALMON,
    Material.POTATO to Material.BAKED_POTATO,
    Material.KELP to Material.DRIED_KELP,
)

private val FUEL_BURN_TICKS = mapOf(
    Material.COAL to 160,
    Material.CHARCOAL to 160,
    Material.OAK_LOG to 30,
    Material.SPRUCE_LOG to 30,
    Material.BIRCH_LOG to 30,
    Material.JUNGLE_LOG to 30,
    Material.ACACIA_LOG to 30,
    Material.DARK_OAK_LOG to 30,
    Material.OAK_PLANKS to 30,
    Material.SPRUCE_PLANKS to 30,
    Material.BIRCH_PLANKS to 30,
    Material.JUNGLE_PLANKS to 30,
    Material.ACACIA_PLANKS to 30,
    Material.DARK_OAK_PLANKS to 30,
    Material.STICK to 10,
    Material.COAL_BLOCK to 1600,
    Material.LAVA_BUCKET to 2000,
    Material.BLAZE_ROD to 240,
    Material.DRIED_KELP_BLOCK to 400,
)

data class SmeltingState(
    val inventory: Inventory,
    var fuelRemaining: Int = 0,
    var smeltProgress: Int = 0,
    val smeltTime: Int = 200,
)

class FurnaceSmeltingModule : OrbitModule("furnace-smelting") {

    private val furnaces = ConcurrentHashMap<Int, SmeltingState>()
    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(1))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        furnaces.clear()
        super.onDisable()
    }

    fun registerFurnace(inventory: Inventory) {
        furnaces[System.identityHashCode(inventory)] = SmeltingState(inventory)
    }

    fun unregisterFurnace(inventory: Inventory) {
        furnaces.remove(System.identityHashCode(inventory))
    }

    private fun tick() {
        furnaces.values.forEach { state -> processFurnace(state) }
    }

    private fun processFurnace(state: SmeltingState) {
        val input = state.inventory.getItemStack(0)
        val fuel = state.inventory.getItemStack(1)
        val output = state.inventory.getItemStack(2)

        val recipe = SMELTING_RECIPES[input.material()] ?: return

        if (state.fuelRemaining <= 0) {
            val burnTime = FUEL_BURN_TICKS[fuel.material()] ?: return
            if (fuel.amount() > 1) {
                state.inventory.setItemStack(1, fuel.withAmount(fuel.amount() - 1))
            } else {
                state.inventory.setItemStack(1, ItemStack.AIR)
            }
            state.fuelRemaining = burnTime
        }

        state.fuelRemaining--
        state.smeltProgress++

        if (state.smeltProgress >= state.smeltTime) {
            state.smeltProgress = 0

            if (input.amount() > 1) {
                state.inventory.setItemStack(0, input.withAmount(input.amount() - 1))
            } else {
                state.inventory.setItemStack(0, ItemStack.AIR)
            }

            if (output.isAir) {
                state.inventory.setItemStack(2, ItemStack.of(recipe))
            } else if (output.material() == recipe && output.amount() < 64) {
                state.inventory.setItemStack(2, output.withAmount(output.amount() + 1))
            }
        }
    }
}
