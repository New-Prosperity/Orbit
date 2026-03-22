package me.nebula.orbit.utils.tooltip

import me.nebula.ether.utils.logging.logger
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

data class TooltipStyleDef(
    val id: String,
    val borderColor: Color,
    val highlightColor: Color,
    val backgroundColor: Color = Color(16, 0, 20, 240),
)

object TooltipStyleRegistry {

    private val styles = ConcurrentHashMap<String, TooltipStyleDef>()

    fun register(style: TooltipStyleDef) {
        styles[style.id] = style
    }

    operator fun get(id: String): TooltipStyleDef? = styles[id]
    fun all(): Collection<TooltipStyleDef> = styles.values
    fun ids(): Set<String> = styles.keys.toSet()
    fun isEmpty(): Boolean = styles.isEmpty()

    fun registerDefaults() {
        register(TooltipStyleDef("royal", Color(212, 140, 22), Color(230, 192, 44)))
        register(TooltipStyleDef("epic", Color(160, 32, 240), Color(190, 100, 255)))
        register(TooltipStyleDef("rare", Color(0, 112, 221), Color(80, 160, 255)))
        register(TooltipStyleDef("legendary", Color(255, 128, 0), Color(255, 180, 60)))
        register(TooltipStyleDef("mythic", Color(230, 0, 26), Color(255, 80, 80)))
        register(TooltipStyleDef("common", Color(128, 128, 128), Color(180, 180, 180)))
    }
}

object TooltipStylePack {

    private val logger = logger("TooltipStylePack")
    private const val SIZE = 100
    private const val BG_BORDER = 9
    private const val FR_BORDER = 10

    fun generate(): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()

        for (style in TooltipStyleRegistry.all()) {
            val base = "assets/minecraft/textures/gui/sprites/tooltip/${style.id}"
            entries["${base}_background.png"] = generateBackground(style)
            entries["${base}_background.png.mcmeta"] = bgMcmeta()
            entries["${base}_frame.png"] = generateFrame(style)
            entries["${base}_frame.png.mcmeta"] = frMcmeta()
        }

        logger.info { "Generated tooltip sprites: ${TooltipStyleRegistry.ids().joinToString()}" }
        return entries
    }

    private fun generateBackground(style: TooltipStyleDef): ByteArray {
        val img = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val bg = style.backgroundColor
        val border = style.borderColor

        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val dx = minOf(x, SIZE - 1 - x)
                val dy = minOf(y, SIZE - 1 - y)
                val d = minOf(dx, dy)

                val c = when {
                    d == 0 -> Color(0, 0, 0, 0)
                    d <= 2 -> Color(border.red, border.green, border.blue, 180)
                    else -> bg
                }
                img.setRGB(x, y, c.rgb)
            }
        }
        return toPng(img)
    }

    private fun generateFrame(style: TooltipStyleDef): ByteArray {
        val img = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val border = style.borderColor
        val highlight = style.highlightColor

        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val dx = minOf(x, SIZE - 1 - x)
                val dy = minOf(y, SIZE - 1 - y)
                val d = minOf(dx, dy)

                val c = when {
                    d == 0 -> border
                    d == 1 -> border
                    d == 2 -> highlight
                    d == 3 -> Color(highlight.red, highlight.green, highlight.blue, 100)
                    d == 4 -> Color(highlight.red, highlight.green, highlight.blue, 40)
                    else -> Color(0, 0, 0, 0)
                }
                img.setRGB(x, y, c.rgb)
            }
        }
        return toPng(img)
    }

    private fun bgMcmeta(): ByteArray =
        """{"gui":{"scaling":{"type":"nine_slice","width":$SIZE,"height":$SIZE,"border":$BG_BORDER}}}""".toByteArray()

    private fun frMcmeta(): ByteArray =
        """{"gui":{"scaling":{"type":"nine_slice","width":$SIZE,"height":$SIZE,"border":$FR_BORDER,"stretch_inner":true}}}""".toByteArray()

    private fun toPng(img: BufferedImage): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }
}

fun ItemStack.withTooltipStyle(styleId: String): ItemStack =
    with(DataComponents.TOOLTIP_STYLE, "minecraft:$styleId")
