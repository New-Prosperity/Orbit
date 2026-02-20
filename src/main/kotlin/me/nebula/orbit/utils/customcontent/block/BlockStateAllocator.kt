package me.nebula.orbit.utils.customcontent.block

import me.nebula.ether.utils.resource.ResourceManager
import net.minestom.server.instance.block.Block
import java.util.concurrent.ConcurrentHashMap

object BlockStateAllocator {

    private val allocations = ConcurrentHashMap<String, AllocationEntry>()
    private val allocatedStateIds = ConcurrentHashMap.newKeySet<Int>()
    private val stateIdToBlockId = ConcurrentHashMap<Int, String>()
    private val pools = LinkedHashMap<BlockHitbox, List<Block>>()
    private val poolNextIndex = ConcurrentHashMap<BlockHitbox, Int>()
    private lateinit var resources: ResourceManager
    private lateinit var filePath: String

    data class AllocationEntry(val hitbox: BlockHitbox, val poolIndex: Int, val state: Block)

    fun init(resources: ResourceManager, path: String) {
        this.resources = resources
        this.filePath = path
        buildPools()
        if (resources.exists(path)) {
            resources.readLines(path).forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size != 2) return@forEach
                val id = parts[0].trim()
                val valueParts = parts[1].trim().split(":", limit = 2)
                if (valueParts.size != 2) return@forEach
                val hitbox = runCatching { BlockHitbox.fromString(valueParts[0]) }.getOrNull() ?: return@forEach
                val poolIndex = valueParts[1].toIntOrNull() ?: return@forEach
                val pool = pools[hitbox] ?: return@forEach
                if (poolIndex >= pool.size) return@forEach
                val state = pool[poolIndex]
                allocations[id] = AllocationEntry(hitbox, poolIndex, state)
                allocatedStateIds += state.stateId()
                stateIdToBlockId[state.stateId()] = id
                val current = poolNextIndex.getOrDefault(hitbox, 0)
                if (poolIndex >= current) poolNextIndex[hitbox] = poolIndex + 1
            }
        }
    }

    fun allocate(customBlockId: String, hitbox: BlockHitbox): Block {
        allocations[customBlockId]?.let { existing ->
            require(existing.hitbox == hitbox) {
                "Block $customBlockId already allocated with hitbox ${existing.hitbox.name}, cannot re-allocate as ${hitbox.name}"
            }
            return existing.state
        }
        val pool = pools[hitbox] ?: error("No pool for hitbox type: ${hitbox.name}")
        val index = poolNextIndex.getOrDefault(hitbox, 0)
        require(index < pool.size) { "Pool exhausted for hitbox ${hitbox.name}: used $index/${pool.size}" }
        val state = pool[index]
        poolNextIndex[hitbox] = index + 1
        allocations[customBlockId] = AllocationEntry(hitbox, index, state)
        allocatedStateIds += state.stateId()
        stateIdToBlockId[state.stateId()] = customBlockId
        save()
        return state
    }

    fun fromVanillaBlock(block: Block): String? =
        stateIdToBlockId[block.stateId()]

    fun isAllocated(block: Block): Boolean =
        allocatedStateIds.contains(block.stateId())

    fun allAllocations(): Map<String, AllocationEntry> = allocations.toMap()

    fun poolSize(hitbox: BlockHitbox): Int = pools[hitbox]?.size ?: 0

    fun poolUsed(hitbox: BlockHitbox): Int = poolNextIndex.getOrDefault(hitbox, 0)

    private fun save() {
        val content = allocations.entries
            .sortedBy { it.key }
            .joinToString("\n") { "${it.key}=${it.value.hitbox.name}:${it.value.poolIndex}" }
        resources.writeText(filePath, content)
    }

    private fun buildPools() {
        pools[BlockHitbox.Full] = buildFullPool()
        pools[BlockHitbox.Slab] = buildCanonicalPool(SLAB_MATERIALS) {
            it.withProperty("type", "bottom").withProperty("waterlogged", "false")
        }
        pools[BlockHitbox.Stair] = buildCanonicalPool(STAIR_MATERIALS) {
            it.withProperty("facing", "north")
                .withProperty("half", "bottom")
                .withProperty("shape", "straight")
                .withProperty("waterlogged", "false")
        }
        pools[BlockHitbox.Thin] = buildCanonicalPool(CARPET_MATERIALS) { it }
        pools[BlockHitbox.Transparent] = buildTransparentPool()
        pools[BlockHitbox.Wall] = buildCanonicalPool(WALL_MATERIALS) {
            it.withProperty("up", "true")
                .withProperty("north", "none")
                .withProperty("south", "none")
                .withProperty("east", "none")
                .withProperty("west", "none")
                .withProperty("waterlogged", "false")
        }
        pools[BlockHitbox.Fence] = buildCanonicalPool(FENCE_MATERIALS) {
            it.withProperty("north", "false")
                .withProperty("south", "false")
                .withProperty("east", "false")
                .withProperty("west", "false")
                .withProperty("waterlogged", "false")
        }
        pools[BlockHitbox.Trapdoor] = buildCanonicalPool(TRAPDOOR_MATERIALS) {
            it.withProperty("facing", "north")
                .withProperty("half", "bottom")
                .withProperty("open", "false")
                .withProperty("powered", "false")
                .withProperty("waterlogged", "false")
        }
        pools.keys.forEach { poolNextIndex.putIfAbsent(it, 0) }
    }

    private fun buildFullPool(): List<Block> = buildList {
        val instruments = listOf(
            "banjo", "bass", "basedrum", "bell", "bit", "chime", "cow_bell",
            "didgeridoo", "flute", "guitar", "harp", "hat", "iron_xylophone",
            "pling", "snare", "xylophone",
        )
        for (instrument in instruments) {
            for (note in 0..24) {
                add(
                    Block.NOTE_BLOCK
                        .withProperty("instrument", instrument)
                        .withProperty("note", note.toString())
                        .withProperty("powered", "false")
                )
            }
        }
        val mushroomBases = listOf(Block.BROWN_MUSHROOM_BLOCK, Block.RED_MUSHROOM_BLOCK, Block.MUSHROOM_STEM)
        val faces = listOf("up", "down", "north", "south", "east", "west")
        for (base in mushroomBases) {
            for (combo in 0 until 64) {
                var state = base
                faces.forEachIndexed { bit, face ->
                    state = state.withProperty(face, ((combo shr bit) and 1 == 1).toString())
                }
                add(state)
            }
        }
    }

    private fun buildTransparentPool(): List<Block> = buildList {
        val keys = listOf("attached", "disarmed", "east", "north", "powered", "south", "west")
        for (combo in 0 until 128) {
            var state = Block.TRIPWIRE
            keys.forEachIndexed { bit, key ->
                state = state.withProperty(key, ((combo shr bit) and 1 == 1).toString())
            }
            add(state)
        }
    }

    private fun buildCanonicalPool(
        materials: List<String>,
        stateMapper: (Block) -> Block,
    ): List<Block> = materials.mapNotNull { name ->
        Block.fromKey("minecraft:$name")?.let(stateMapper)
    }

    private val SLAB_MATERIALS = listOf(
        "oak_slab", "spruce_slab", "birch_slab", "jungle_slab", "acacia_slab",
        "dark_oak_slab", "mangrove_slab", "cherry_slab", "bamboo_slab",
        "crimson_slab", "warped_slab", "stone_slab", "smooth_stone_slab",
        "sandstone_slab", "cut_sandstone_slab", "red_sandstone_slab",
        "cut_red_sandstone_slab", "cobblestone_slab", "mossy_cobblestone_slab",
        "stone_brick_slab", "mossy_stone_brick_slab", "granite_slab",
        "polished_granite_slab", "diorite_slab", "polished_diorite_slab",
        "andesite_slab", "polished_andesite_slab", "cobbled_deepslate_slab",
        "polished_deepslate_slab", "deepslate_brick_slab", "deepslate_tile_slab",
        "brick_slab", "mud_brick_slab", "nether_brick_slab",
        "red_nether_brick_slab", "quartz_slab", "smooth_quartz_slab",
        "purpur_slab", "prismarine_slab", "prismarine_brick_slab",
        "dark_prismarine_slab", "blackstone_slab", "polished_blackstone_slab",
        "polished_blackstone_brick_slab", "end_stone_brick_slab",
        "oxidized_cut_copper_slab", "weathered_cut_copper_slab",
        "exposed_cut_copper_slab", "cut_copper_slab",
        "waxed_oxidized_cut_copper_slab", "waxed_weathered_cut_copper_slab",
        "waxed_exposed_cut_copper_slab", "waxed_cut_copper_slab",
    )

    private val STAIR_MATERIALS = listOf(
        "oak_stairs", "spruce_stairs", "birch_stairs", "jungle_stairs", "acacia_stairs",
        "dark_oak_stairs", "mangrove_stairs", "cherry_stairs", "bamboo_stairs",
        "bamboo_mosaic_stairs", "crimson_stairs", "warped_stairs",
        "stone_stairs", "sandstone_stairs", "red_sandstone_stairs",
        "cobblestone_stairs", "mossy_cobblestone_stairs",
        "stone_brick_stairs", "mossy_stone_brick_stairs",
        "granite_stairs", "polished_granite_stairs",
        "diorite_stairs", "polished_diorite_stairs",
        "andesite_stairs", "polished_andesite_stairs",
        "cobbled_deepslate_stairs", "polished_deepslate_stairs",
        "deepslate_brick_stairs", "deepslate_tile_stairs",
        "brick_stairs", "mud_brick_stairs",
        "nether_brick_stairs", "red_nether_brick_stairs",
        "quartz_stairs", "smooth_quartz_stairs",
        "purpur_stairs", "prismarine_stairs", "prismarine_brick_stairs",
        "dark_prismarine_stairs", "blackstone_stairs",
        "polished_blackstone_stairs", "polished_blackstone_brick_stairs",
        "end_stone_brick_stairs",
        "oxidized_cut_copper_stairs", "weathered_cut_copper_stairs",
        "exposed_cut_copper_stairs", "cut_copper_stairs",
        "waxed_oxidized_cut_copper_stairs", "waxed_weathered_cut_copper_stairs",
        "waxed_exposed_cut_copper_stairs", "waxed_cut_copper_stairs",
    )

    private val CARPET_MATERIALS = listOf(
        "white_carpet", "orange_carpet", "magenta_carpet", "light_blue_carpet",
        "yellow_carpet", "lime_carpet", "pink_carpet", "gray_carpet",
        "light_gray_carpet", "cyan_carpet", "purple_carpet", "blue_carpet",
        "brown_carpet", "green_carpet", "red_carpet", "black_carpet", "moss_carpet",
    )

    private val WALL_MATERIALS = listOf(
        "cobblestone_wall", "mossy_cobblestone_wall",
        "stone_brick_wall", "mossy_stone_brick_wall",
        "granite_wall", "diorite_wall", "andesite_wall",
        "sandstone_wall", "red_sandstone_wall",
        "brick_wall", "nether_brick_wall", "red_nether_brick_wall",
        "end_stone_brick_wall", "blackstone_wall",
        "polished_blackstone_wall", "polished_blackstone_brick_wall",
        "cobbled_deepslate_wall", "polished_deepslate_wall",
        "deepslate_brick_wall", "deepslate_tile_wall",
        "mud_brick_wall", "prismarine_wall",
    )

    private val FENCE_MATERIALS = listOf(
        "oak_fence", "spruce_fence", "birch_fence", "jungle_fence",
        "acacia_fence", "dark_oak_fence", "mangrove_fence", "cherry_fence",
        "bamboo_fence", "crimson_fence", "warped_fence", "nether_brick_fence",
    )

    private val TRAPDOOR_MATERIALS = listOf(
        "oak_trapdoor", "spruce_trapdoor", "birch_trapdoor", "jungle_trapdoor",
        "acacia_trapdoor", "dark_oak_trapdoor", "mangrove_trapdoor",
        "cherry_trapdoor", "bamboo_trapdoor", "crimson_trapdoor", "warped_trapdoor",
    )
}
