package me.nebula.orbit.utils.customcontent.armor

import me.nebula.ether.utils.logging.logger
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

object ArmorShaderPack {

    private val logger = logger("ArmorShaderPack")

    private const val SHADER_RESOURCE_BASE = "shaders/armor"
    private const val LEATHER_LAYER_WIDTH = 64
    private const val LEATHER_LAYER_HEIGHT = 192
    private const val BBMODEL_AREA_HEIGHT = 64
    private const val MARKER_X = 63
    private const val LAYER_TYPE_Y = 62
    private const val COLOR_ID_Y = 63

    private const val REGION_TOP_Y = 64
    private const val REGION_BOTTOM_Y = 96
    private const val REGION_X_FACE_Y = 128
    private const val REGION_Z_FACE_Y = 160

    private val ARM_TOP_FACE = FaceAnchor(44, 16, 4, 4)
    private val ARM_BOTTOM_FACE = FaceAnchor(48, 16, 4, 4)
    private val ARM_X_FACES = listOf(
        FaceAnchor(40, 20, 4, 12),
        FaceAnchor(48, 20, 4, 12),
    )
    private val ARM_Z_FACES = listOf(
        FaceAnchor(44, 20, 4, 12),
        FaceAnchor(52, 20, 4, 12),
    )

    private val LEG_TOP_FACE = FaceAnchor(4, 16, 4, 4)
    private val LEG_BOTTOM_FACE = FaceAnchor(8, 16, 4, 4)
    private val LEG_X_FACES = listOf(
        FaceAnchor(0, 20, 4, 12),
        FaceAnchor(8, 20, 4, 12),
    )
    private val LEG_Z_FACES = listOf(
        FaceAnchor(4, 20, 4, 12),
        FaceAnchor(12, 20, 4, 12),
    )

    private val MIRROR_RIGHT = Color(255, 0, 64, 255)
    private val MIRROR_LEFT = Color(0, 64, 255, 255)

    private data class FaceAnchor(val ax: Int, val ay: Int, val sx: Int, val sy: Int)

    private val STATIC_FILES = mapOf(
        "assets/minecraft/shaders/core/entity.vsh" to "core/entity.vsh",
        "assets/minecraft/shaders/core/entity.fsh" to "core/entity.fsh",
        "assets/minecraft/shaders/include/cem/frag_funcs.glsl" to "include/cem/frag_funcs.glsl",
        "assets/minecraft/shaders/include/cem/frag_main_setup.glsl" to "include/cem/frag_main_setup.glsl",
        "assets/minecraft/shaders/include/cem/vert_setup.glsl" to "include/cem/vert_setup.glsl",
        "assets/minecraft/shaders/include/cem_faces.glsl" to "include/cem_faces.glsl",
        "assets/minecraft/shaders/include/cem_locs.glsl" to "include/cem_locs.glsl",
        "assets/minecraft/shaders/include/emissive_utils.glsl" to "include/emissive_utils.glsl",
        "assets/minecraft/shaders/include/fog_reader.glsl" to "include/fog_reader.glsl",
        "assets/minecraft/shaders/include/matf.glsl" to "include/matf.glsl",
        "assets/minecraft/shaders/include/mods/armor/armorparts.glsl" to "include/mods/armor/armorparts.glsl",
        "assets/minecraft/shaders/include/mods/armor/parts.glsl" to "include/mods/armor/parts.glsl",
        "assets/minecraft/shaders/include/mods/armor/setup.glsl" to "include/mods/armor/setup.glsl",
        "assets/minecraft/shaders/include/mods/parts.glsl" to "include/mods/parts.glsl",
        "assets/minecraft/shaders/include/mods/redefine.glsl" to "include/mods/redefine.glsl",
        "assets/minecraft/shaders/include/tools.glsl" to "include/tools.glsl",
        "assets/minecraft/shaders/include/wpos_etrans.glsl" to "include/wpos_etrans.glsl",
    )

    fun generate(armors: List<RegisteredArmor>): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()

        for ((packPath, resourcePath) in STATIC_FILES) {
            val bytes = readResource("$SHADER_RESOURCE_BASE/$resourcePath")
            if (bytes != null) {
                entries[packPath] = bytes
            } else {
                logger.warn { "Missing static shader resource: $resourcePath" }
            }
        }

        val armorGlsl = ArmorGlslGenerator.generateArmorGlsl(armors)
        entries["assets/minecraft/shaders/include/mods/armor/armor.glsl"] = armorGlsl.toByteArray(Charsets.UTF_8)

        val armorcordsGlsl = ArmorGlslGenerator.generateArmorcordsGlsl(armors)
        entries["assets/minecraft/shaders/include/armorcords.glsl"] = armorcordsGlsl.toByteArray(Charsets.UTF_8)

        entries["assets/minecraft/textures/entity/equipment/humanoid/leather.png"] =
            generateLeatherLayer(armors, isLayerTwo = false)
        entries["assets/minecraft/textures/entity/equipment/humanoid_leggings/leather.png"] =
            generateLeatherLayer(armors, isLayerTwo = true)

        val transparentOverlay = generateTransparentPng()
        entries["assets/minecraft/textures/entity/equipment/humanoid/leather_overlay.png"] = transparentOverlay
        entries["assets/minecraft/textures/entity/equipment/humanoid_leggings/leather_overlay.png"] = transparentOverlay

