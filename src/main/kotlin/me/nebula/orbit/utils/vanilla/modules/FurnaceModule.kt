package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.VanillaModules
import me.nebula.orbit.utils.vanilla.dropInventoryContents
import me.nebula.orbit.utils.vanilla.packBlockPos
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.concurrent.ConcurrentHashMap

private val SMELTING_RECIPES = mapOf(
    Material.IRON_ORE to Material.IRON_INGOT,
    Material.DEEPSLATE_IRON_ORE to Material.IRON_INGOT,
    Material.RAW_IRON to Material.IRON_INGOT,
    Material.GOLD_ORE to Material.GOLD_INGOT,
    Material.DEEPSLATE_GOLD_ORE to Material.GOLD_INGOT,
    Material.RAW_GOLD to Material.GOLD_INGOT,
    Material.COPPER_ORE to Material.COPPER_INGOT,
    Material.DEEPSLATE_COPPER_ORE to Material.COPPER_INGOT,
    Material.RAW_COPPER to Material.COPPER_INGOT,
    Material.SAND to Material.GLASS,
    Material.RED_SAND to Material.GLASS,
    Material.COBBLESTONE to Material.STONE,
    Material.STONE to Material.SMOOTH_STONE,
    Material.CLAY_BALL to Material.BRICK,
    Material.CLAY to Material.TERRACOTTA,
    Material.OAK_LOG to Material.CHARCOAL,
    Material.SPRUCE_LOG to Material.CHARCOAL,
    Material.BIRCH_LOG to Material.CHARCOAL,
    Material.JUNGLE_LOG to Material.CHARCOAL,
    Material.ACACIA_LOG to Material.CHARCOAL,
    Material.DARK_OAK_LOG to Material.CHARCOAL,
    Material.BEEF to Material.COOKED_BEEF,
    Material.PORKCHOP to Material.COOKED_PORKCHOP,
    Material.CHICKEN to Material.COOKED_CHICKEN,
    Material.MUTTON to Material.COOKED_MUTTON,
    Material.RABBIT to Material.COOKED_RABBIT,
    Material.COD to Material.COOKED_COD,
    Material.SALMON to Material.COOKED_SALMON,
    Material.POTATO to Material.BAKED_POTATO,
    Material.KELP to Material.DRIED_KELP,
    Material.CACTUS to Material.GREEN_DYE,
    Material.ANCIENT_DEBRIS to Material.NETHERITE_SCRAP,
    Material.NETHER_GOLD_ORE to Material.GOLD_INGOT,
    Material.NETHER_QUARTZ_ORE to Material.QUARTZ,
)

private val FUEL_BURN_TIMES = mapOf(
    Material.COAL to 1600,
    Material.CHARCOAL to 1600,
    Material.COAL_BLOCK to 16000,
    Material.OAK_LOG to 300,
    Material.SPRUCE_LOG to 300,
    Material.BIRCH_LOG to 300,
    Material.JUNGLE_LOG to 300,
    Material.ACACIA_LOG to 300,
    Material.DARK_OAK_LOG to 300,
    Material.OAK_PLANKS to 300,
    Material.SPRUCE_PLANKS to 300,
    Material.BIRCH_PLANKS to 300,
    Material.JUNGLE_PLANKS to 300,
    Material.ACACIA_PLANKS to 300,
    Material.DARK_OAK_PLANKS to 300,
    Material.STICK to 100,
    Material.LAVA_BUCKET to 20000,
    Material.BLAZE_ROD to 2400,
    Material.DRIED_KELP_BLOCK to 4000,
    Material.BAMBOO to 50,
)

private const val COOK_TIME = 200

private data class FurnaceState(
    val inv: Inventory,
    var fuelRemaining: Int = 0,
    var fuelTotal: Int = 0,
    var cookProgress: Int = 0,
)

private val FURNACE_NAMES = setOf("minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker")

