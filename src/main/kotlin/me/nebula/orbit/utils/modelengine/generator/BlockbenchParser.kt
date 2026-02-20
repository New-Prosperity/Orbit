package me.nebula.orbit.utils.modelengine.generator

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minestom.server.coordinate.Vec
import java.io.Reader

object BlockbenchParser {

    fun parse(name: String, reader: Reader): BlockbenchModel {
        val root = JsonParser.parseReader(reader).asJsonObject

        val meta = root.getAsJsonObject("meta")?.let {
            BbMeta(
                formatVersion = it["format_version"]?.asString ?: "4.0",
                modelFormat = it["model_format"]?.asString ?: "free",
                boxUv = it["box_uv"]?.asBoolean ?: false,
            )
        } ?: BbMeta("4.0", "free", false)

        val resolution = root.getAsJsonObject("resolution")?.let {
            BbResolution(it["width"]?.asInt ?: 16, it["height"]?.asInt ?: 16)
        } ?: BbResolution(16, 16)

        val elements = root.getAsJsonArray("elements")?.map { parseElement(it.asJsonObject) } ?: emptyList()
        val groups = root.getAsJsonArray("outliner")?.mapNotNull { parseOutlinerEntry(it) }
            ?.filterIsInstance<BbGroupChild.SubGroup>()
            ?.map { it.group }
            ?: emptyList()
        val textures = root.getAsJsonArray("textures")?.mapIndexed { i, el -> parseTexture(i, el.asJsonObject) } ?: emptyList()
        val animations = root.getAsJsonArray("animations")?.map { parseAnimation(it.asJsonObject) } ?: emptyList()

        return BlockbenchModel(name, meta, resolution, elements, groups, textures, animations)
    }

    private fun parseElement(obj: JsonObject): BbElement = BbElement(
        uuid = obj["uuid"]?.asString ?: "",
        name = obj["name"]?.asString ?: "",
        from = obj.readVec("from"),
        to = obj.readVec("to"),
        origin = obj.readVec("origin"),
        rotation = obj.readVec("rotation"),
        inflate = obj["inflate"]?.asFloat ?: 0f,
        faces = obj.getAsJsonObject("faces")?.entrySet()?.associate { (k, v) -> k to parseFace(v.asJsonObject) } ?: emptyMap(),
        visibility = obj["visibility"]?.asBoolean ?: true,
    )

    private fun parseFace(obj: JsonObject): BbFace = BbFace(
        uv = obj.getAsJsonArray("uv")?.let { arr ->
            FloatArray(arr.size()) { arr[it].asFloat }
        } ?: FloatArray(4),
        texture = obj["texture"]?.let { if (it.isJsonNull) -1 else it.asInt } ?: -1,
        rotation = obj["rotation"]?.asInt ?: 0,
    )

    private fun parseOutlinerEntry(element: JsonElement): BbGroupChild? {
        if (element.isJsonPrimitive) {
            return BbGroupChild.ElementRef(element.asString)
        }
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val children = obj.getAsJsonArray("children")?.mapNotNull { parseOutlinerEntry(it) } ?: emptyList()
            val group = BbGroup(
                uuid = obj["uuid"]?.asString ?: "",
                name = obj["name"]?.asString ?: "",
                origin = obj.readVec("origin"),
                rotation = obj.readVec("rotation"),
                children = children,
                visibility = obj["visibility"]?.asBoolean ?: true,
            )
            return BbGroupChild.SubGroup(group)
        }
        return null
    }

    private fun parseTexture(index: Int, obj: JsonObject): BbTexture = BbTexture(
        id = index,
        name = obj["name"]?.asString ?: "texture_$index",
        width = obj["width"]?.asInt ?: 16,
        height = obj["height"]?.asInt ?: 16,
        source = obj["source"]?.asString ?: "",
    )

    private fun parseAnimation(obj: JsonObject): BbAnimation {
        val animators = mutableMapOf<String, BbAnimator>()
        obj.getAsJsonObject("animators")?.entrySet()?.forEach { (uuid, animatorEl) ->
            val animatorObj = animatorEl.asJsonObject
            val name = animatorObj["name"]?.asString ?: uuid
            val keyframes = animatorObj.getAsJsonArray("keyframes")?.map { parseKeyframe(it.asJsonObject) } ?: emptyList()
            animators[uuid] = BbAnimator(name, keyframes)
        }

        return BbAnimation(
            name = obj["name"]?.asString ?: "",
            length = obj["length"]?.asFloat ?: 0f,
            loop = obj["loop"]?.asString ?: "once",
            animators = animators,
        )
    }

    private fun parseKeyframe(obj: JsonObject): BbKeyframe {
        val dataPoints = obj.getAsJsonArray("data_points")?.map { dp ->
            val dpObj = dp.asJsonObject
            Vec(
                dpObj["x"]?.asDoubleOrZero() ?: 0.0,
                dpObj["y"]?.asDoubleOrZero() ?: 0.0,
                dpObj["z"]?.asDoubleOrZero() ?: 0.0,
            )
        } ?: emptyList()

        return BbKeyframe(
            channel = obj["channel"]?.asString ?: "position",
            time = obj["time"]?.asFloat ?: 0f,
            dataPoints = dataPoints,
            interpolation = obj["interpolation"]?.asString ?: "linear",
            bezierLeftTime = obj.readVec("bezier_left_time"),
            bezierLeftValue = obj.readVec("bezier_left_value"),
            bezierRightTime = obj.readVec("bezier_right_time"),
            bezierRightValue = obj.readVec("bezier_right_value"),
        )
    }

    private fun JsonObject.readVec(key: String): Vec {
        val arr = getAsJsonArray(key) ?: return Vec.ZERO
        return Vec(
            arr.getOrZero(0),
            arr.getOrZero(1),
            arr.getOrZero(2),
        )
    }

    private fun JsonArray.getOrZero(index: Int): Double =
        if (index < size()) get(index).asDoubleOrZero() else 0.0

    private fun JsonElement.asDoubleOrZero(): Double = runCatching {
        if (isJsonPrimitive && asJsonPrimitive.isNumber) asDouble
        else if (isJsonPrimitive && asJsonPrimitive.isString) asString.toDoubleOrNull() ?: 0.0
        else 0.0
    }.getOrDefault(0.0)
}
