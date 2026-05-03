package me.nebula.orbit.utils.customcontent.helditem

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.ether.utils.logging.logger
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

object HeldItemPack {

    private val log = logger("HeldItemPack")
    private val gson = GsonProvider.pretty

    private const val ITEMS_DIR = "assets/minecraft/items"
    private const val MODELS_DIR = "assets/minecraft/models"
    private const val TEXTURE_DIR = "assets/minecraft/textures/customcontent/helditems"

    private const val IDLE_FPS = 15
    private const val ACTION_FPS = 30

    fun generate(items: List<RegisteredHeldItem>): Map<String, ByteArray> {
        if (items.isEmpty()) return emptyMap()

        val entries = LinkedHashMap<String, ByteArray>()
        for (item in items) {
            generateItem(item, entries)
        }
        log.info { "Generated held-item pack: ${items.size} items, ${entries.size} entries" }
        return entries
    }

    private fun generateItem(item: RegisteredHeldItem, entries: MutableMap<String, ByteArray>) {
        val sourceTex = decodeBase64Image(item.parsed.textures.firstOrNull()?.source ?: "")
        val texW = sourceTex?.width ?: item.parsed.resolutionWidth.coerceAtLeast(1)
        val texH = sourceTex?.height ?: item.parsed.resolutionHeight.coerceAtLeast(1)

        val texturePath = "$TEXTURE_DIR/${item.id}.png"
        if (sourceTex != null) {
            entries[texturePath] = encodePng(sourceTex)
        } else {
            entries[texturePath] = encodePng(BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB))
        }

        val animByTrigger = item.parsed.animations.groupBy { it.trigger }
        val idleAnim = animByTrigger[AnimationTrigger.IDLE]?.firstOrNull()
        val swingAnim = animByTrigger[AnimationTrigger.SWING]?.firstOrNull()
        val useAnim = animByTrigger[AnimationTrigger.USE]?.firstOrNull()

        val explicitStates = item.parsed.animations
            .filter { it.trigger == AnimationTrigger.IDLE && it != idleAnim }
            .toMutableList()

        val baseModelName = "customcontent/helditems/${item.id}/rest"
        emitModel(entries, baseModelName, HeldItemFrameBaker.bakeRest(item.parsed), item, texW, texH, isBase = true)

        val idleModelNames = idleAnim?.let { emitAnimation(entries, item, it, IDLE_FPS, texW, texH) } ?: emptyList()
        val swingModelNames = swingAnim?.let { emitAnimation(entries, item, it, ACTION_FPS, texW, texH) } ?: emptyList()
        val useModelNames = useAnim?.let { emitAnimation(entries, item, it, ACTION_FPS, texW, texH) } ?: emptyList()

        val explicitStateMap = mutableMapOf<String, List<String>>()
        for (anim in explicitStates) {
            explicitStateMap[anim.name] = emitAnimation(entries, item, anim, ACTION_FPS, texW, texH)
        }

        val rootModel = buildRootModel(
            baseModelName = baseModelName,
            idleModelNames = idleModelNames,
            swingModelNames = swingModelNames,
            useModelNames = useModelNames,
            explicitStates = explicitStateMap,
        )

        val itemDef = JsonObject()
        itemDef.add("model", rootModel)
        itemDef.addProperty("hand_animation_on_swap", false)

