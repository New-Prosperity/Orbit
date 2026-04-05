package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.time.Duration
import kotlin.random.Random

private val BLOCK_DROP_MAP: Map<String, Material> by lazy {
    val map = HashMap<String, Material>()
    for (material in Material.values()) {
        val block = Block.fromKey(material.key())
        if (block != null) {
            map[block.name()] = material
        }
    }
    map["minecraft:stone"] = Material.COBBLESTONE
    map["minecraft:grass_block"] = Material.DIRT
    map["minecraft:coal_ore"] = Material.COAL
    map["minecraft:deepslate_coal_ore"] = Material.COAL
    map["minecraft:iron_ore"] = Material.RAW_IRON
    map["minecraft:deepslate_iron_ore"] = Material.RAW_IRON
    map["minecraft:copper_ore"] = Material.RAW_COPPER
    map["minecraft:deepslate_copper_ore"] = Material.RAW_COPPER
    map["minecraft:gold_ore"] = Material.RAW_GOLD
    map["minecraft:deepslate_gold_ore"] = Material.RAW_GOLD
    map["minecraft:diamond_ore"] = Material.DIAMOND
    map["minecraft:deepslate_diamond_ore"] = Material.DIAMOND
    map["minecraft:lapis_ore"] = Material.LAPIS_LAZULI
    map["minecraft:deepslate_lapis_ore"] = Material.LAPIS_LAZULI
    map["minecraft:redstone_ore"] = Material.REDSTONE
    map["minecraft:deepslate_redstone_ore"] = Material.REDSTONE
    map["minecraft:emerald_ore"] = Material.EMERALD
    map["minecraft:deepslate_emerald_ore"] = Material.EMERALD
    map["minecraft:nether_quartz_ore"] = Material.QUARTZ
    map["minecraft:nether_gold_ore"] = Material.GOLD_NUGGET
    map["minecraft:glowstone"] = Material.GLOWSTONE_DUST
    map["minecraft:tall_grass"] = Material.SHORT_GRASS
    map["minecraft:large_fern"] = Material.FERN
    map.remove("minecraft:spawner")
    map.remove("minecraft:bedrock")
    map.remove("minecraft:barrier")
    map
}

private val NO_DROP_BLOCKS = setOf(
    "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
    "minecraft:fire", "minecraft:soul_fire", "minecraft:water", "minecraft:lava",
    "minecraft:moving_piston", "minecraft:piston_head",
    "minecraft:nether_portal", "minecraft:end_portal",
    "minecraft:frosted_ice", "minecraft:budding_amethyst",
    "minecraft:infested_stone", "minecraft:infested_cobblestone",
    "minecraft:infested_stone_bricks", "minecraft:infested_mossy_stone_bricks",
    "minecraft:infested_cracked_stone_bricks", "minecraft:infested_chiseled_stone_bricks",
    "minecraft:infested_deepslate",
)

private val REQUIRES_PICKAXE = setOf(
    "minecraft:stone", "minecraft:cobblestone", "minecraft:mossy_cobblestone",
    "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
    "minecraft:deepslate", "minecraft:cobbled_deepslate",
    "minecraft:tuff", "minecraft:calcite", "minecraft:dripstone_block",
    "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
    "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
    "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
    "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
    "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
    "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
    "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
    "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
    "minecraft:nether_quartz_ore", "minecraft:nether_gold_ore",
    "minecraft:ancient_debris", "minecraft:obsidian", "minecraft:crying_obsidian",
    "minecraft:netherrack", "minecraft:basalt", "minecraft:smooth_basalt",
    "minecraft:blackstone", "minecraft:end_stone",
    "minecraft:stone_bricks", "minecraft:mossy_stone_bricks",
    "minecraft:cracked_stone_bricks", "minecraft:chiseled_stone_bricks",
    "minecraft:bricks", "minecraft:nether_bricks", "minecraft:red_nether_bricks",
    "minecraft:prismarine", "minecraft:dark_prismarine", "minecraft:prismarine_bricks",
    "minecraft:sandstone", "minecraft:red_sandstone",
    "minecraft:smooth_stone", "minecraft:smooth_sandstone", "minecraft:smooth_red_sandstone",
    "minecraft:terracotta", "minecraft:concrete",
    "minecraft:iron_block", "minecraft:gold_block", "minecraft:diamond_block",
    "minecraft:netherite_block", "minecraft:emerald_block", "minecraft:lapis_block",
    "minecraft:redstone_block", "minecraft:copper_block",
    "minecraft:raw_iron_block", "minecraft:raw_copper_block", "minecraft:raw_gold_block",
    "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
    "minecraft:stonecutter", "minecraft:grindstone",
    "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil",
    "minecraft:brewing_stand", "minecraft:cauldron",
    "minecraft:enchanting_table", "minecraft:lodestone",
    "minecraft:respawn_anchor",
)

