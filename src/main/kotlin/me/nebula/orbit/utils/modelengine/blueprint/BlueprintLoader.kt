package me.nebula.orbit.utils.modelengine.blueprint

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.nebula.orbit.utils.modelengine.math.QUAT_IDENTITY
import me.nebula.orbit.utils.modelengine.math.eulerToQuat
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Vec
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomModelData
import java.io.Reader

object BlueprintLoader {

    fun load(name: String, reader: Reader): ModelBlueprint {
        val root = JsonParser.parseReader(reader).asJsonObject
        val bonesJson = root.getAsJsonArray("bones")
        val animationsJson = root.getAsJsonObject("animations")

        val boneMap = linkedMapOf<String, BlueprintBone>()
        val rootBones = mutableListOf<String>()

        bonesJson.forEach { element ->
            val obj = element.asJsonObject
            val boneName = obj["name"].asString
            val parentName = obj["parent"]?.asString
            val childNames = obj.getAsJsonArray("children")?.map { it.asString } ?: emptyList()
            val offset = obj.readVec("offset")
            val rotation = obj.readRotation("rotation")
            val scale = obj.readVec("scale", Vec(1.0, 1.0, 1.0))
            val visible = obj["visible"]?.asBoolean ?: true

            val modelItem = obj["modelItem"]?.asJsonObject?.let { itemObj ->
                val material = Material.fromKey(itemObj["material"].asString) ?: Material.PAPER
                val customModelData = itemObj["customModelData"]?.asInt ?: 0
                ItemStack.of(material).with(
                    DataComponents.CUSTOM_MODEL_DATA,
                    CustomModelData(listOf(customModelData.toFloat()), emptyList(), emptyList(), emptyList()),
                )
            }

            val behaviors = obj.getAsJsonObject("behaviors")?.let { parseBehaviors(it) } ?: emptyMap()

            boneMap[boneName] = BlueprintBone(
                name = boneName,
                parentName = parentName,
                childNames = childNames,
                offset = offset,
                rotation = rotation,
                scale = scale,
                modelItem = modelItem,
                behaviors = behaviors,
                visible = visible,
            )

            if (parentName == null) rootBones += boneName
        }

        val animations = linkedMapOf<String, AnimationBlueprint>()
        animationsJson?.entrySet()?.forEach { (animName, animElement) ->
            val animObj = animElement.asJsonObject
            animations[animName] = parseAnimation(animName, animObj)
        }

        return ModelBlueprint(
            name = name,
            bones = boneMap,
            rootBoneNames = rootBones,
            animations = animations,
            hitboxWidth = root["hitboxWidth"]?.asFloat ?: 1f,
            hitboxHeight = root["hitboxHeight"]?.asFloat ?: 2f,
            eyeHeight = root["eyeHeight"]?.asFloat ?: 1.6f,
        )
    }

    private fun parseAnimation(name: String, obj: JsonObject): AnimationBlueprint {
        val length = obj["length"].asFloat
        val loopStr = obj["loop"]?.asString ?: "once"
        val loop = when (loopStr.lowercase()) {
            "loop" -> LoopMode.LOOP
            "hold" -> LoopMode.HOLD
            else -> LoopMode.ONCE
        }

        val boneKeyframes = linkedMapOf<String, BoneKeyframes>()
        obj.getAsJsonObject("bones")?.entrySet()?.forEach { (boneName, boneElement) ->
            val boneObj = boneElement.asJsonObject
            boneKeyframes[boneName] = BoneKeyframes(
                position = parseKeyframes(boneObj.getAsJsonArray("position")),
                rotation = parseKeyframes(boneObj.getAsJsonArray("rotation")),
                scale = parseKeyframes(boneObj.getAsJsonArray("scale")),
            )
        }

        return AnimationBlueprint(name, length, loop, boneKeyframes)
    }

    private fun parseKeyframes(array: com.google.gson.JsonArray?): List<Keyframe> {
        if (array == null) return emptyList()
        return array.map { element ->
            val obj = element.asJsonObject
            val time = obj["time"].asFloat
            val value = obj.readVec("value")
            val interpStr = obj["interpolation"]?.asString ?: "linear"
            val interpolation = when (interpStr.lowercase()) {
                "catmullrom" -> InterpolationType.CATMULLROM
                "bezier" -> InterpolationType.BEZIER
                "step" -> InterpolationType.STEP
                else -> InterpolationType.LINEAR
            }
            Keyframe(
                time = time,
                value = value,
                interpolation = interpolation,
                bezierLeftTime = obj.readVec("bezierLeftTime"),
                bezierLeftValue = obj.readVec("bezierLeftValue"),
                bezierRightTime = obj.readVec("bezierRightTime"),
                bezierRightValue = obj.readVec("bezierRightValue"),
            )
        }
    }

    private fun parseBehaviors(obj: JsonObject): Map<BoneBehaviorType, Map<String, Any>> {
        val result = mutableMapOf<BoneBehaviorType, Map<String, Any>>()
        obj.entrySet().forEach { (key, value) ->
            val type = runCatching { BoneBehaviorType.valueOf(key.uppercase()) }.getOrNull() ?: return@forEach
            val config = if (value.isJsonObject) {
                value.asJsonObject.entrySet().mapNotNull { (k, v) ->
                    when {
                        v.isJsonNull -> null
                        v.isJsonPrimitive && v.asJsonPrimitive.isNumber -> k to (v.asDouble as Any)
                        v.isJsonPrimitive && v.asJsonPrimitive.isBoolean -> k to (v.asBoolean as Any)
                        v.isJsonPrimitive && v.asJsonPrimitive.isString -> k to (v.asString as Any)
                        else -> null
                    }
                }.toMap()
            } else emptyMap()
            result[type] = config
        }
        return result
    }

    private fun JsonObject.readVec(key: String, default: Vec = Vec.ZERO): Vec {
        val arr = getAsJsonArray(key) ?: return default
        if (arr.size() < 3) return default
        return Vec(
            arr[0].asDouble,
            arr[1].asDouble,
            arr[2].asDouble,
        )
    }

    private fun JsonObject.readRotation(key: String): me.nebula.orbit.utils.modelengine.math.Quat {
        val arr = getAsJsonArray(key) ?: return QUAT_IDENTITY
        return eulerToQuat(arr[0].asFloat, arr[1].asFloat, arr[2].asFloat)
    }
}
