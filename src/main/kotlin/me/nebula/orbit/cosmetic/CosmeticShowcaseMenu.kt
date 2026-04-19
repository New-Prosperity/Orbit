package me.nebula.orbit.cosmetic

import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import java.util.UUID
import me.nebula.gravity.translation.Keys
import me.nebula.ether.utils.translation.asTranslationKey

object CosmeticShowcaseMenu {

    private val categorySlots = listOf(
        Triple(CosmeticCategory.ARMOR_SKIN, 10, Material.LEATHER_CHESTPLATE),
        Triple(CosmeticCategory.KILL_EFFECT, 11, Material.REDSTONE),
        Triple(CosmeticCategory.TRAIL, 12, Material.BLAZE_POWDER),
        Triple(CosmeticCategory.WIN_EFFECT, 13, Material.FIREWORK_ROCKET),
        Triple(CosmeticCategory.PROJECTILE_TRAIL, 14, Material.ARROW),
        Triple(CosmeticCategory.COMPANION, 19, Material.ARMOR_STAND),
        Triple(CosmeticCategory.PET, 20, Material.BONE),
        Triple(CosmeticCategory.MOUNT, 21, Material.SADDLE),
        Triple(CosmeticCategory.SPAWN_EFFECT, 22, Material.ENDER_PEARL),
        Triple(CosmeticCategory.DEATH_EFFECT, 23, Material.WITHER_SKELETON_SKULL),
        Triple(CosmeticCategory.AURA, 28, Material.NETHER_STAR),
        Triple(CosmeticCategory.ELIMINATION_MESSAGE, 29, Material.NAME_TAG),
        Triple(CosmeticCategory.JOIN_QUIT_MESSAGE, 30, Material.OAK_SIGN),
        Triple(CosmeticCategory.GADGET, 31, Material.BLAZE_ROD),
        Triple(CosmeticCategory.GRAVESTONE, 32, Material.MOSSY_COBBLESTONE),
    )

    fun open(viewer: Player, targetUuid: UUID, targetName: String) {
        val data = CosmeticStore.load(targetUuid)
        val title = viewer.translateRaw(Keys.Orbit.Cosmetic.ShowcaseTitle, "player" to targetName)

        val gui = gui(title, rows = 5) {
            categorySlots.forEach { (category, slot, defaultMaterial) ->
                val equippedId = data?.equipped?.get(category.name)
                val definition = equippedId?.let { CosmeticRegistry[it] }

                if (definition != null) {
                    val level = data.owned[equippedId] ?: 1
                    val material = Material.fromKey(definition.material) ?: defaultMaterial
                    val rarityName = viewer.translateRaw("orbit.cosmetic.rarity.${definition.rarity.name.lowercase()}".asTranslationKey())
                    slot(slot, itemStack(material) {
                        name("${definition.rarity.colorTag}${viewer.translateRaw(definition.nameKey)}")
                        lore("<gray>${viewer.translateRaw(category.displayKey)}")
                        lore("${definition.rarity.colorTag}$rarityName")
                        if (definition.maxLevel > 1) {
                            lore("<white>${viewer.translateRaw(Keys.Orbit.Cosmetic.Level, "level" to "$level", "max" to "${definition.maxLevel}")}")
                        }
                        glowing()
                        clean()
                    })
                } else {
                    slot(slot, itemStack(Material.GRAY_STAINED_GLASS_PANE) {
                        name("<gray>${viewer.translateRaw(category.displayKey)}")
                        lore("<dark_gray>${viewer.translateRaw(Keys.Orbit.Cosmetic.NoneEquipped)}")
                        clean()
                    })
                }
            }
            fillDefault()
            closeButton(40)
        }
        viewer.openGui(gui)
    }
}
