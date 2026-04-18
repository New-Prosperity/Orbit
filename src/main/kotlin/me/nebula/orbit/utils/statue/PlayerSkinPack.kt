package me.nebula.orbit.utils.statue

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.modelengine.model.StandaloneModelOwner
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.CustomModelData
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.ResolvableProfile
import java.util.UUID

object PlayerSkinPack {

    private val logger = logger("PlayerSkinPack")
    private val gson = GsonProvider.pretty

    private val STATIC_FILES = mapOf(
        "assets/minecraft/shaders/core/entity.vsh" to "shaders/armor/core/entity.vsh",
        "assets/minecraft/shaders/core/entity.fsh" to "shaders/armor/core/entity.fsh",
    )

    enum class BodyPart(
        val customModelData: Int,
        val partId: Int,
        val baseModelPath: String,
        val translation: FloatArray,
        val scale: FloatArray,
        val rotation: FloatArray = floatArrayOf(180f, 180f, 0f),
    ) {
        HEAD(1, 0, "custom/entities/player/head",
            floatArrayOf(0f, -9f, 0f), floatArrayOf(1f, 1f, 1f)),
        RIGHT_ARM(2, 1, "custom/entities/player/right_arm",
            floatArrayOf(0.9375f, -23f, 0f), floatArrayOf(0.5f, 1.5f, 0.5f)),
        LEFT_ARM(3, 2, "custom/entities/player/left_arm",
            floatArrayOf(-0.9375f, -23f, 0f), floatArrayOf(0.5f, 1.5f, 0.5f)),
        TORSO(4, 5, "custom/entities/player/torso",
            floatArrayOf(0f, -25f, 0f), floatArrayOf(1f, 1.5f, 0.5f)),
        RIGHT_LEG(5, 6, "custom/entities/player/right_leg",
            floatArrayOf(-0.1f, -24f, 0f), floatArrayOf(0.5f, 1.5f, 0.5f)),
        LEFT_LEG(6, 7, "custom/entities/player/left_leg",
            floatArrayOf(0.1f, -24f, 0f), floatArrayOf(0.5f, 1.5f, 0.5f)),
    }

    const val SPACING = 1024.0

    fun generate(): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()

        for ((packPath, resourcePath) in STATIC_FILES) {
            val bytes = readResource(resourcePath)
            if (bytes != null) {
                entries[packPath] = bytes
            } else {
                logger.warn { "Missing static shader resource: $resourcePath" }
            }
        }

        entries["assets/minecraft/items/player_head.json"] =
            gson.toJson(buildPlayerHeadItemDef()).toByteArray(Charsets.UTF_8)

        for (part in BodyPart.entries) {
            entries["assets/minecraft/models/${part.baseModelPath}.json"] =
                gson.toJson(buildBaseModel(part)).toByteArray(Charsets.UTF_8)
        }

        logger.info { "Generated player skin pack: ${entries.size} entries" }
        return entries
    }

    private fun buildPlayerHeadItemDef(): JsonObject = JsonObject().apply {
        add("model", JsonObject().apply {
            addProperty("type", "minecraft:range_dispatch")
            addProperty("property", "minecraft:custom_model_data")
            add("entries", JsonArray().apply {
                for (part in BodyPart.entries) {
                    add(JsonObject().apply {
                        addProperty("threshold", part.customModelData)
                        add("model", JsonObject().apply {
                            addProperty("type", "minecraft:special")
                            addProperty("base", "minecraft:${part.baseModelPath}")
                            add("model", JsonObject().apply {
                                addProperty("type", "minecraft:player_head")
                            })
                        })
                    })
                }
            })
            add("fallback", JsonObject().apply {
                addProperty("type", "minecraft:special")
                addProperty("base", "minecraft:item/template_skull")
                add("model", JsonObject().apply {
                    addProperty("type", "minecraft:player_head")
                })
            })
            add("transformation", JsonObject().apply {
                add("left_rotation", JsonArray().apply { add(0f); add(0f); add(0f); add(1f) })
                add("right_rotation", JsonArray().apply { add(0f); add(0f); add(0f); add(1f) })
                add("scale", JsonArray().apply { add(1f); add(1f); add(1f) })
                add("translation", JsonArray().apply { add(0.5f); add(0f); add(0.5f) })
            })
        })
    }

    private fun buildBaseModel(part: BodyPart): JsonObject = JsonObject().apply {
        addProperty("parent", "builtin/entity")
        add("display", JsonObject().apply {
            add("thirdperson_righthand", JsonObject().apply {
                add("rotation", JsonArray().apply {
                    part.rotation.forEach { add(it) }
                })
                add("translation", JsonArray().apply {
                    part.translation.forEach { add(it) }
                })
                add("scale", JsonArray().apply {
                    part.scale.forEach { add(it) }
                })
            })
        })
    }

    private fun readResource(path: String): ByteArray? =
        Thread.currentThread().contextClassLoader
            .getResourceAsStream(path)
            ?.use { it.readAllBytes() }

    fun applyTo(owner: StandaloneModelOwner, skin: PlayerSkin) {
        val profile = ResolvableProfile(GameProfile(
            UUID(0L, 0L), "statue",
            listOf(GameProfile.Property("textures", skin.textures(), skin.signature())),
        ))
        owner.modeledEntity?.models?.values?.forEach { active ->
            active.bones.forEach { (name, bone) ->
                val part = bonePartOf(name) ?: return@forEach
                bone.modelItem = ItemStack.of(Material.PLAYER_HEAD)
                    .with(DataComponents.PROFILE, profile)
                    .with(DataComponents.CUSTOM_MODEL_DATA,
                        CustomModelData(listOf(part.customModelData.toFloat()), emptyList(), emptyList(), emptyList()))
                bone.skinPartId = part.partId
            }
        }
    }

    private fun bonePartOf(boneName: String): BodyPart? = when (boneName) {
        "head" -> BodyPart.HEAD
        "body" -> BodyPart.TORSO
        "right_arm" -> BodyPart.LEFT_ARM
        "left_arm" -> BodyPart.RIGHT_ARM
        "right_leg" -> BodyPart.LEFT_LEG
        "left_leg" -> BodyPart.RIGHT_LEG
        else -> null
    }
}
