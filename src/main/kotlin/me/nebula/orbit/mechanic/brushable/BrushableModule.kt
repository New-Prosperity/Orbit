package me.nebula.orbit.mechanic.brushable

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class BrushKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val ARCHAEOLOGY_LOOT = listOf(
    Material.BRICK,
    Material.EMERALD,
    Material.WHEAT_SEEDS,
    Material.CLAY_BALL,
    Material.STICK,
    Material.GOLD_NUGGET,
    Material.COAL,
    Material.IRON_NUGGET,
    Material.FLINT,
    Material.DIAMOND,
    Material.LAPIS_LAZULI,
)

class BrushableModule : OrbitModule("brushable") {

    private val brushProgress = ConcurrentHashMap<BrushKey, Int>()

    override fun onEnable() {
        super.onEnable()
        brushProgress.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:suspicious_gravel") return@addListener
            if (event.player.getItemInMainHand().material() != Material.BRUSH) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = BrushKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val progress = brushProgress.compute(key) { _, current -> (current ?: 0) + 1 } ?: return@addListener

            if (progress < 4) {
                val dusted = event.block.getProperty("dusted")?.toIntOrNull() ?: 0
                if (dusted < 3) {
                    instance.setBlock(pos, event.block.withProperty("dusted", (dusted + 1).toString()))
                }
            } else {
                brushProgress.remove(key)
                instance.setBlock(pos, Block.GRAVEL)

                val loot = ARCHAEOLOGY_LOOT[Random.nextInt(ARCHAEOLOGY_LOOT.size)]
                val drop = ItemEntity(ItemStack.of(loot))
                drop.setInstance(instance, Vec(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5))
                drop.setPickupDelay(Duration.ofMillis(500))
            }
        }
    }

    override fun onDisable() {
        brushProgress.clear()
        super.onDisable()
    }
}
