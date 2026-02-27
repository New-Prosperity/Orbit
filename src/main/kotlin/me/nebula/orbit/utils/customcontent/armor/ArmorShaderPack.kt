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
    private const val LEATHER_LAYER_HEIGHT = 32
    private const val MARKER_X = 63
    private const val LAYER_TYPE_Y = 30
    private const val COLOR_ID_Y = 31

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

        logger.info { "Generated armor shader pack: ${armors.size} armors, ${entries.size} entries" }
        return entries
    }

    private fun generateLeatherLayer(armors: List<RegisteredArmor>, isLayerTwo: Boolean): ByteArray {
        val maxColorId = armors.maxOfOrNull { it.colorId } ?: 0
        val atlasWidth = LEATHER_LAYER_WIDTH * (maxColorId + 1)
        val img = BufferedImage(atlasWidth, LEATHER_LAYER_HEIGHT, BufferedImage.TYPE_INT_ARGB)

        val g0 = img.createGraphics()
        g0.color = Color(255, 255, 255, 255)
        g0.fillRect(0, 0, LEATHER_LAYER_WIDTH, LEATHER_LAYER_HEIGHT)
        g0.dispose()
        img.setRGB(MARKER_X, LAYER_TYPE_Y, 0)
        img.setRGB(MARKER_X, COLOR_ID_Y, 0)

        val layerMarker = if (isLayerTwo) Color(255, 255, 255, 255) else Color(0, 0, 0, 255)

        for (armor in armors) {
            val cellX = armor.colorId * LEATHER_LAYER_WIDTH

            val texIndex = if (isLayerTwo) 1 else 0
            val source = armor.parsed.textures.getOrElse(texIndex) { armor.parsed.textures.firstOrNull() }?.source ?: ""
            val texture = decodeBase64Image(source)
            if (texture != null) {
                val g = img.createGraphics()
                g.drawImage(texture, cellX, 0, LEATHER_LAYER_WIDTH, LEATHER_LAYER_HEIGHT, null)
                g.dispose()
            } else {
                logger.warn { "Failed to decode texture for armor '${armor.id}' layer ${texIndex + 1}" }
            }

            img.setRGB(cellX + MARKER_X, LAYER_TYPE_Y, layerMarker.rgb)
            img.setRGB(cellX + MARKER_X, COLOR_ID_Y, Color(armor.colorR, armor.colorG, armor.colorB, 255).rgb)
        }

        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
        return baos.toByteArray()
    }

    private fun decodeBase64Image(source: String): BufferedImage? {
        if (source.isBlank()) return null
        return runCatching {
            val data = if (source.contains(",")) source.substringAfter(",") else source
            val bytes = Base64.getDecoder().decode(data)
            ImageIO.read(ByteArrayInputStream(bytes))
        }.onFailure { logger.warn { "Base64 image decode error: ${it.message}" } }.getOrNull()
    }

    private fun readResource(path: String): ByteArray? =
        Thread.currentThread().contextClassLoader
            .getResourceAsStream(path)
            ?.use { it.readAllBytes() }
}