        entries["$ITEMS_DIR/customcontent/helditems/${item.id}.json"] = gson.toJson(itemDef).toByteArray(Charsets.UTF_8)
    }

    private fun emitAnimation(
        entries: MutableMap<String, ByteArray>,
        item: RegisteredHeldItem,
        anim: HeldItemAnimation,
        fps: Int,
        texW: Int,
        texH: Int,
    ): List<String> {
        val frames = HeldItemFrameBaker.bakeAnimation(item.parsed, anim, fps)
        val sanitized = sanitize(anim.name)
        return frames.mapIndexed { index, frame ->
            val name = "customcontent/helditems/${item.id}/${sanitized}_$index"
            emitModel(entries, name, frame, item, texW, texH, isBase = false)
            name
        }
    }

    private fun emitModel(
        entries: MutableMap<String, ByteArray>,
        modelName: String,
        frame: HeldItemFrameBaker.BakedFrame,
        item: RegisteredHeldItem,
        texW: Int,
        texH: Int,
        isBase: Boolean,
    ) {
        val obj = JsonObject()
        val textures = JsonObject()
        textures.addProperty("layer0", "minecraft:customcontent/helditems/${item.id}")
        textures.addProperty("particle", "minecraft:customcontent/helditems/${item.id}")
        obj.add("textures", textures)

        val elements = JsonArray()
        for (el in frame.elements) {
            elements.add(buildElementJson(el, texW, texH))
        }
        obj.add("elements", elements)

        val display = buildDisplayJson(item)
        if (display.size() > 0) obj.add("display", display)

        val path = "$MODELS_DIR/${modelName.replace('/', '/')}.json"
        entries[path] = gson.toJson(obj).toByteArray(Charsets.UTF_8)
    }

    private fun buildElementJson(el: HeldItemFrameBaker.BakedElement, texW: Int, texH: Int): JsonObject {
        val obj = JsonObject()
        obj.add("from", vec3Json(el.fromX, el.fromY, el.fromZ))
        obj.add("to", vec3Json(el.toX, el.toY, el.toZ))
        if (el.rotation != null) {
            val r = JsonObject()
            r.add("origin", vec3Json(el.rotation.originX, el.rotation.originY, el.rotation.originZ))
            r.addProperty("x", el.rotation.eulerX)
            r.addProperty("y", el.rotation.eulerY)
            r.addProperty("z", el.rotation.eulerZ)
            obj.add("rotation", r)
        }
        val faces = JsonObject()
        for (face in el.faces) {
            val fobj = JsonObject()
            val uv = JsonArray()
            uv.add(face.uMin * 16f / texW.toFloat())
            uv.add(face.vMin * 16f / texH.toFloat())
            uv.add(face.uMax * 16f / texW.toFloat())
            uv.add(face.vMax * 16f / texH.toFloat())
            fobj.add("uv", uv)
            fobj.addProperty("texture", "#layer0")
            if (face.rotation != 0) fobj.addProperty("rotation", face.rotation)
            faces.add(face.direction, fobj)
        }
        obj.add("faces", faces)
        return obj
    }

    private fun buildDisplayJson(item: RegisteredHeldItem): JsonObject {
        val display = JsonObject()
        val scale = item.modelScale
        val userSlots = item.parsed.displaySlots
        val allSlots = listOf(
            "firstperson_righthand", "firstperson_lefthand",
            "thirdperson_righthand", "thirdperson_lefthand",
            "gui", "head", "ground", "fixed",
        )
        for (slotName in allSlots) {
            val user = userSlots[slotName]
            if (user == null && scale == 1f) continue
            val rotation = user?.rotation ?: FloatArray(3)
            val translation = user?.translation ?: FloatArray(3)
            val baseScale = user?.scale ?: floatArrayOf(1f, 1f, 1f)
            val composedScale = floatArrayOf(baseScale[0] * scale, baseScale[1] * scale, baseScale[2] * scale)

            val entry = JsonObject()
            if (rotation.any { it != 0f }) entry.add("rotation", floatArrJson(rotation))
            if (translation.any { it != 0f }) entry.add("translation", floatArrJson(translation))
            if (composedScale.any { it != 1f }) entry.add("scale", floatArrJson(composedScale))
            if (entry.size() > 0) display.add(slotName, entry)
        }
        return display
    }

    private fun buildRootModel(
        baseModelName: String,
        idleModelNames: List<String>,
        swingModelNames: List<String>,
        useModelNames: List<String>,
        explicitStates: Map<String, List<String>>,
    ): JsonObject {
        val idleOrBase = if (idleModelNames.isNotEmpty()) {
            buildTimeRangeDispatch(idleModelNames)
        } else {
            modelRef(baseModelName)
        }

        val swingOrIdle = if (swingModelNames.isNotEmpty()) {
            buildCooldownRangeDispatch(swingModelNames, idleOrBase)
        } else {
            idleOrBase
        }

        val useOrFallback = if (useModelNames.isNotEmpty()) {
            val useDispatch = buildUseDurationRangeDispatch(useModelNames)
            val cond = JsonObject()
            cond.addProperty("type", "minecraft:condition")
            cond.addProperty("property", "minecraft:using_item")
            cond.add("on_true", useDispatch)
            cond.add("on_false", swingOrIdle)
            cond
        } else {
            swingOrIdle
        }

        if (explicitStates.isEmpty()) return useOrFallback

        val select = JsonObject()
        select.addProperty("type", "minecraft:select")
        select.addProperty("property", "minecraft:custom_model_data")
        select.addProperty("index", 0)
        val cases = JsonArray()
        for ((stateName, models) in explicitStates) {
            val caseObj = JsonObject()
            caseObj.addProperty("when", stateName)
            caseObj.add("model", buildExplicitStateDispatch(models))
            cases.add(caseObj)
        }
        select.add("cases", cases)
        select.add("fallback", useOrFallback)
        return select
    }

    private fun buildTimeRangeDispatch(modelNames: List<String>): JsonObject {
        val rd = JsonObject()
        rd.addProperty("type", "minecraft:range_dispatch")
        rd.addProperty("property", "minecraft:time")
        rd.addProperty("source", "random")
        rd.addProperty("scale", modelNames.size.toFloat())
        val entries = JsonArray()
        for ((i, name) in modelNames.withIndex()) {
            val e = JsonObject()
            e.addProperty("threshold", i.toFloat())
            e.add("model", modelRef(name))
            entries.add(e)
        }
        rd.add("entries", entries)
        return rd
    }

    private fun buildCooldownRangeDispatch(swingModelNames: List<String>, idleFallback: JsonObject): JsonObject {
        val n = swingModelNames.size
        val rd = JsonObject()
        rd.addProperty("type", "minecraft:range_dispatch")
        rd.addProperty("property", "minecraft:cooldown")
        rd.addProperty("scale", 1.0f)
        val entries = JsonArray()
        val zero = JsonObject()
        zero.addProperty("threshold", 0.0f)
        zero.add("model", idleFallback)
        entries.add(zero)
        for (i in 0 until n) {
            val frameIndex = n - 1 - i
            val e = JsonObject()
            e.addProperty("threshold", (i + 1).toFloat() / n.toFloat())
            e.add("model", modelRef(swingModelNames[frameIndex]))
            entries.add(e)
        }
        rd.add("entries", entries)
        return rd
    }

    private fun buildUseDurationRangeDispatch(useModelNames: List<String>): JsonObject {
        val rd = JsonObject()
        rd.addProperty("type", "minecraft:range_dispatch")
        rd.addProperty("property", "minecraft:use_duration")
        rd.addProperty("scale", 1.0f / ACTION_FPS.toFloat())
        val entries = JsonArray()
        for ((i, name) in useModelNames.withIndex()) {
            val e = JsonObject()
            e.addProperty("threshold", i.toFloat())
            e.add("model", modelRef(name))
            entries.add(e)
        }
        rd.add("entries", entries)
        return rd
    }

    private fun buildExplicitStateDispatch(modelNames: List<String>): JsonObject {
        val rd = JsonObject()
        rd.addProperty("type", "minecraft:range_dispatch")
        rd.addProperty("property", "minecraft:custom_model_data")
        rd.addProperty("index", 0)
        rd.addProperty("scale", modelNames.size.toFloat())
        val entries = JsonArray()
        for ((i, name) in modelNames.withIndex()) {
            val e = JsonObject()
            e.addProperty("threshold", i.toFloat())
            e.add("model", modelRef(name))
            entries.add(e)
        }
        rd.add("entries", entries)
        return rd
    }

    private fun modelRef(name: String): JsonObject {
        val obj = JsonObject()
        obj.addProperty("type", "minecraft:model")
        obj.addProperty("model", "minecraft:$name")
        return obj
    }

    private fun vec3Json(x: Float, y: Float, z: Float): JsonArray {
        val a = JsonArray()
        a.add(x); a.add(y); a.add(z)
        return a
    }

    private fun floatArrJson(arr: FloatArray): JsonArray {
        val a = JsonArray()
        arr.forEach { a.add(it) }
        return a
    }

    private fun sanitize(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]"), "_")

    private fun decodeBase64Image(source: String): BufferedImage? {
        if (source.isBlank()) return null
        return runCatching {
            val data = if (source.contains(",")) source.substringAfter(",") else source
            val bytes = Base64.getDecoder().decode(data)
            ImageIO.read(ByteArrayInputStream(bytes))
        }.onFailure { log.warn { "Texture decode error: ${it.message}" } }.getOrNull()
    }

    private fun encodePng(img: BufferedImage): ByteArray =
        ByteArrayOutputStream().use { ImageIO.write(img, "png", it); it.toByteArray() }
}
