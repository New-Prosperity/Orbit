package me.nebula.orbit.utils.vanilla.modules

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

private data class BrewRecipe(val ingredient: Material, val input: Material, val output: Material)

private val BREW_RECIPES = listOf(
    BrewRecipe(Material.NETHER_WART, Material.GLASS_BOTTLE, Material.POTION),
    BrewRecipe(Material.SUGAR, Material.POTION, Material.POTION),
    BrewRecipe(Material.RABBIT_FOOT, Material.POTION, Material.POTION),
    BrewRecipe(Material.GLISTERING_MELON_SLICE, Material.POTION, Material.POTION),
    BrewRecipe(Material.SPIDER_EYE, Material.POTION, Material.POTION),
    BrewRecipe(Material.GHAST_TEAR, Material.POTION, Material.POTION),
    BrewRecipe(Material.BLAZE_POWDER, Material.POTION, Material.POTION),
    BrewRecipe(Material.MAGMA_CREAM, Material.POTION, Material.POTION),
    BrewRecipe(Material.GOLDEN_CARROT, Material.POTION, Material.POTION),
    BrewRecipe(Material.PUFFERFISH, Material.POTION, Material.POTION),
    BrewRecipe(Material.PHANTOM_MEMBRANE, Material.POTION, Material.POTION),
    BrewRecipe(Material.TURTLE_SCUTE, Material.POTION, Material.POTION),
    BrewRecipe(Material.FERMENTED_SPIDER_EYE, Material.POTION, Material.POTION),
    BrewRecipe(Material.REDSTONE, Material.POTION, Material.POTION),
    BrewRecipe(Material.GLOWSTONE_DUST, Material.POTION, Material.POTION),
    BrewRecipe(Material.GUNPOWDER, Material.POTION, Material.SPLASH_POTION),
    BrewRecipe(Material.DRAGON_BREATH, Material.SPLASH_POTION, Material.LINGERING_POTION),
)

private val VALID_INGREDIENTS = BREW_RECIPES.map { it.ingredient }.toSet()

private data class BrewState(
    val inv: Inventory,
    var fuelCharges: Int = 0,
    var brewProgress: Int = 0,
    var brewing: Boolean = false,
)

private const val BREW_TIME = 400

private class BrewingStandBlockHandler(
    private val key: Key,
    private val stands: ConcurrentHashMap<Long, BrewState>,
) : BlockHandler {

    override fun getKey(): Key = key

    override fun isTickable(): Boolean = true

    override fun onInteract(interaction: BlockHandler.Interaction): Boolean {
        if (!VanillaModules.isEnabled(interaction.instance, "brewing-stand")) return true
        val pos = interaction.blockPosition
        val packed = packBlockPos(pos.blockX(), pos.blockY(), pos.blockZ())
        val state = stands.getOrPut(packed) {
            BrewState(Inventory(InventoryType.BREWING_STAND, Component.text("Brewing Stand")))
        }
        interaction.player.openInventory(state.inv)
        return false
    }

    override fun onDestroy(destroy: BlockHandler.Destroy) {
        if (!VanillaModules.isEnabled(destroy.instance, "brewing-stand")) return
        val pos = destroy.blockPosition
        val packed = packBlockPos(pos.blockX(), pos.blockY(), pos.blockZ())
        val state = stands.remove(packed) ?: return
        dropInventoryContents(destroy.instance, state.inv, pos.blockX(), pos.blockY(), pos.blockZ())
    }

    override fun tick(tick: BlockHandler.Tick) {
        if (!VanillaModules.isEnabled(tick.instance, "brewing-stand")) return
        val pos = tick.blockPosition
        val packed = packBlockPos(pos.blockX(), pos.blockY(), pos.blockZ())
        val state = stands[packed] ?: return
        tickBrewing(state)
    }

    private fun tickBrewing(state: BrewState) {
        val inv = state.inv
        val ingredient = inv.getItemStack(3)
        if (ingredient.isAir || ingredient.material() !in VALID_INGREDIENTS) {
            state.brewing = false
            state.brewProgress = 0
            return
        }

        var hasBottle = false
        for (i in 0..2) {
            val bottle = inv.getItemStack(i)
            if (!bottle.isAir && canBrew(ingredient.material(), bottle.material())) {
                hasBottle = true
                break
            }
        }
        if (!hasBottle) {
            state.brewing = false
            state.brewProgress = 0
            return
        }

        if (state.fuelCharges <= 0) {
            val fuel = inv.getItemStack(4)
            if (fuel.material() != Material.BLAZE_POWDER || fuel.isAir) {
                state.brewing = false
                state.brewProgress = 0
                return
            }
            state.fuelCharges = 20
            inv.setItemStack(4, if (fuel.amount() > 1) fuel.withAmount(fuel.amount() - 1) else ItemStack.AIR)
        }

        state.brewing = true
        state.brewProgress++

        if (state.brewProgress >= BREW_TIME) {
            state.brewProgress = 0
            state.fuelCharges--

            for (i in 0..2) {
                val bottle = inv.getItemStack(i)
                if (bottle.isAir) continue
                val recipe = findRecipe(ingredient.material(), bottle.material()) ?: continue
                inv.setItemStack(i, ItemStack.of(recipe.output))
            }

            inv.setItemStack(3, if (ingredient.amount() > 1) ingredient.withAmount(ingredient.amount() - 1) else ItemStack.AIR)
            state.brewing = false
        }
    }

    private fun canBrew(ingredient: Material, bottle: Material): Boolean =
        BREW_RECIPES.any { it.ingredient == ingredient && it.input == bottle }

    private fun findRecipe(ingredient: Material, bottle: Material): BrewRecipe? =
        BREW_RECIPES.firstOrNull { it.ingredient == ingredient && it.input == bottle }
}

object BrewingStandModule : VanillaModule {

    override val id = "brewing-stand"
    override val description = "Brewing stand with blaze powder fuel, 400-tick brew time, potion recipes"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val stands = ConcurrentHashMap<Long, BrewState>()

        val blockManager = MinecraftServer.getBlockManager()
        val handler = BrewingStandBlockHandler(Key.key("minecraft:brewing_stand"), stands)
        blockManager.registerHandler("minecraft:brewing_stand") { handler }

        val node = EventNode.all("vanilla-brewing-stand")

        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:brewing_stand") return@addListener
            event.setBlock(event.block.withHandler(handler))
        }

        return node
    }
}
