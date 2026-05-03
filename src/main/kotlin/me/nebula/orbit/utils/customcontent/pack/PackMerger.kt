package me.nebula.orbit.utils.customcontent.pack

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import me.nebula.orbit.utils.customcontent.furniture.BlockbenchColliderParser
import me.nebula.orbit.utils.customcontent.furniture.FurnitureJsonLoader
import me.nebula.orbit.utils.customcontent.furniture.FurnitureRegistry
import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import me.nebula.orbit.utils.modelengine.generator.BbDisplaySlot
import me.nebula.orbit.utils.modelengine.generator.BlockbenchParser
import me.nebula.orbit.utils.modelengine.generator.GeneratedBoneModel
import me.nebula.orbit.utils.modelengine.generator.ModelGenerator
import me.nebula.orbit.utils.modelengine.generator.RawGenerationResult
import net.minestom.server.item.Material
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PackMerger {

    private val logger = logger("PackMerger")
    private val gson = GsonProvider.pretty

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
        blocksDirectory: String,
        modelEngineRawResults: List<RawGenerationResult>,
        armorShaderEntries: Map<String, ByteArray> = emptyMap(),
        packFormat: Int = 84,
    ): MergeResult {
        val entries = LinkedHashMap<String, ByteArray>()

        entries["pack.mcmeta"] = buildMcMeta(packFormat)

        modelEngineRawResults.forEach { raw ->
            raw.textureBytes.forEach { (texPath, bytes) ->
                entries["assets/minecraft/textures/$texPath"] = bytes
            }
            raw.boneModels.forEach { (boneKey, boneModel) ->
                val json = buildModelJson(boneModel)
                entries["assets/minecraft/models/x/$boneKey.json"] = json
                entries["assets/minecraft/items/$boneKey.json"] =
                    buildItemDefinition("minecraft:x/$boneKey")
            }
        }

        val customItemModels = mutableMapOf<String, GeneratedBoneModel>()
        val customItemTextures = mutableMapOf<String, ByteArray>()

        val furnitureItemIds = FurnitureRegistry.all().map { it.itemId }.toSet()

        CustomItemRegistry.all().forEach { item ->
            if (item.id in furnitureItemIds) return@forEach
            processCustomContentModel(resources, item.id, item.modelPath, modelsDirectory,
                customItemModels, customItemTextures)
        }

        val furnitureModels = mutableMapOf<String, GeneratedBoneModel>()
        val furnitureTextures = mutableMapOf<String, ByteArray>()
        FurnitureRegistry.all().forEach { def ->
            processFurnitureModel(resources, def.id, furnitureModels, furnitureTextures)
        }

        CustomBlockRegistry.all().forEach { block ->
            processBlockSubfolder(resources, block.id, blocksDirectory, entries)
        }

        customItemTextures.forEach { (key, bytes) ->
            entries["assets/minecraft/textures/x/$key.png"] = bytes
        }
        customItemModels.forEach { (key, model) ->
            entries["assets/minecraft/models/x/$key.json"] = buildModelJson(model)
        }

        furnitureTextures.forEach { (key, bytes) ->
            entries["assets/minecraft/textures/x/$key.png"] = bytes
        }
        furnitureModels.forEach { (key, model) ->
            entries["assets/minecraft/models/x/$key.json"] = buildModelJson(model)
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

        dedupeTextures(entries)
        obfuscateShaderIncludes(entries)

        val customTextures = entries.keys
            .filter {
                it.endsWith(".png") && it.startsWith("assets/minecraft/textures/x/")
            }
            .map { it.removePrefix("assets/minecraft/textures/").removeSuffix(".png") }
        if (customTextures.isNotEmpty()) {
            val atlasJson = JsonObject().apply {
                add("sources", JsonArray().apply {
                    for (tex in customTextures) {
                        add(JsonObject().apply {
                            addProperty("type", "minecraft:single")
                            addProperty("resource", "minecraft:$tex")
                        })
                    }
                })
            }
            entries["assets/minecraft/atlases/blocks.json"] = GsonProvider.pretty.toJson(atlasJson).toByteArray(Charsets.UTF_8)
        }

        entries["assets/minecraft/sounds.json"] = buildBlockSoundSilencer()

        val textures = entries.keys.count { it.endsWith(".png") }
        val models = entries.keys.count { it.contains("/models/") }
        val shaders = entries.keys.count { it.contains("/shaders/") }
        logger.info { "Merged pack: ${entries.size} entries ($models models, $textures textures, $shaders shaders)" }

        val packBytes = writeZip(entries)
        val sha1 = sha1Hex(packBytes)
        return MergeResult(packBytes, sha1)
    }

    private fun processBlockSubfolder(
        resources: ResourceManager,
        id: String,
        blocksDirectory: String,
        entries: MutableMap<String, ByteArray>,
    ) {
        val subdir = "$blocksDirectory/$id"
        val modelPath = "$subdir/model.json"
        check(resources.exists(modelPath)) {
            "Custom block '$id' missing model.json at $modelPath"
        }

        val pngs = resources.list(subdir, "png", recursive = false)
        val textureBaseNames = pngs.map { it.substringAfterLast('/').removeSuffix(".png") }.toSet()
        check(textureBaseNames.isNotEmpty()) {
            "Custom block '$id' has no .png textures in $subdir"
        }

        pngs.forEach { png ->
            val baseName = png.substringAfterLast('/').removeSuffix(".png")
            val obfTex = ObfuscationCodec.obfuscate("cc_block_${id}_$baseName")
            entries["assets/minecraft/textures/x/$obfTex.png"] = resources.readBytes(png)
        }

        val obfModel = ObfuscationCodec.obfuscate("cc_block_$id")
        val rewritten = rewriteBlockModelTextures(resources.readText(modelPath), id, textureBaseNames)
        entries["assets/minecraft/models/x/$obfModel.json"] = rewritten.toByteArray(Charsets.UTF_8)
    }

    private fun rewriteBlockModelTextures(modelJson: String, id: String, available: Set<String>): String {
        val root = JsonParser.parseString(modelJson).asJsonObject
        val textures = root.getAsJsonObject("textures") ?: return modelJson
        val rewritten = JsonObject()
        textures.entrySet().forEach { (key, value) ->
            val ref = value.asString
            val baseName = ref.substringAfterLast('/').substringAfterLast(':')
            check(baseName in available) {
                "Custom block '$id' model.json references texture '$ref' but no '$baseName.png' exists in subfolder"
            }
            val obfTex = ObfuscationCodec.obfuscate("cc_block_${id}_$baseName")
            rewritten.addProperty(key, "x/$obfTex")
        }
        root.add("textures", rewritten)
        return gson.toJson(root)
    }

    private fun processFurnitureModel(
        resources: ResourceManager,
        id: String,
        modelOutput: MutableMap<String, GeneratedBoneModel>,
        textureOutput: MutableMap<String, ByteArray>,
    ) {
        val filePath = "customcontent/furniture/$id.bbmodel"
        if (!resources.exists(filePath)) {
            logger.warn { "Furniture '$id' has no .bbmodel at $filePath — skipping model emission" }
            return
        }
        val model = resources.reader(filePath).use { BlockbenchParser.parse(id, it) }
        val excluded = BlockbenchColliderParser.elementUuidsUnderColliderBones(model, FurnitureJsonLoader.DEFAULT_COLLIDER_PREFIX)
        val (boneModel, atlasBytes) = ModelGenerator.buildFlatModel(model, elementFilter = { it.uuid !in excluded })
        val obfModel = ObfuscationCodec.obfuscate("cc_furniture_$id")
        val obfTexture = ObfuscationCodec.obfuscate("cc_${id}_atlas")
        modelOutput[obfModel] = boneModel
        textureOutput[obfTexture] = atlasBytes
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
        val obfModel = ObfuscationCodec.obfuscate("cc_item_$id")
        val obfTexture = ObfuscationCodec.obfuscate("cc_${id}_atlas")
        modelOutput[obfModel] = boneModel
        textureOutput[obfTexture] = atlasBytes
    }

    private fun collectItemOverrides(): Map<Material, List<ItemModelOverrideWriter.OverrideEntry>> {
        val overrides = mutableMapOf<Material, MutableList<ItemModelOverrideWriter.OverrideEntry>>()
        val furnitureItemIds = FurnitureRegistry.all().associateBy { it.itemId }
        val blocksByItemId = CustomBlockRegistry.all().associateBy { it.itemId }

        CustomItemRegistry.all().forEach { item ->
            val furniture = furnitureItemIds[item.id]
            val block = blocksByItemId[item.id]
            val modelPath = when {
                furniture != null -> "x/" + ObfuscationCodec.obfuscate("cc_furniture_${furniture.id}")
                block != null -> "x/" + ObfuscationCodec.obfuscate("cc_block_${block.id}")
                else -> "x/" + ObfuscationCodec.obfuscate("cc_item_${item.id}")
            }
            overrides.getOrPut(item.baseMaterial) { mutableListOf() }
                .add(ItemModelOverrideWriter.OverrideEntry(item.customModelDataId, modelPath))
        }

        CustomBlockRegistry.all().forEach { block ->
            if (CustomItemRegistry[block.itemId] != null) return@forEach
            overrides.getOrPut(Material.PAPER) { mutableListOf() }
                .add(ItemModelOverrideWriter.OverrideEntry(
                    block.customModelDataId,
                    "x/" + ObfuscationCodec.obfuscate("cc_block_${block.id}"),
                ))
        }

        return overrides
    }

    private val SILENCED_SOUND_NAMESPACES = listOf<String>()
    private val SILENCED_SOUND_EVENTS = listOf("break", "place", "hit", "step", "fall")

    private fun buildBlockSoundSilencer(): ByteArray {
        val root = JsonObject()
        for (ns in SILENCED_SOUND_NAMESPACES) {
            for (event in SILENCED_SOUND_EVENTS) {
                root.add("block.$ns.$event", JsonObject().apply {
                    addProperty("replace", true)
                    add("sounds", JsonArray())
                })
            }
        }
        return gson.toJson(root).toByteArray(Charsets.UTF_8)
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
                if (!has("particle") && model.textures.isNotEmpty()) {
                    addProperty("particle", model.textures.first())
                }
            })
            add("display", buildDisplayJson(model.display))
            add("elements", JsonArray().apply {
                model.elements.forEach { element ->
                    add(JsonObject().apply {
                        add("from", element.from.toJsonArray())
                        add("to", element.to.toJsonArray())
                        if (element.rotation != null) {
                            add("rotation", JsonObject().apply {
                                val euler = element.rotation.euler
                                if (euler != null) {
                                    addProperty("x", euler[0])
                                    addProperty("y", euler[1])
                                    addProperty("z", euler[2])
                                } else {
                                    addProperty("angle", element.rotation.angle)
                                    addProperty("axis", element.rotation.axis)
                                }
                                add("origin", element.rotation.origin.toJsonArray())
                            })
                        }
                        add("faces", JsonObject().apply {
                            element.faces.forEach { (face, faceData) ->
                                add(face, JsonObject().apply {
                                    add("uv", JsonArray().also { arr ->
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
            add("sources", JsonArray().apply {
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

    private fun buildDisplayJson(display: Map<String, BbDisplaySlot>): JsonObject = JsonObject().apply {
        if (display.isEmpty()) {
            add("thirdperson_righthand", JsonObject().apply {
                add("translation", JsonArray().apply { add(0); add(0); add(0) })
                add("scale", JsonArray().apply { add(1); add(1); add(1) })
            })
            return@apply
        }
        for ((slot, data) in display) {
            add(slot, JsonObject().apply {
                add("rotation", data.rotation.toJsonArray())
                add("translation", data.translation.toJsonArray())
                add("scale", data.scale.toJsonArray())
            })
        }
    }

    private fun sha1Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun FloatArray.toJsonArray(): JsonArray =
        JsonArray().also { arr -> forEach { arr.add(it) } }

    private fun dedupeTextures(entries: MutableMap<String, ByteArray>) {
        val byHash = mutableMapOf<String, MutableList<String>>()
        for ((path, bytes) in entries) {
            if (!path.endsWith(".png")) continue
            if (!path.startsWith("assets/minecraft/textures/x/")) continue
            val hash = sha1Hex(bytes)
            byHash.getOrPut(hash) { mutableListOf() }.add(path)
        }

        val rewrites = mutableMapOf<String, String>()
        var savedBytes = 0
        for ((_, paths) in byHash) {
            if (paths.size <= 1) continue
            val canonical = paths.min()
            val canonicalRef = canonical
                .removePrefix("assets/minecraft/textures/")
                .removeSuffix(".png")
            for (path in paths) {
                if (path == canonical) continue
                val ref = path.removePrefix("assets/minecraft/textures/").removeSuffix(".png")
                rewrites[ref] = canonicalRef
                savedBytes += entries[path]?.size ?: 0
                entries.remove(path)
            }
        }

        val updated = mutableMapOf<String, ByteArray>()
        var rewriteCount = 0
        var missingCount = 0
        for ((path, bytes) in entries) {
            if (!path.endsWith(".json")) continue
            if (!path.contains("/models/")) continue
            val root = try {
                JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject
            } catch (_: Exception) { continue }
            val textures = root.getAsJsonObject("textures") ?: continue
            val newTextures = JsonObject()
            var changed = false
            for ((key, value) in textures.entrySet()) {
                if (!value.isJsonPrimitive) {
                    newTextures.add(key, value)
                    continue
                }
                val ref = value.asString
                if (ref.startsWith("#")) {
                    newTextures.add(key, value)
                    continue
                }
                val (namespace, bare) = splitNamespace(ref)
                val refExists = entries.containsKey("assets/minecraft/textures/$bare.png")
                if (refExists) {
                    newTextures.add(key, value)
                    continue
                }
                val canonical = rewrites[bare]
                if (canonical != null && entries.containsKey("assets/minecraft/textures/$canonical.png")) {
                    val resolved = if (namespace != null) "$namespace:$canonical" else canonical
                    newTextures.addProperty(key, resolved)
                    changed = true
                    rewriteCount++
                } else {
                    newTextures.add(key, value)
                    missingCount++
                    logger.warn { "Pack model '$path' references missing texture '$ref' (slot=$key) — no canonical found" }
                }
            }
            if (changed) {
                root.add("textures", newTextures)
                updated[path] = gson.toJson(root).toByteArray(Charsets.UTF_8)
            }
        }
        updated.forEach { (path, bytes) -> entries[path] = bytes }

        if (missingCount > 0) {
            logger.warn { "Pack contains $missingCount dangling texture references; affected models will render as black/magenta" }
        }
        logger.info {
            "Texture dedup: removed ${rewrites.size} duplicate textures, " +
                "saved ${savedBytes / 1024}KB, healed $rewriteCount refs across ${updated.size} models"
        }
    }

    private fun splitNamespace(value: String): Pair<String?, String> {
        val colon = value.indexOf(':')
        return if (colon >= 0) value.substring(0, colon) to value.substring(colon + 1)
        else null to value
    }

    private fun obfuscateShaderIncludes(entries: MutableMap<String, ByteArray>) {
        val includePrefix = "assets/minecraft/shaders/include/"
        val shaderExts = setOf("glsl", "vsh", "fsh")
        val renames = mutableMapOf<String, String>()
        val pathRenames = mutableMapOf<String, String>()

        for (path in entries.keys.toList()) {
            if (!path.startsWith(includePrefix)) continue
            val ext = path.substringAfterLast('.', "")
            if (ext !in shaderExts) continue
            val rel = path.removePrefix(includePrefix)
            if (rel.startsWith("x/")) continue
            val obf = ObfuscationCodec.obfuscate("shader_$rel")
            val newRel = "x/$obf.$ext"
            renames[rel] = newRel
            pathRenames[path] = includePrefix + newRel
        }

        if (renames.isEmpty()) return

        for ((oldPath, newPath) in pathRenames) {
            val bytes = entries.remove(oldPath) ?: continue
            entries[newPath] = bytes
        }

        val importRegex = Regex("""#moj_import\s+<([^>]+)>""")
        val updated = mutableMapOf<String, ByteArray>()
        for ((path, bytes) in entries) {
            val ext = path.substringAfterLast('.', "")
            if (ext !in shaderExts) continue
            val text = String(bytes, Charsets.UTF_8)
            val newText = importRegex.replace(text) { match ->
                val raw = match.groupValues[1]
                val (ns, ref) = splitNamespace(raw)
                val resolved = renames[ref] ?: return@replace match.value
                if (ns == null) "#moj_import <$resolved>" else "#moj_import <$ns:$resolved>"
            }
            if (newText != text) updated[path] = newText.toByteArray(Charsets.UTF_8)
        }
        updated.forEach { (path, bytes) -> entries[path] = bytes }

        logger.info {
            "Shader obfuscation: renamed ${renames.size} include files, rewrote ${updated.size} import directives"
        }
    }
}
