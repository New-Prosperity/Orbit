package me.nebula.orbit.utils.statue

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.orbit.utils.modelengine.generator.BbAnimation
import me.nebula.orbit.utils.modelengine.generator.BbElement
import me.nebula.orbit.utils.modelengine.generator.BbGroup
import me.nebula.orbit.utils.modelengine.generator.BbGroupChild
import me.nebula.orbit.utils.modelengine.generator.BbKeyframe
import me.nebula.orbit.utils.modelengine.generator.BlockbenchModel
import net.minestom.server.coordinate.Vec

internal fun exportBbmodelJson(model: BlockbenchModel): String {
    val root = JsonObject()

    root.add("meta", JsonObject().apply {
        addProperty("format_version", model.meta.formatVersion)
        addProperty("model_format", model.meta.modelFormat)
        addProperty("box_uv", model.meta.boxUv)
    })

    root.addProperty("name", model.name)

    root.add("resolution", JsonObject().apply {
        addProperty("width", model.resolution.width)
        addProperty("height", model.resolution.height)
    })

    root.add("elements", JsonArray().apply {
        for (element in model.elements) {
            add(serializeElement(element))
        }
    })

    root.add("outliner", JsonArray().apply {
        for (group in model.groups) {
            add(serializeGroup(group))
        }
    })

    root.add("textures", JsonArray().apply {
        for (texture in model.textures) {
            add(JsonObject().apply {
                addProperty("id", texture.id)
                addProperty("name", texture.name)
                addProperty("width", texture.width)
                addProperty("height", texture.height)
                addProperty("source", texture.source)
            })
        }
    })

    root.add("animations", JsonArray().apply {
        for (animation in model.animations) {
            add(serializeAnimation(animation))
        }
    })

    if (model.display.isNotEmpty()) {
        root.add("display", JsonObject().apply {
            for ((key, slot) in model.display) {
                add(key, JsonObject().apply {
                    add("rotation", slot.rotation.toJsonArray())
                    add("translation", slot.translation.toJsonArray())
                    add("scale", slot.scale.toJsonArray())
                })
            }
        })
    }

    return GsonProvider.pretty.toJson(root)
}

private fun serializeElement(element: BbElement): JsonObject = JsonObject().apply {
    addProperty("uuid", element.uuid)
    addProperty("name", element.name)
    add("from", element.from.toJsonArray())
    add("to", element.to.toJsonArray())
    add("origin", element.origin.toJsonArray())
    add("rotation", element.rotation.toJsonArray())
    addProperty("inflate", element.inflate)
    addProperty("visibility", element.visibility)
    addProperty("mirror_uv", element.mirrorUv)
    addProperty("light_emission", element.lightEmission)
    add("faces", JsonObject().apply {
        for ((faceName, face) in element.faces) {
            add(faceName, JsonObject().apply {
                add("uv", face.uv.toJsonArray())
                addProperty("texture", face.texture)
                if (face.rotation != 0) addProperty("rotation", face.rotation)
            })
        }
    })
}

private fun serializeGroup(group: BbGroup): JsonObject = JsonObject().apply {
    addProperty("uuid", group.uuid)
    addProperty("name", group.name)
    add("origin", group.origin.toJsonArray())
    add("rotation", group.rotation.toJsonArray())
    addProperty("visibility", group.visibility)
    add("children", JsonArray().apply {
        for (child in group.children) {
            when (child) {
                is BbGroupChild.ElementRef -> add(child.uuid)
                is BbGroupChild.SubGroup -> add(serializeGroup(child.group))
            }
        }
    })
}

private fun serializeAnimation(animation: BbAnimation): JsonObject = JsonObject().apply {
    addProperty("name", animation.name)
    addProperty("length", animation.length)
    addProperty("loop", animation.loop)
    add("animators", JsonObject().apply {
        for ((uuid, animator) in animation.animators) {
            add(uuid, JsonObject().apply {
                addProperty("name", animator.name)
                add("keyframes", JsonArray().apply {
                    for (keyframe in animator.keyframes) {
                        add(serializeKeyframe(keyframe))
                    }
                })
            })
        }
    })
}

private fun serializeKeyframe(keyframe: BbKeyframe): JsonObject = JsonObject().apply {
    addProperty("channel", keyframe.channel)
    addProperty("time", keyframe.time)
    addProperty("interpolation", keyframe.interpolation)
    add("data_points", JsonArray().apply {
        for (point in keyframe.dataPoints) {
            add(JsonObject().apply {
                addProperty("x", point.x())
                addProperty("y", point.y())
                addProperty("z", point.z())
            })
        }
    })
    if (keyframe.bezierLeftTime != Vec.ZERO) add("bezier_left_time", keyframe.bezierLeftTime.toJsonArray())
    if (keyframe.bezierLeftValue != Vec.ZERO) add("bezier_left_value", keyframe.bezierLeftValue.toJsonArray())
    if (keyframe.bezierRightTime != Vec.ZERO) add("bezier_right_time", keyframe.bezierRightTime.toJsonArray())
    if (keyframe.bezierRightValue != Vec.ZERO) add("bezier_right_value", keyframe.bezierRightValue.toJsonArray())
}

private fun Vec.toJsonArray(): JsonArray = JsonArray().apply {
    add(x())
    add(y())
    add(z())
}

private fun FloatArray.toJsonArray(): JsonArray = JsonArray().apply {
    for (v in this@toJsonArray) add(v)
}
