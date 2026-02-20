package me.nebula.orbit.utils.customcontent.item

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.nebula.ether.utils.resource.ResourceManager
import net.minestom.server.item.Material

data class CustomItemDefinition(
    val id: String,
    val baseMaterial: Material,
    val displayName: String?,
    val lore: List<String>,
    val unbreakable: Boolean,
    val glowing: Boolean,
    val maxStackSize: Int,
    val modelPath: String,
)

object CustomItemLoader {

    fun load(resources: ResourceManager, path: String): CustomItemDefinition {
        val root = JsonParser.parseString(resources.readText(path)).asJsonObject
        return parse(root)
    }

    fun loadAll(resources: ResourceManager, directory: String): List<CustomItemDefinition> =
        resources.list(directory, "json").map { load(resources, it) }

    private fun parse(obj: JsonObject): CustomItemDefinition {
        val id = obj["id"].asString
        val materialKey = obj["base_material"].asString
        val material = Material.fromKey(materialKey)
            ?: error("Unknown material: $materialKey for item $id")
        val displayName = obj["display_name"]?.asString
        val lore = obj.getAsJsonArray("lore")
            ?.map { it.asString }
            ?: emptyList()
        val unbreakable = obj["unbreakable"]?.asBoolean ?: false
        val glowing = obj["glowing"]?.asBoolean ?: false
        val maxStackSize = obj["max_stack_size"]?.asInt ?: material.maxStackSize()
        val model = obj["model"].asString

        return CustomItemDefinition(
            id = id,
            baseMaterial = material,
            displayName = displayName,
            lore = lore,
            unbreakable = unbreakable,
            glowing = glowing,
            maxStackSize = maxStackSize,
            modelPath = model,
        )
    }
}

class CustomItemDsl @PublishedApi internal constructor(val id: String) {

    @PublishedApi internal var baseMaterial: Material = Material.PAPER
    @PublishedApi internal var displayName: String? = null
    @PublishedApi internal val lore = mutableListOf<String>()
    @PublishedApi internal var unbreakable = false
    @PublishedApi internal var glowing = false
    @PublishedApi internal var maxStackSize = -1
    @PublishedApi internal var modelPath: String = "$id.bbmodel"

    fun material(material: Material) { baseMaterial = material }
    fun name(name: String) { displayName = name }
    fun lore(line: String) { lore += line }
    fun lore(lines: List<String>) { lore += lines }
    fun unbreakable() { unbreakable = true }
    fun glowing() { glowing = true }
    fun maxStackSize(size: Int) { maxStackSize = size }
    fun model(path: String) { modelPath = path }

    @PublishedApi internal fun toDefinition(): CustomItemDefinition = CustomItemDefinition(
        id = id,
        baseMaterial = baseMaterial,
        displayName = displayName,
        lore = lore.toList(),
        unbreakable = unbreakable,
        glowing = glowing,
        maxStackSize = if (maxStackSize > 0) maxStackSize else baseMaterial.maxStackSize(),
        modelPath = modelPath,
    )
}
