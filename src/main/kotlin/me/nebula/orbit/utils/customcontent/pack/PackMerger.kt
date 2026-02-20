package me.nebula.orbit.utils.customcontent.pack

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import me.nebula.orbit.utils.modelengine.generator.*
import net.minestom.server.item.Material
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PackMerger {

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
        packFormat: Int = 42,
    ): MergeResult {
        val entries = LinkedHashMap<String, ByteArray>()

        entries["pack.mcmeta"] = buildMcMeta(packFormat)

        modelEngineRawResults.forEach { raw ->
            raw.textureBytes.forEach { (texPath, bytes) ->
                entries["assets/minecraft/textures/modelengine/$texPath"] = bytes
            }
            raw.boneModels.forEach { (bonePath, boneModel) ->
                val json = buildModelJson(boneModel)
                entries["assets/minecraft/models/modelengine/$bonePath.json"] = json
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

        val itemOverrides = collectItemOverrides(modelEngineRawResults)
        ItemModelOverrideWriter.generate(itemOverrides).forEach { (path, bytes) ->
            entries[path] = bytes
        }

        BlockStateWriter.generate().forEach { (path, bytes) ->
            entries[path] = bytes
        }

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

    private fun collectItemOverrides(
        rawResults: List<RawGenerationResult>,
    ): Map<Material, List<ItemModelOverrideWriter.OverrideEntry>> {
        val overrides = mutableMapOf<Material, MutableList<ItemModelOverrideWriter.OverrideEntry>>()

        rawResults.forEach { raw ->
            raw.boneModels.keys.forEach { bonePath ->
                val parts = bonePath.split("/", limit = 2)
                if (parts.size == 2) {
                    val cmdId = ModelIdRegistry.getId(parts[0], parts[1]) ?: return@forEach
                    overrides.getOrPut(Material.PAPER) { mutableListOf() }
                        .add(ItemModelOverrideWriter.OverrideEntry(cmdId, "modelengine/$bonePath"))
                }
            }
        }

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

    private fun buildMcMeta(packFormat: Int): ByteArray {
        val json = JsonObject().apply {
            add("pack", JsonObject().apply {
                addProperty("pack_format", packFormat)
                addProperty("description", "Nebula merged resource pack")
            })
        }
        return gson.toJson(json).toByteArray(Charsets.UTF_8)
    }

    private fun buildModelJson(model: GeneratedBoneModel): ByteArray {
        val json = JsonObject().apply {
            addProperty("parent", "minecraft:block/block")
            add("textures", JsonObject().apply {
                model.textures.forEachIndexed { i, path -> addProperty(i.toString(), path) }
            })
            if (model.elements.isNotEmpty()) {
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
                                        addProperty("texture", "#${faceData.textureIndex}")
                                        if (faceData.rotation != 0) addProperty("rotation", faceData.rotation)
                                    })
                                }
                            })
                        })
                    }
                })
            }
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

    private fun sha1Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun FloatArray.toJsonArray(): com.google.gson.JsonArray =
        com.google.gson.JsonArray().also { arr -> forEach { arr.add(it) } }
}
