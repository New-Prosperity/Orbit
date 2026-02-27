package me.nebula.orbit.utils.customcontent.armor

import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.modelengine.generator.BlockbenchParser
import me.nebula.orbit.utils.modelengine.generator.ModelIdRegistry
import net.minestom.server.color.Color
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.concurrent.ConcurrentHashMap

object CustomArmorRegistry {

    private val logger = logger("CustomArmor")
    private val armors = ConcurrentHashMap<String, RegisteredArmor>()

    fun all(): Collection<RegisteredArmor> = armors.values

    operator fun get(id: String): RegisteredArmor? = armors[id]

    fun register(id: String, parsed: ParsedArmor): RegisteredArmor {
        val colorId = ModelIdRegistry.assignId("cc:armor:$id")
        val (r, g, b) = colorIdToRgb(colorId)

        val armor = RegisteredArmor(
            id = id,
            colorId = colorId,
            colorR = r,
            colorG = g,
            colorB = b,
            parsed = parsed,
        )
        armors[id] = armor
        logger.info { "Registered custom armor: $id (colorId=$colorId, rgb=$r,$g,$b, pieces=${parsed.pieces.size})" }
        return armor
    }

    fun loadFromResources(resources: ResourceManager, directory: String) {
        resources.ensureDirectory(directory)
        val files = resources.list(directory, "bbmodel", recursive = false)
        for (path in files) {
            val fileName = path.substringAfterLast('/')
            val armorId = fileName.removeSuffix(".bbmodel")
            logger.info { "Loading armor model: $fileName" }

            val model = resources.reader(path).use { BlockbenchParser.parse(armorId, it) }
            val parsed = ArmorParser.parse(model)

            if (parsed.pieces.isEmpty()) {
                logger.warn { "Armor '$armorId' has no recognized bone prefixes, skipping" }
                continue
            }

            register(armorId, parsed)
            logger.info { "Armor '$armorId': ${parsed.pieces.joinToString { "${it.part.id}(${it.cubes.size} cubes)" }}" }
        }
    }

    fun isEmpty(): Boolean = armors.isEmpty()

    fun clear() {
        armors.clear()
    }

    private fun colorIdToRgb(colorId: Int): Triple<Int, Int, Int> {
        require(colorId in 1..16_777_215) { "Color ID out of range: $colorId" }
        val r = colorId and 0xFF
        val g = (colorId shr 8) and 0xFF
        val b = (colorId shr 16) and 0xFF
        return Triple(r, g, b)
    }
}

fun RegisteredArmor.createItem(part: ArmorPart): ItemStack {
    val material = when (part) {
        ArmorPart.Helmet -> Material.LEATHER_HELMET
        ArmorPart.Chestplate, ArmorPart.RightArm, ArmorPart.LeftArm -> Material.LEATHER_CHESTPLATE
        ArmorPart.InnerArmor, ArmorPart.RightLeg, ArmorPart.LeftLeg -> Material.LEATHER_LEGGINGS
        ArmorPart.RightBoot, ArmorPart.LeftBoot -> Material.LEATHER_BOOTS
    }
    return ItemStack.of(material).with { builder ->
        builder.set(DataComponents.DYED_COLOR, Color(colorR, colorG, colorB))
    }
}

fun RegisteredArmor.equipFullSet(player: Player) {
    val helmet = createItem(ArmorPart.Helmet)
    val chestplate = createItem(ArmorPart.Chestplate)
    val leggings = createItem(ArmorPart.InnerArmor)
    val boots = createItem(ArmorPart.RightBoot)
    player.setEquipment(EquipmentSlot.HELMET, helmet)
    player.setEquipment(EquipmentSlot.CHESTPLATE, chestplate)
    player.setEquipment(EquipmentSlot.LEGGINGS, leggings)
    player.setEquipment(EquipmentSlot.BOOTS, boots)
}