private class FurnaceBlockHandler(
    private val key: Key,
    private val furnaces: ConcurrentHashMap<Long, FurnaceState>,
    private val baseCookTime: Int,
) : BlockHandler {

    override fun getKey(): Key = key

    override fun isTickable(): Boolean = true

    override fun onInteract(interaction: BlockHandler.Interaction): Boolean {
        if (!VanillaModules.isEnabled(interaction.instance, "furnace")) return true
        val blockName = interaction.block.name()
        val pos = interaction.blockPosition
        val packed = packBlockPos(pos.blockX(), pos.blockY(), pos.blockZ())
        val title = when (blockName) {
            "minecraft:blast_furnace" -> "Blast Furnace"
            "minecraft:smoker" -> "Smoker"
            else -> "Furnace"
        }
        val state = furnaces.getOrPut(packed) {
            FurnaceState(Inventory(InventoryType.FURNACE, Component.text(title)))
        }
        interaction.player.openInventory(state.inv)
        return false
    }

    override fun onDestroy(destroy: BlockHandler.Destroy) {
        if (!VanillaModules.isEnabled(destroy.instance, "furnace")) return
        val pos = destroy.blockPosition
        val packed = packBlockPos(pos.blockX(), pos.blockY(), pos.blockZ())
        val state = furnaces.remove(packed) ?: return
        dropInventoryContents(destroy.instance, state.inv, pos.blockX(), pos.blockY(), pos.blockZ())
    }

    override fun tick(tick: BlockHandler.Tick) {
        if (!VanillaModules.isEnabled(tick.instance, "furnace")) return
        val pos = tick.blockPosition
        val packed = packBlockPos(pos.blockX(), pos.blockY(), pos.blockZ())
        val state = furnaces[packed] ?: return
        tickFurnace(state)
    }

    private fun tickFurnace(state: FurnaceState) {
        val inv = state.inv
        val input = inv.getItemStack(0)
        val fuel = inv.getItemStack(1)
        val output = inv.getItemStack(2)

        val recipe = if (!input.isAir) SMELTING_RECIPES[input.material()] else null

        if (recipe == null) {
            state.cookProgress = 0
            return
        }

        if (!output.isAir && (output.material() != recipe || output.amount() >= 64)) {
            state.cookProgress = 0
            return
        }

        if (state.fuelRemaining <= 0) {
            val fuelTime = if (!fuel.isAir) FUEL_BURN_TIMES[fuel.material()] else null
            if (fuelTime == null) {
                state.cookProgress = 0
                return
            }
            state.fuelTotal = fuelTime
            state.fuelRemaining = fuelTime
            inv.setItemStack(1, if (fuel.amount() > 1) fuel.withAmount(fuel.amount() - 1) else {
                if (fuel.material() == Material.LAVA_BUCKET) ItemStack.of(Material.BUCKET) else ItemStack.AIR
            })
        }

        state.fuelRemaining--
        state.cookProgress++

        if (state.cookProgress >= baseCookTime) {
            state.cookProgress = 0
            inv.setItemStack(0, if (input.amount() > 1) input.withAmount(input.amount() - 1) else ItemStack.AIR)
            inv.setItemStack(2, if (output.isAir) ItemStack.of(recipe) else output.withAmount(output.amount() + 1))
        }
    }
}

object FurnaceModule : VanillaModule {

    override val id = "furnace"
    override val description = "Furnace, blast furnace, and smoker smelting with fuel and common recipes"
    override val configParams = listOf(
        ConfigParam.IntParam("cookTimeTicks", "Base ticks to smelt one item", COOK_TIME, 50, 1000),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val baseCookTime = config.getInt("cookTimeTicks", COOK_TIME)
        val furnaces = ConcurrentHashMap<Long, FurnaceState>()

        val blockManager = MinecraftServer.getBlockManager()
        val furnaceHandler = FurnaceBlockHandler(Key.key("minecraft:furnace"), furnaces, baseCookTime)
        val blastFurnaceHandler = FurnaceBlockHandler(Key.key("minecraft:blast_furnace"), furnaces, baseCookTime)
        val smokerHandler = FurnaceBlockHandler(Key.key("minecraft:smoker"), furnaces, baseCookTime)

        blockManager.registerHandler("minecraft:furnace") { furnaceHandler }
        blockManager.registerHandler("minecraft:blast_furnace") { blastFurnaceHandler }
        blockManager.registerHandler("minecraft:smoker") { smokerHandler }

        val node = EventNode.all("vanilla-furnace")

        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val blockName = event.block.name()
            if (blockName !in FURNACE_NAMES) return@addListener
            val handler = when (blockName) {
                "minecraft:blast_furnace" -> blastFurnaceHandler
                "minecraft:smoker" -> smokerHandler
                else -> furnaceHandler
            }
            event.setBlock(event.block.withHandler(handler))
        }

        return node
    }
}
