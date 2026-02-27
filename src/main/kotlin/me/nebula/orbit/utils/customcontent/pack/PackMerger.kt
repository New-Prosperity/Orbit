package me.nebula.orbit.utils.customcontent.pack

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import me.nebula.orbit.utils.modelengine.generator.*
import net.minestom.server.item.Material
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

object PackMerger {

    private val logger = logger("PackMerger")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class MergeResult(val packBytes: ByteArray, val sha1: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MergeResult) return false
            return packBytes.contentEquals(other.packBytes) && sha1 == other.sha1
        }

        override fun hashCode(): Int {
            var result = packBytes.contentHashCode()
            result = 31 * result + sha1.hashCode()
            return result
        }
    }

    fun merge(
        resources: ResourceManager,
        modelsDirectory: String,
        modelEngineRawResults: List<RawGenerationResult>,
        armorShaderEntries: Map<String, ByteArray> = emptyMap(),
        packFormat: Int = 75,
    ): MergeResult {
        val entries = LinkedHashMap<String, ByteArray>()

        entries["pack.mcmeta"] = buildMcMeta(packFormat)

        injectTestCube(entries)

        modelEngineRawResults.forEach { raw ->
            raw.textureBytes.forEach { (texPath, bytes) ->
                entries["assets/minecraft/textures/$texPath"] = bytes
            }
            raw.boneModels.forEach { (boneKey, boneModel) ->
                val json = buildModelJson(boneModel)
                entries["assets/minecraft/models/$boneKey.json"] = json
                entries["assets/minecraft/items/$boneKey.json"] =
                    buildItemDefinition("minecraft:$boneKey")
            }
        }

        val customItemModels = mutableMapOf<String, GeneratedBoneModel>()
        val customItemTextures = mutableMapOf<String, ByteArray>()

        CustomItemRegistry.all().forEach { item ->
            processCustomContentModel(resources, item.id, item.modelPath, modelsDirectory,
                customItemModels, customItemTextures)
        }

        val customBlockModels = mutableMapOf<String, GeneratedBoneModel>()
        val customBlockTextures = mutableMapOf<String, ByteArray>()

        CustomBlockRegistry.all().forEach { block ->
            processCustomContentModel(resources, block.id, block.modelPath, modelsDirectory,
                customBlockModels, customBlockTextures)
        }

        customItemTextures.forEach { (path, bytes) ->
            entries["assets/minecraft/textures/customcontent/$path"] = bytes
        }
        customItemModels.forEach { (id, model) ->
            entries["assets/minecraft/models/customcontent/items/$id.json"] = buildModelJson(model)
        }

        customBlockTextures.forEach { (path, bytes) ->
            entries["assets/minecraft/textures/customcontent/$path"] = bytes
        }
        customBlockModels.forEach { (id, model) ->
            entries["assets/minecraft/models/customcontent/blocks/$id.json"] = buildModelJson(model)
        }

        val itemOverrides = collectItemOverrides()
        ItemModelOverrideWriter.generate(itemOverrides).forEach { (path, bytes) ->
            entries[path] = bytes
        }

        BlockStateWriter.generate().forEach { (path, bytes) ->
            entries[path] = bytes
        }

        armorShaderEntries.forEach { (path, bytes) ->
            entries[path] = bytes
        }

        entries["assets/minecraft/atlases/blocks.json"] = buildAtlasDefinition(entries)

        val textures = entries.keys.count { it.endsWith(".png") }
        val models = entries.keys.count { it.contains("/models/") }
        val shaders = entries.keys.count { it.contains("/shaders/") }
        logger.info { "Merged pack: ${entries.size} entries ($models models, $textures textures, $shaders shaders)" }

        val packBytes = writeZip(entries)
        val sha1 = sha1Hex(packBytes)
        return MergeResult(packBytes, sha1)
    }

    private fun processCustomContentModel(
        resources: ResourceManager,
        id: String,
        modelPath: String,
        modelsDirectory: String,
        modelOutput: MutableMap<String, GeneratedBoneModel>,
        textureOutput: MutableMap<String, ByteArray>,
    ) {
        val filePath = "$modelsDirectory/$modelPath"
        if (!resources.exists(filePath)) return

        val model = resources.reader(filePath).use {
            BlockbenchParser.parse(id, it)
        }
        val (boneModel, atlasBytes) = ModelGenerator.buildFlatModel(model)
        modelOutput[id] = boneModel
        textureOutput["${id}_atlas.png"] = atlasBytes
    }

    private fun collectItemOverrides(): Map<Material, List<ItemModelOverrideWriter.OverrideEntry>> {
        val overrides = mutableMapOf<Material, MutableList<ItemModelOverrideWriter.OverrideEntry>>()

        CustomItemRegistry.all().forEach { item ->
            overrides.getOrPut(item.baseMaterial) { mutableListOf() }
                .add(ItemModelOverrideWriter.OverrideEntry(
                    item.customModelDataId,
                    "customcontent/items/${item.id}",
                ))
        }

        CustomBlockRegistry.all().forEach { block ->
            val item = CustomItemRegistry[block.itemId] ?: return@forEach
            overrides.getOrPut(item.baseMaterial) { mutableListOf() }
                .add(ItemModelOverrideWriter.OverrideEntry(
                    block.customModelDataId,
                    "customcontent/blocks/${block.id}",
                ))
        }

        return overrides
    }

    private fun buildItemDefinition(modelPath: String): ByteArray {
        val json = JsonObject().apply {
            add("model", JsonObject().apply {
                addProperty("type", "minecraft:model")
                addProperty("model", modelPath)
            })
        }
        return gson.toJson(json).toByteArray(Charsets.UTF_8)
    }

    private fun buildMcMeta(packFormat: Int): ByteArray {
        val json = JsonObject().apply {
            add("pack", JsonObject().apply {
                addProperty("pack_format", packFormat)
                addProperty("min_format", packFormat)
                addProperty("max_format", packFormat)
                addProperty("description", "Nebula merged resource pack")
            })
        }
        return gson.toJson(json).toByteArray(Charsets.UTF_8)
    }

    private fun buildModelJson(model: GeneratedBoneModel): ByteArray {
        val json = JsonObject().apply {
            add("textures", JsonObject().apply {
                model.textures.forEachIndexed { i, path -> addProperty(i.toString(), path) }
            })
            add("display", JsonObject().apply {
                add("thirdperson_righthand", JsonObject().apply {
                    add("translation", com.google.gson.JsonArray().apply { add(0); add(0); add(0) })
                    add("scale", com.google.gson.JsonArray().apply { add(1); add(1); add(1) })
                })
            })
            add("elements", com.google.gson.JsonArray().apply {
                model.elements.forEach { element ->
                    add(JsonObject().apply {
                        add("from", element.from.toJsonArray())
                        add("to", element.to.toJsonArray())
                        if (element.rotation != null) {
                            add("rotation", JsonObject().apply {
                                addProperty("angle", element.rotation.angle)
                                addProperty("axis", element.rotation.axis)
                                add("origin", element.rotation.origin.toJsonArray())
                            })
                        }
                        add("faces", JsonObject().apply {
                            element.faces.forEach { (face, faceData) ->
                                add(face, JsonObject().apply {
                                    add("uv", com.google.gson.JsonArray().also { arr ->
                                        faceData.uv.forEach { arr.add(it) }
                                    })
                                    addProperty("rotation", faceData.rotation)
                                    addProperty("texture", "#${faceData.textureIndex}")
                                })
                            }
                        })
                    })
                }
            })
        }
        return gson.toJson(json).toByteArray(Charsets.UTF_8)
    }

    private fun writeZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            entries.forEach { (path, data) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun buildAtlasDefinition(entries: Map<String, ByteArray>): ByteArray {
        val json = JsonObject().apply {
            add("sources", com.google.gson.JsonArray().apply {
                entries.keys
                    .filter { it.startsWith("assets/minecraft/textures/") && it.endsWith(".png") }
                    .map { it.removePrefix("assets/minecraft/textures/").removeSuffix(".png") }
                    .forEach { textureName ->
                        add(JsonObject().apply {
                            addProperty("type", "single")
                            addProperty("resource", "minecraft:$textureName")
                        })
                    }
            })
        }
        return gson.toJson(json).toByteArray(Charsets.UTF_8)
    }

    private fun sha1Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun injectTestCube(entries: MutableMap<String, ByteArray>) {
        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(255, 0, 0, 255)
        g.fillRect(0, 0, 16, 16)
        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
        entries["assets/minecraft/textures/me_debug_red.png"] = baos.toByteArray()

        val model = """
{
  "textures": {
    "0": "minecraft:me_debug_red",
    "particle": "minecraft:me_debug_red"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 16, 16],
      "faces": {
        "north": {"uv": [0, 0, 16, 16], "texture": "#0"},
        "south": {"uv": [0, 0, 16, 16], "texture": "#0"},
        "east":  {"uv": [0, 0, 16, 16], "texture": "#0"},
        "west":  {"uv": [0, 0, 16, 16], "texture": "#0"},
        "up":    {"uv": [0, 0, 16, 16], "texture": "#0"},
        "down":  {"uv": [0, 0, 16, 16], "texture": "#0"}
      }
    }
  ],
  "display": {
    "head": {
      "translation": [0, 0, 0],
      "scale": [1, 1, 1]
    }
  }
}
""".trim()
        entries["assets/minecraft/models/me_debug_cube.json"] = model.toByteArray(Charsets.UTF_8)
        entries["assets/minecraft/items/me_debug_cube.json"] = buildItemDefinition("minecraft:me_debug_cube")
    }

    private fun FloatArray.toJsonArray(): com.google.gson.JsonArray =
        com.google.gson.JsonArray().also { arr -> forEach { arr.add(it) } }
}
