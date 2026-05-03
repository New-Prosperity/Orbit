package me.nebula.orbit.utils.customcontent.helditem

import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.modelengine.generator.BlockbenchParser
import me.nebula.orbit.utils.modelengine.generator.ModelIdRegistry
import net.minestom.server.color.Color
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Vec
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

object HeldItemAnimationRegistry {

    private val log = logger("HeldItemAnim")
    private val items = ConcurrentHashMap<String, RegisteredHeldItem>()
    private val byColorPrefix = ConcurrentHashMap<Int, RegisteredHeldItem>()

    val DEFAULT_MATERIAL: Material = Material.LEATHER_HORSE_ARMOR

    fun all(): Collection<RegisteredHeldItem> = items.values

    operator fun get(id: String): RegisteredHeldItem? = items[id]

    fun register(id: String, parsed: ParsedHeldItem): RegisteredHeldItem {
        require(!items.containsKey(id)) { "Duplicate held-item registration: $id" }
        val colorId = ModelIdRegistry.assignId("cc:helditem:$id")
        val (r, g, b) = colorIdToRgb(colorId)

        val entry = RegisteredHeldItem(
            id = id,
            colorId = colorId,
            colorR = r,
            colorG = g,
            colorB = b,
            parsed = parsed,
            modelScale = computeModelScale(parsed),
        )
        items[id] = entry
        byColorPrefix[colorPrefixKey(r, g, b)] = entry
        log.info { "Registered held-item: $id (colorId=$colorId, rgb=$r,$g,$b, bones=${parsed.bones.size}, anims=${parsed.animations.size}, scale=${entry.modelScale})" }
        return entry
    }

    private fun computeModelScale(parsed: ParsedHeldItem): Float {
        var maxAbs = 0f
        for (bone in parsed.bones) {
            for (cube in bone.cubes) {
                maxAbs = max(maxAbs, absMax(cube.from))
                maxAbs = max(maxAbs, absMax(cube.to))
                maxAbs = max(maxAbs, absMax(cube.origin))
            }
            maxAbs = max(maxAbs, absMax(bone.pivot))
        }
        return if (maxAbs > MAX_COORD) maxAbs / MAX_COORD else 1f
    }

    private fun absMax(v: Vec): Float =
        max(
            max(abs(v.x().toFloat()), abs(v.y().toFloat())),
            abs(v.z().toFloat()),
        )

    private const val MAX_COORD: Float = 15.9f

    fun loadFromResources(resources: ResourceManager, directory: String) {
        resources.ensureDirectory(directory)
        val files = resources.list(directory, "bbmodel", recursive = false)
        val mainFiles = files.filter { !it.endsWith(".anim.bbmodel") }
        for (path in mainFiles) {
            val fileName = path.substringAfterLast('/')
            val id = fileName.removeSuffix(".bbmodel")
            log.info { "Loading held-item model: $fileName" }
            val mainModel = resources.reader(path).use { BlockbenchParser.parse(id, it) }

            val animPath = path.removeSuffix(".bbmodel") + ".anim.bbmodel"
            val animModel = if (resources.exists(animPath)) {
                log.info { "  + companion animations: ${animPath.substringAfterLast('/')}" }
                resources.reader(animPath).use { BlockbenchParser.parse(id, it) }
            } else null

            val parsed = HeldItemAnimationParser.parse(mainModel, animModel)
            if (parsed.bones.isEmpty()) {
                log.warn { "Held-item '$id' has no bones, skipping" }
                continue
            }
            register(id, parsed)
        }
    }

    fun isEmpty(): Boolean = items.isEmpty()

    fun clear() {
        items.clear()
        byColorPrefix.clear()
    }

    fun fromItem(stack: ItemStack): RegisteredHeldItem? {
        val color = stack.get(DataComponents.DYED_COLOR) ?: return null
        return byColorPrefix[colorPrefixKey(color.red(), color.green(), color.blue())]
    }

    private fun colorPrefixKey(r: Int, g: Int, b: Int): Int =
        ((r and 0xFC) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun colorIdToRgb(colorId: Int): Triple<Int, Int, Int> {
        require(colorId in 1..2_097_151) { "HeldItem color ID out of range: $colorId" }
        val spaced = colorId * 4
        val r = spaced and 0xFC
        val g = (spaced shr 8) and 0xFF
        val b = (spaced shr 16) and 0xFF
        return Triple(r, g, b)
    }
}

fun RegisteredHeldItem.createItem(
    material: Material = HeldItemAnimationRegistry.DEFAULT_MATERIAL,
    trigger: AnimationTrigger = AnimationTrigger.IDLE,
): ItemStack {
    val rgb = triggerColor(trigger)
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    return ItemStack.of(material).with { builder ->
        builder.set(DataComponents.DYED_COLOR, Color(r, g, b))
        builder.set(DataComponents.ITEM_MODEL, "minecraft:customcontent/helditems/$id")
    }
}

fun ItemStack.withHeldItemTrigger(trigger: AnimationTrigger): ItemStack {
    val entry = HeldItemAnimationRegistry.fromItem(this) ?: return this
    val rgb = entry.triggerColor(trigger)
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    return this.with { it.set(DataComponents.DYED_COLOR, Color(r, g, b)) }
}
