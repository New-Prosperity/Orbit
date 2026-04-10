package me.nebula.orbit.utils.customcontent.pack

import com.google.gson.JsonObject
import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager

object SpriteItemPack {

    private val logger = logger("SpriteItemPack")
    private val gson = GsonProvider.pretty

    fun generate(resources: ResourceManager, spritesDir: String): Map<String, ByteArray> {
        val pngFiles = resources.list(spritesDir, "png", recursive = true)
        if (pngFiles.isEmpty()) return emptyMap()

        val entries = mutableMapOf<String, ByteArray>()

        for (path in pngFiles) {
            val relativePath = path.removePrefix("$spritesDir/").removeSuffix(".png")
            entries["assets/minecraft/textures/sprites/$relativePath.png"] = resources.readBytes(path)
            entries["assets/minecraft/models/sprites/$relativePath.json"] = buildFlatModel(relativePath)
            entries["assets/minecraft/items/sprites/$relativePath.json"] = buildItemDefinition(relativePath)
        }

        logger.info { "Generated ${pngFiles.size} sprite item models" }
        return entries
    }

    private fun buildFlatModel(relativePath: String): ByteArray {
        val json = JsonObject().apply {
            addProperty("parent", "minecraft:item/generated")
            add("textures", JsonObject().apply {
                addProperty("layer0", "minecraft:sprites/$relativePath")
            })
        }
        return gson.toJson(json).toByteArray(Charsets.UTF_8)
    }

    private fun buildItemDefinition(relativePath: String): ByteArray {
        val json = JsonObject().apply {
            add("model", JsonObject().apply {
                addProperty("type", "minecraft:model")
                addProperty("model", "minecraft:sprites/$relativePath")
            })
        }
        return gson.toJson(json).toByteArray(Charsets.UTF_8)
    }
}
