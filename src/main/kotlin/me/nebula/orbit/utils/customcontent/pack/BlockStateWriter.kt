package me.nebula.orbit.utils.customcontent.pack

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.orbit.utils.customcontent.block.BlockHitbox
import me.nebula.orbit.utils.customcontent.block.CustomBlock
import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import net.minestom.server.instance.block.Block

object BlockStateWriter {

    private val gson = GsonProvider.pretty

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

    private val TRIPWIRE_KEYS = listOf("attached", "disarmed", "east", "north", "powered", "south", "west")

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

        if ("minecraft:tripwire" in usedBaseBlocks) {
            result["assets/minecraft/blockstates/tripwire.json"] = buildTripwireVariants(byStateId)
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
        val base = checkNotNull(Block.fromKey(blockName)) { "Unknown block key: $blockName" }
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

    private fun buildTripwireVariants(byStateId: Map<Int, CustomBlock>): ByteArray {
        val multipart = JsonArray()

        for (entry in vanillaTripwireParts()) {
            multipart.add(entry)
        }

        for (combo in 0 until 128) {
            var state = Block.TRIPWIRE
            val whenObj = JsonObject()
            TRIPWIRE_KEYS.forEachIndexed { bit, key ->
                val value = ((combo shr bit) and 1 == 1).toString()
                state = state.withProperty(key, value)
                whenObj.addProperty(key, value)
            }
            val custom = byStateId[state.stateId()] ?: continue
            multipart.add(JsonObject().apply {
                add("when", whenObj)
                add("apply", JsonObject().apply { addProperty("model", "customcontent/blocks/${custom.id}") })
            })
        }

        return toBytes(JsonObject().apply { add("multipart", multipart) })
    }

    private fun vanillaTripwireParts(): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        for (attached in listOf("false", "true")) {
            val prefix = if (attached == "true") "block/tripwire_attached" else "block/tripwire"
            data class Entry(val model: String, val rotation: Int?, val e: String, val n: String, val s: String, val w: String)
            val entries = listOf(
                Entry("${prefix}_ns", null, "false", "false", "false", "false"),
                Entry("${prefix}_ns", 90,   "true",  "false", "false", "true"),
                Entry("${prefix}_ns", null, "false", "true",  "true",  "false"),
                Entry("${prefix}_ne", null, "true",  "true",  "false", "false"),
                Entry("${prefix}_ne", 270,  "false", "true",  "false", "true"),
                Entry("${prefix}_ne", 180,  "false", "false", "true",  "true"),
                Entry("${prefix}_ne", 90,   "true",  "false", "true",  "false"),
                Entry("${prefix}_nse", null, "true",  "true",  "true",  "false"),
                Entry("${prefix}_nse", 270, "false", "true",  "true",  "true"),
                Entry("${prefix}_nse", 180, "true",  "false", "true",  "true"),
                Entry("${prefix}_nse", 90,  "true",  "true",  "false", "true"),
                Entry("${prefix}_nsew", null,"true", "true",  "true",  "true"),
                Entry("${prefix}_n", 90,   "true",  "false", "false", "false"),
                Entry("${prefix}_n", null, "false", "true",  "false", "false"),
                Entry("${prefix}_n", 180,  "false", "false", "true",  "false"),
                Entry("${prefix}_n", 270,  "false", "false", "false", "true"),
            )
            for (e in entries) {
                val whenObj = JsonObject().apply {
                    addProperty("attached", attached)
                    addProperty("east", e.e)
                    addProperty("north", e.n)
                    addProperty("south", e.s)
                    addProperty("west", e.w)
                }
                val applyObj = JsonObject().apply {
                    addProperty("model", e.model)
                    if (e.rotation != null) addProperty("y", e.rotation)
                }
                out += JsonObject().apply {
                    add("when", whenObj)
                    add("apply", applyObj)
                }
            }
        }
        return out
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
