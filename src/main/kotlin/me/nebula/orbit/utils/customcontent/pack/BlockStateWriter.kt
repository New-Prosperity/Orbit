package me.nebula.orbit.utils.customcontent.pack

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.nebula.orbit.utils.customcontent.block.BlockHitbox
import me.nebula.orbit.utils.customcontent.block.CustomBlock
import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import net.minestom.server.instance.block.Block

object BlockStateWriter {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val INSTRUMENTS = listOf(
        "banjo", "bass", "basedrum", "bell", "bit", "chime", "cow_bell",
        "didgeridoo", "flute", "guitar", "harp", "hat", "iron_xylophone",
        "pling", "snare", "xylophone",
    )

    private val MUSHROOM_FACES = listOf("down", "east", "north", "south", "up", "west")

    private val MUSHROOM_BLOCKS = mapOf(
        "minecraft:brown_mushroom_block" to "block/brown_mushroom_block",
        "minecraft:red_mushroom_block" to "block/red_mushroom_block",
        "minecraft:mushroom_stem" to "block/mushroom_stem",
    )

    fun generate(): Map<String, ByteArray> {
        val allBlocks = CustomBlockRegistry.all()
        if (allBlocks.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, ByteArray>()
        val byStateId = allBlocks.associateBy { it.allocatedState.stateId() }
        val usedBaseBlocks = allBlocks.map { it.allocatedState.name() }.toSet()

        if ("minecraft:note_block" in usedBaseBlocks) {
            result["assets/minecraft/blockstates/note_block.json"] = buildNoteBlockVariants(byStateId)
        }

        for ((blockName, vanillaModel) in MUSHROOM_BLOCKS) {
            if (blockName in usedBaseBlocks) {
                val name = blockName.removePrefix("minecraft:")
                result["assets/minecraft/blockstates/$name.json"] = buildMushroomVariants(blockName, vanillaModel, byStateId)
            }
        }

        allBlocks.filter { it.hitbox == BlockHitbox.Thin }.forEach { customBlock ->
            val name = customBlock.allocatedState.name().removePrefix("minecraft:")
            result["assets/minecraft/blockstates/$name.json"] = buildSingleVariant("customcontent/blocks/${customBlock.id}")
        }

        return result
    }

    private fun buildNoteBlockVariants(byStateId: Map<Int, CustomBlock>): ByteArray {
        val variants = JsonObject()
        for (instrument in INSTRUMENTS) {
            for (note in 0..24) {
                for (powered in listOf("false", "true")) {
                    val state = Block.NOTE_BLOCK
                        .withProperty("instrument", instrument)
                        .withProperty("note", note.toString())
                        .withProperty("powered", powered)
                    val key = "instrument=$instrument,note=$note,powered=$powered"
                    val custom = byStateId[state.stateId()]
                    val model = custom?.let { "customcontent/blocks/${it.id}" } ?: "block/note_block"
                    variants.add(key, JsonObject().apply { addProperty("model", model) })
                }
            }
        }
        return toBytes(JsonObject().apply { add("variants", variants) })
    }

    private fun buildMushroomVariants(
        blockName: String,
        vanillaModel: String,
        byStateId: Map<Int, CustomBlock>,
    ): ByteArray {
        val base = Block.fromKey(blockName)!!
        val variants = JsonObject()
        for (combo in 0 until 64) {
            var state = base
            val props = mutableListOf<String>()
            MUSHROOM_FACES.forEachIndexed { bit, face ->
                val value = ((combo shr bit) and 1 == 1).toString()
                state = state.withProperty(face, value)
                props += "$face=$value"
            }
            val key = props.joinToString(",")
            val custom = byStateId[state.stateId()]
            val model = custom?.let { "customcontent/blocks/${it.id}" } ?: vanillaModel
            variants.add(key, JsonObject().apply { addProperty("model", model) })
        }
        return toBytes(JsonObject().apply { add("variants", variants) })
    }

    private fun buildSingleVariant(modelPath: String): ByteArray =
        toBytes(JsonObject().apply {
            add("variants", JsonObject().apply {
                add("", JsonObject().apply { addProperty("model", modelPath) })
            })
        })

    private fun toBytes(json: JsonObject): ByteArray =
        gson.toJson(json).toByteArray(Charsets.UTF_8)
}