private val REQUIRES_IRON_PICKAXE = setOf(
    "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
    "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
    "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
    "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
)

private val REQUIRES_DIAMOND_PICKAXE = setOf(
    "minecraft:obsidian", "minecraft:crying_obsidian",
    "minecraft:ancient_debris", "minecraft:netherite_block",
    "minecraft:respawn_anchor",
)

private val PICKAXES = setOf(
    Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
    Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
)

private val IRON_TIER_PICKAXES = setOf(
    Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
)

private val DIAMOND_TIER_PICKAXES = setOf(
    Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
)

private val REQUIRES_SHEARS = setOf(
    "minecraft:cobweb", "minecraft:vine",
    "minecraft:glow_lichen", "minecraft:dead_bush",
)

private val MULTI_DROP_BLOCKS = mapOf(
    "minecraft:lapis_ore" to (4 to 9),
    "minecraft:deepslate_lapis_ore" to (4 to 9),
    "minecraft:redstone_ore" to (4 to 5),
    "minecraft:deepslate_redstone_ore" to (4 to 5),
    "minecraft:nether_gold_ore" to (2 to 6),
    "minecraft:glowstone" to (2 to 4),
)

object BlockDropsModule : VanillaModule {

    override val id = "block-drops"
    override val description = "Blocks drop items when broken with correct tool (vanilla requirements)"
    override val configParams = listOf(
        ConfigParam.BoolParam("enabled", "Drop items from broken blocks", true),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-block-drops")

        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.player.gameMode == GameMode.CREATIVE) return@addListener

            val block = event.block
            val blockName = block.name()
            if (blockName in NO_DROP_BLOCKS) return@addListener

            val tool = event.player.itemInMainHand.material()

            if (blockName in REQUIRES_DIAMOND_PICKAXE && tool !in DIAMOND_TIER_PICKAXES) return@addListener
            if (blockName in REQUIRES_IRON_PICKAXE && tool !in IRON_TIER_PICKAXES) return@addListener
            if (blockName in REQUIRES_PICKAXE && tool !in PICKAXES) return@addListener
            if (blockName in REQUIRES_SHEARS && tool != Material.SHEARS) return@addListener

            val dropMaterial = BLOCK_DROP_MAP[blockName] ?: return@addListener

            val dropCount = MULTI_DROP_BLOCKS[blockName]?.let { (min, max) ->
                Random.nextInt(min, max + 1)
            } ?: 1

            val dropItem = ItemStack.of(dropMaterial, dropCount)
            val pos = event.blockPosition
            val itemEntity = ItemEntity(dropItem)
            itemEntity.setPickupDelay(Duration.ofMillis(500))
            itemEntity.velocity = Vec(
                (Random.nextDouble() - 0.5) * 2,
                Random.nextDouble() * 3 + 1,
                (Random.nextDouble() - 0.5) * 2,
            )
            itemEntity.setInstance(event.instance, Pos(pos.blockX() + 0.5, pos.blockY() + 0.25, pos.blockZ() + 0.5))
        }

        return node
    }
}
