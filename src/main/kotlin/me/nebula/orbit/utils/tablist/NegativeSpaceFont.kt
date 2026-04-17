package me.nebula.orbit.utils.tablist

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.nebula.ether.utils.gson.GsonProvider
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object NegativeSpaceFont {

    private val POSITIVE_SHIFTS = intArrayOf(1, 2, 4, 8, 16, 32, 64, 128)
    private val NEGATIVE_SHIFTS = intArrayOf(-1, -2, -4, -8, -16, -32, -64, -128)

    private val POSITIVE_CHARS = charArrayOf(
        '\uF101', '\uF102', '\uF104', '\uF108',
        '\uF110', '\uF120', '\uF140', '\uF180',
    )
    private val NEGATIVE_CHARS = charArrayOf(
        '\uF201', '\uF202', '\uF204', '\uF208',
        '\uF210', '\uF220', '\uF240', '\uF280',
    )

    fun shift(pixels: Int): String = buildString {
        if (pixels == 0) return@buildString
        val chars = if (pixels > 0) POSITIVE_CHARS else NEGATIVE_CHARS
        val shifts = if (pixels > 0) POSITIVE_SHIFTS else NEGATIVE_SHIFTS
        var remaining = if (pixels > 0) pixels else -pixels
        for (i in shifts.indices.reversed()) {
            val shiftAbs = if (pixels > 0) shifts[i] else -shifts[i]
            while (remaining >= shiftAbs) {
                append(chars[i])
                remaining -= shiftAbs
            }
        }
    }

    fun generate(): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        val gson = GsonProvider.pretty

        val splitPng = generateSplitTexture()
        entries["assets/minecraft/textures/font/ns_split.png"] = splitPng

        val json = JsonObject().apply {
            add("providers", JsonArray().apply {
                for (i in POSITIVE_SHIFTS.indices) {
                    add(buildProvider(POSITIVE_CHARS[i], POSITIVE_SHIFTS[i]))
                    add(buildProvider(NEGATIVE_CHARS[i], NEGATIVE_SHIFTS[i]))
                }
            })
        }
        entries["assets/minecraft/font/default.json"] = gson.toJson(json).toByteArray(Charsets.UTF_8)

        return entries
    }

    private fun buildProvider(char: Char, advance: Int): JsonObject = JsonObject().apply {
        addProperty("type", "bitmap")
        addProperty("file", "minecraft:font/ns_split.png")
        addProperty("ascent", -32768)
        addProperty("height", advance)
        add("chars", JsonArray().apply { add(char.toString()) })
    }

    private fun generateSplitTexture(): ByteArray {
        val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
        return baos.toByteArray()
    }
}
