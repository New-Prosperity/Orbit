package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import me.nebula.orbit.utils.particle.spawnParticleAt
import me.nebula.orbit.utils.sound.playSound
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import kotlin.random.Random

private val CROP_BLOCKS = mapOf(
    "minecraft:wheat" to 7,
    "minecraft:carrots" to 7,
    "minecraft:potatoes" to 7,
    "minecraft:beetroots" to 3,
    "minecraft:nether_wart" to 3,
    "minecraft:sweet_berry_bush" to 3,
    "minecraft:torchflower_crop" to 1,
)

private val SAPLING_NAMES = setOf(
    "minecraft:oak_sapling", "minecraft:spruce_sapling", "minecraft:birch_sapling",
    "minecraft:jungle_sapling", "minecraft:acacia_sapling", "minecraft:dark_oak_sapling",
    "minecraft:cherry_sapling", "minecraft:mangrove_propagule",
)

private val FLOWERS = listOf(
    Block.DANDELION, Block.POPPY, Block.BLUE_ORCHID, Block.ALLIUM,
    Block.AZURE_BLUET, Block.RED_TULIP, Block.ORANGE_TULIP,
    Block.WHITE_TULIP, Block.PINK_TULIP, Block.OXEYE_DAISY,
    Block.CORNFLOWER, Block.LILY_OF_THE_VALLEY,
)

object BoneMealModule : VanillaModule {

    override val id = "bone-meal"
    override val description = "Bone meal grows crops, saplings, and spreads grass"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-bone-meal")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val item = event.player.itemInMainHand
            if (item.material() != Material.BONE_MEAL) return@addListener

            val blockName = event.block.name()
            val x = event.blockPosition.blockX()
            val y = event.blockPosition.blockY()
            val z = event.blockPosition.blockZ()
            var consumed = false

            when {
                blockName in CROP_BLOCKS -> consumed = growCrop(event.instance, x, y, z, event.block, blockName)
                blockName in SAPLING_NAMES -> consumed = growSapling(event.instance, x, y, z, blockName)
                blockName == "minecraft:grass_block" -> consumed = spreadGrass(event.instance, x, y, z)
                blockName == "minecraft:mushroom_stem" || blockName == "minecraft:brown_mushroom" || blockName == "minecraft:red_mushroom" -> consumed = true
            }

            if (consumed) {
                if (event.player.gameMode != GameMode.CREATIVE) {
                    event.player.setItemInMainHand(item.consume(1))
                }
                event.instance.spawnParticleAt(Particle.HAPPY_VILLAGER, event.blockPosition.add(0.5, 0.5, 0.5), count = 10, spread = 0.5f)
                event.player.playSound(SoundEvent.ITEM_BONE_MEAL_USE)
            }
        }

        return node
    }

    private fun growCrop(instance: Instance, x: Int, y: Int, z: Int, block: Block, blockName: String): Boolean {
        val maxAge = CROP_BLOCKS[blockName] ?: return false
        val currentAge = block.getProperty("age")?.toIntOrNull() ?: 0
        if (currentAge >= maxAge) return false
        val newAge = (currentAge + Random.nextInt(2, 6)).coerceAtMost(maxAge)
        instance.setBlock(x, y, z, block.withProperty("age", newAge.toString()))
        return true
    }

    private fun growSapling(instance: Instance, x: Int, y: Int, z: Int, blockName: String): Boolean {
        val logBlock = when {
            blockName.contains("oak") -> Block.OAK_LOG
            blockName.contains("spruce") -> Block.SPRUCE_LOG
            blockName.contains("birch") -> Block.BIRCH_LOG
            blockName.contains("jungle") -> Block.JUNGLE_LOG
            blockName.contains("acacia") -> Block.ACACIA_LOG
            blockName.contains("dark_oak") -> Block.DARK_OAK_LOG
            blockName.contains("cherry") -> Block.CHERRY_LOG
            blockName.contains("mangrove") -> Block.MANGROVE_LOG
            else -> Block.OAK_LOG
        }
        val leafBlock = when {
            blockName.contains("oak") -> Block.OAK_LEAVES
            blockName.contains("spruce") -> Block.SPRUCE_LEAVES
            blockName.contains("birch") -> Block.BIRCH_LEAVES
            blockName.contains("jungle") -> Block.JUNGLE_LEAVES
            blockName.contains("acacia") -> Block.ACACIA_LEAVES
            blockName.contains("dark_oak") -> Block.DARK_OAK_LEAVES
            blockName.contains("cherry") -> Block.CHERRY_LEAVES
            blockName.contains("mangrove") -> Block.MANGROVE_LEAVES
            else -> Block.OAK_LEAVES
        }

        val height = Random.nextInt(4, 7)
        instance.setBlock(x, y, z, Block.AIR)
        for (dy in 0 until height) {
            instance.setBlock(x, y + dy, z, logBlock)
        }
        val leafStart = height - 3
        for (dy in leafStart..height) {
            val radius = if (dy >= height - 1) 1 else 2
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (dx == 0 && dz == 0 && dy < height) continue
                    if (dx * dx + dz * dz > radius * radius + 1) continue
                    val lx = x + dx
                    val ly = y + dy
                    val lz = z + dz
                    if (instance.getBlock(lx, ly, lz).isAir) {
                        instance.setBlock(lx, ly, lz, leafBlock.withProperty("persistent", "true"))
                    }
                }
            }
        }
        return true
    }

    private fun spreadGrass(instance: Instance, x: Int, y: Int, z: Int): Boolean {
        var placed = false
        for (i in 0 until 16) {
            val dx = x + Random.nextInt(-3, 4)
            val dz = z + Random.nextInt(-3, 4)
            val dy = y + 1
            val above = instance.getBlock(dx, dy, dz)
            val below = instance.getBlock(dx, dy - 1, dz)
            if (!above.isAir || below.name() != "minecraft:grass_block") continue

            val roll = Random.nextInt(10)
            val block = when {
                roll < 6 -> Block.SHORT_GRASS
                roll < 8 -> Block.TALL_GRASS
                roll < 9 -> FLOWERS[Random.nextInt(FLOWERS.size)]
                else -> Block.FERN
            }
            instance.setBlock(dx, dy, dz, block)
            placed = true
        }
        return placed
    }

}
