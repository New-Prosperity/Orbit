package me.nebula.orbit.mechanic.suspicioussand

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import kotlin.random.Random

private val SUSPICIOUS_BLOCKS = setOf("minecraft:suspicious_sand", "minecraft:suspicious_gravel")

private val ARCHAEOLOGY_LOOT = listOf(
    Material.BRICK, Material.EMERALD, Material.WHEAT_SEEDS,
    Material.CLAY_BALL, Material.STICK, Material.GOLD_NUGGET,
    Material.COAL, Material.IRON_NUGGET, Material.FLINT,
)

class SuspiciousSandModule : OrbitModule("suspicious-sand") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() !in SUSPICIOUS_BLOCKS) return@addListener
            if (event.player.getItemInMainHand().material() != Material.BRUSH) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val dusted = event.block.getProperty("dusted")?.toIntOrNull() ?: 0

            if (dusted < 3) {
                instance.setBlock(pos, event.block.withProperty("dusted", (dusted + 1).toString()))
            } else {
                val replacement = if (event.block.name() == "minecraft:suspicious_sand") Block.SAND else Block.GRAVEL
                instance.setBlock(pos, replacement)

                val loot = ARCHAEOLOGY_LOOT[Random.nextInt(ARCHAEOLOGY_LOOT.size)]
                val drop = ItemEntity(ItemStack.of(loot))
                drop.setInstance(instance, net.minestom.server.coordinate.Vec(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5))
                drop.setPickupDelay(java.time.Duration.ofMillis(500))
            }
        }
    }
}
