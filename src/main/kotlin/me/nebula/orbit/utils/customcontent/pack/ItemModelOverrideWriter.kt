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
            val path = "assets/minecraft/models/item/$materialKey.json"
            val json = buildOverrideJson(materialKey, sorted)
            path to gson.toJson(json).toByteArray(Charsets.UTF_8)
        }.toMap()

    private fun buildOverrideJson(materialKey: String, entries: List<OverrideEntry>): JsonObject =
        JsonObject().apply {
            addProperty("parent", "minecraft:item/generated")
            add("textures", JsonObject().apply {
                addProperty("layer0", "minecraft:item/$materialKey")
            })
            add("overrides", JsonArray().apply {
                entries.forEach { entry ->
                    add(JsonObject().apply {
                        add("predicate", JsonObject().apply {
                            addProperty("custom_model_data", entry.customModelData)
                        })
                        addProperty("model", entry.modelPath)
                    })
                }
            })
        }
}