        logger.info { "Generated armor shader pack: ${armors.size} armors, ${entries.size} entries" }
        return entries
    }

    private fun generateLeatherLayer(armors: List<RegisteredArmor>, isLayerTwo: Boolean): ByteArray {
        val maxColorId = armors.maxOfOrNull { it.colorId } ?: 0
        val atlasWidth = LEATHER_LAYER_WIDTH * (maxColorId + 1)
        val img = BufferedImage(atlasWidth, LEATHER_LAYER_HEIGHT, BufferedImage.TYPE_INT_ARGB)

        val layerMarker = if (isLayerTwo) Color(255, 255, 255, 255) else Color(0, 0, 0, 255)

        val g0 = img.createGraphics()
        g0.color = Color(255, 255, 255, 255)
        g0.fillRect(0, 0, LEATHER_LAYER_WIDTH, LEATHER_LAYER_HEIGHT)
        g0.dispose()
        img.setRGB(MARKER_X, LAYER_TYPE_Y, layerMarker.rgb)
        img.setRGB(MARKER_X, COLOR_ID_Y, Color(0, 0, 0, 255).rgb)

        for (armor in armors) {
            val cellX = armor.colorId * LEATHER_LAYER_WIDTH

            val targetLayer = if (isLayerTwo) 2 else 1
            val texIndex = armor.parsed.pieces.firstOrNull { it.part.layer == targetLayer }?.textureIndex
                ?: armor.parsed.pieces.firstOrNull()?.textureIndex
                ?: 0
            val source = armor.parsed.textures.getOrNull(texIndex)?.source
                ?: armor.parsed.textures.firstOrNull()?.source
                ?: ""
            val texture = decodeBase64Image(source)
            if (texture != null) {
                val g = img.createGraphics()
                g.drawImage(texture, cellX, 0, LEATHER_LAYER_WIDTH, BBMODEL_AREA_HEIGHT, null)
                g.dispose()
            } else {
                logger.warn { "Failed to decode texture for armor '${armor.id}' layer $targetLayer" }
            }

            clearMarkerRegions(img, cellX)
            placeFaceMarkers(img, cellX, ARM_TOP_FACE, REGION_TOP_Y)
            placeFaceMarkers(img, cellX, ARM_BOTTOM_FACE, REGION_BOTTOM_Y)
            for (f in ARM_X_FACES) placeFaceMarkers(img, cellX, f, REGION_X_FACE_Y)
            for (f in ARM_Z_FACES) placeFaceMarkers(img, cellX, f, REGION_Z_FACE_Y)
            placeFaceMarkers(img, cellX, LEG_TOP_FACE, REGION_TOP_Y)
            placeFaceMarkers(img, cellX, LEG_BOTTOM_FACE, REGION_BOTTOM_Y)
            for (f in LEG_X_FACES) placeFaceMarkers(img, cellX, f, REGION_X_FACE_Y)
            for (f in LEG_Z_FACES) placeFaceMarkers(img, cellX, f, REGION_Z_FACE_Y)

            img.setRGB(cellX + MARKER_X, LAYER_TYPE_Y, layerMarker.rgb)
            img.setRGB(cellX + MARKER_X, COLOR_ID_Y, Color(armor.colorR, armor.colorG, armor.colorB, 255).rgb)
        }

        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
        return baos.toByteArray()
    }

    private fun clearMarkerRegions(img: BufferedImage, cellX: Int) {
        val g = img.createGraphics()
        g.color = Color(0, 0, 0, 0)
        g.composite = java.awt.AlphaComposite.Src
        g.fillRect(cellX, BBMODEL_AREA_HEIGHT, LEATHER_LAYER_WIDTH, LEATHER_LAYER_HEIGHT - BBMODEL_AREA_HEIGHT)
        g.dispose()
    }

    private fun placeFaceMarkers(img: BufferedImage, cellX: Int, face: FaceAnchor, regionY: Int) {
        val nonMirroredPositions = listOf(
            face.ax - 1 to face.ay - 1 + regionY,
            face.ax to face.ay + face.sy - 1 + regionY,
            face.ax + face.sx to face.ay + face.sy + regionY,
            face.ax + face.sx - 1 to face.ay + regionY,
        )
        val mirroredPositions = listOf(
            face.ax + face.sx - 1 to face.ay - 1 + regionY,
            face.ax + face.sx to face.ay + face.sy - 1 + regionY,
            face.ax to face.ay + face.sy + regionY,
            face.ax - 1 to face.ay + regionY,
        )
        for ((px, py) in nonMirroredPositions) {
            setMarker(img, cellX + px, py, MIRROR_RIGHT)
        }
        for ((px, py) in mirroredPositions) {
            setMarker(img, cellX + px, py, MIRROR_LEFT)
        }
    }

    private fun setMarker(img: BufferedImage, x: Int, y: Int, color: Color) {
        if (x < 0 || x >= img.width || y < 0 || y >= img.height) return
        img.setRGB(x, y, color.rgb)
    }

    private fun decodeBase64Image(source: String): BufferedImage? {
        if (source.isBlank()) return null
        return runCatching {
            val data = if (source.contains(",")) source.substringAfter(",") else source
            val bytes = Base64.getDecoder().decode(data)
            ImageIO.read(ByteArrayInputStream(bytes))
        }.onFailure { logger.warn { "Base64 image decode error: ${it.message}" } }.getOrNull()
    }

    private fun generateTransparentPng(): ByteArray {
        val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, 0)
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
        return baos.toByteArray()
    }

    private fun readResource(path: String): ByteArray? =
        Thread.currentThread().contextClassLoader
            .getResourceAsStream(path)
            ?.use { it.readAllBytes() }
}
