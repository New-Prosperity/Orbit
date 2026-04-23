package me.nebula.orbit.utils.customcontent.furniture

import com.google.gson.JsonObject
import me.nebula.ether.utils.gson.GsonProvider
import net.minestom.server.instance.block.Block

object FurnitureCollisionPack {

    private val gson = GsonProvider.pretty
    private const val EMPTY_MODEL_PATH = "customcontent/furniture/empty"

    fun generate(): Map<String, ByteArray> {
        val allocations = FurnitureCollisionStates.allocations()
        if (allocations.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, ByteArray>()
        result["assets/minecraft/models/$EMPTY_MODEL_PATH.json"] = buildEmptyModel()

        val byBaseBlock = allocations.values.groupBy { it.name() }
        for ((baseName, states) in byBaseBlock) {
            val blockstateJson = buildBlockstateRedirect(baseName, states) ?: continue
            val fileName = baseName.removePrefix("minecraft:")
            result["assets/minecraft/blockstates/$fileName.json"] = blockstateJson
        }
        return result
    }

    private fun buildEmptyModel(): ByteArray {
        val json = JsonObject().apply {
            add("textures", JsonObject())
            add("elements", com.google.gson.JsonArray())
        }
        return gson.toJson(json).toByteArray(Charsets.UTF_8)
    }

    private fun buildBlockstateRedirect(baseName: String, furnitureStates: List<Block>): ByteArray? {
        val baseBlock = Block.fromKey(baseName) ?: return null
        val hiddenKeys = furnitureStates.map { propertyKey(it) }.toSet()
        val allVariantKeys = enumerateVariantKeys(baseBlock) ?: return null

        val variants = JsonObject()
        for (key in allVariantKeys) {
            val variant = JsonObject()
            if (key in hiddenKeys) {
                variant.addProperty("model", EMPTY_MODEL_PATH)
            } else {
                val vanillaModel = vanillaModelPath(baseName, key, baseBlock)
                variant.addProperty("model", vanillaModel)
            }
            variants.add(key, variant)
        }
        return toBytes(JsonObject().apply { add("variants", variants) })
    }

    private fun propertyKey(block: Block): String {
        val props = block.properties()
        if (props.isEmpty()) return ""
        return props.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }
    }

    private fun enumerateVariantKeys(base: Block): List<String>? {
        val possible = base.possibleStates()
        if (possible.isEmpty()) return listOf("")
        return possible.map { propertyKey(it) }.distinct()
    }

    private fun vanillaModelPath(baseName: String, variantKey: String, baseBlock: Block): String {
        val shortName = baseName.removePrefix("minecraft:")
        return "block/$shortName"
    }

    private fun toBytes(json: JsonObject): ByteArray =
        gson.toJson(json).toByteArray(Charsets.UTF_8)
}
