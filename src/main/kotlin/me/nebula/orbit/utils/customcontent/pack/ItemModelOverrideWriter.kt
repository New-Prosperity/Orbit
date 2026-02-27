package me.nebula.orbit.utils.customcontent.pack

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minestom.server.item.Material

object ItemModelOverrideWriter {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class OverrideEntry(val customModelData: Int, val modelPath: String)

    fun generate(overrides: Map<Material, List<OverrideEntry>>): Map<String, ByteArray> =
        overrides.mapNotNull { (material, entries) ->
            if (entries.isEmpty()) return@mapNotNull null
            val sorted = entries.sortedBy { it.customModelData }
            val materialKey = material.key().value()
            val path = "assets/minecraft/items/$materialKey.json"
            val json = buildItemDefinition(materialKey, sorted)
            path to gson.toJson(json).toByteArray(Charsets.UTF_8)
        }.toMap()

    private fun buildItemDefinition(materialKey: String, entries: List<OverrideEntry>): JsonObject =
        JsonObject().apply {
            add("model", JsonObject().apply {
                addProperty("type", "minecraft:range_dispatch")
                addProperty("property", "minecraft:custom_model_data")
                addProperty("index", 0)
                addProperty("scale", 1.0)
                add("fallback", JsonObject().apply {
                    addProperty("type", "minecraft:model")
                    addProperty("model", "minecraft:item/$materialKey")
                })
                add("entries", JsonArray().apply {
                    entries.forEach { entry ->
                        add(JsonObject().apply {
                            addProperty("threshold", entry.customModelData.toDouble())
                            add("model", JsonObject().apply {
                                addProperty("type", "minecraft:model")
                                addProperty("model", entry.modelPath)
                            })
                        })
                    }
                })
            })
        }
}
