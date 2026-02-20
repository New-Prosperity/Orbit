package me.nebula.orbit.utils.modelengine.generator

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PackWriter {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun write(
        packName: String,
        packDescription: String,
        models: Map<String, GeneratedBoneModel>,
        textureBytes: Map<String, ByteArray>,
        packFormat: Int = 42,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            writeMcMeta(zip, packName, packDescription, packFormat)

            textureBytes.forEach { (path, data) ->
                zip.putNextEntry(ZipEntry("assets/minecraft/textures/modelengine/$path"))
                zip.write(data)
                zip.closeEntry()
            }

            models.forEach { (bonePath, boneModel) ->
                val modelJson = buildModelJson(boneModel)
                zip.putNextEntry(ZipEntry("assets/minecraft/models/modelengine/$bonePath.json"))
                zip.write(gson.toJson(modelJson).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun writeMcMeta(zip: ZipOutputStream, name: String, description: String, packFormat: Int) {
        val meta = JsonObject().apply {
            add("pack", JsonObject().apply {
                addProperty("pack_format", packFormat)
                addProperty("description", description)
            })
        }
        zip.putNextEntry(ZipEntry("pack.mcmeta"))
        zip.write(gson.toJson(meta).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildModelJson(model: GeneratedBoneModel): JsonObject = JsonObject().apply {
        addProperty("parent", "minecraft:item/generated")

        add("textures", JsonObject().apply {
            model.textures.forEachIndexed { i, path ->
                addProperty(i.toString(), path)
            }
        })

        if (model.elements.isNotEmpty()) {
            add("elements", JsonArray().apply {
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
                                    add("uv", JsonArray().also { arr ->
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

        add("display", JsonObject().apply {
            add("head", JsonObject().apply {
                add("translation", JsonArray().also { it.add(0); it.add(0); it.add(0) })
                add("scale", JsonArray().also { it.add(1); it.add(1); it.add(1) })
            })
        })
    }

    private fun FloatArray.toJsonArray(): JsonArray = JsonArray().also { arr -> forEach { arr.add(it) } }
}

data class GeneratedBoneModel(
    val textures: List<String>,
    val elements: List<GeneratedElement>,
)

data class GeneratedElement(
    val from: FloatArray,
    val to: FloatArray,
    val rotation: GeneratedRotation?,
    val faces: Map<String, GeneratedFace>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedElement) return false
        return from.contentEquals(other.from) && to.contentEquals(other.to) && rotation == other.rotation && faces == other.faces
    }

    override fun hashCode(): Int {
        var result = from.contentHashCode()
        result = 31 * result + to.contentHashCode()
        result = 31 * result + (rotation?.hashCode() ?: 0)
        result = 31 * result + faces.hashCode()
        return result
    }
}

data class GeneratedRotation(
    val angle: Float,
    val axis: String,
    val origin: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedRotation) return false
        return angle == other.angle && axis == other.axis && origin.contentEquals(other.origin)
    }

    override fun hashCode(): Int {
        var result = angle.hashCode()
        result = 31 * result + axis.hashCode()
        result = 31 * result + origin.contentHashCode()
        return result
    }
}

data class GeneratedFace(
    val uv: FloatArray,
    val textureIndex: Int,
    val rotation: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedFace) return false
        return uv.contentEquals(other.uv) && textureIndex == other.textureIndex && rotation == other.rotation
    }

    override fun hashCode(): Int {
        var result = uv.contentHashCode()
        result = 31 * result + textureIndex
        result = 31 * result + rotation
        return result
    }
}
