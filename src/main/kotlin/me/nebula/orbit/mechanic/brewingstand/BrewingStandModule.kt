package me.nebula.orbit.mechanic.brewingstand

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translateDefault
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private data class BrewKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val INGREDIENTS = setOf(
    Material.NETHER_WART, Material.REDSTONE, Material.GLOWSTONE_DUST,
    Material.FERMENTED_SPIDER_EYE, Material.GUNPOWDER, Material.DRAGON_BREATH,
    Material.SUGAR, Material.RABBIT_FOOT, Material.GLISTERING_MELON_SLICE,
    Material.SPIDER_EYE, Material.PUFFERFISH, Material.MAGMA_CREAM,
    Material.GOLDEN_CARROT, Material.BLAZE_POWDER, Material.GHAST_TEAR,
    Material.TURTLE_HELMET, Material.PHANTOM_MEMBRANE,
)

class BrewingStandModule : OrbitModule("brewing-stand") {

    private val inventories = ConcurrentHashMap<BrewKey, Inventory>()
    private val brewTimers = ConcurrentHashMap<BrewKey, Int>()

    override fun onEnable() {
        super.onEnable()
        inventories.cleanOnInstanceRemove { it.instanceHash }
        brewTimers.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:brewing_stand") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = BrewKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val inv = inventories.getOrPut(key) {
                Inventory(InventoryType.BREWING_STAND, translateDefault("orbit.mechanic.brewing_stand.title"))
            }
            event.player.openInventory(inv)
        }

        MinecraftServer.getSchedulerManager().buildTask {
            for ((key, inv) in inventories) {
                val ingredient = inv.getItemStack(3)
                val fuel = inv.getItemStack(4)

                if (ingredient.isAir || ingredient.material() !in INGREDIENTS) {
                    brewTimers.remove(key)
                    continue
                }

                val hasBottle = (0..2).any { !inv.getItemStack(it).isAir }
                if (!hasBottle) {
                    brewTimers.remove(key)
                    continue
                }

                val ticks = brewTimers.getOrPut(key) { 400 }
                if (ticks <= 0) {
                    inv.setItemStack(3, ingredient.consume(1))
                    brewTimers.remove(key)
                } else {
                    brewTimers[key] = ticks - 1
                }
            }
        }.repeat(TaskSchedule.tick(1)).schedule()
    }

    override fun onDisable() {
        inventories.clear()
        brewTimers.clear()
        super.onDisable()
    }
}
