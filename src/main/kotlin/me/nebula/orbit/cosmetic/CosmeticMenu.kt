package me.nebula.orbit.cosmetic

import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.gravity.cosmetic.CosmeticPlayerData
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.cosmetic.EquipCosmeticProcessor
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material

object CosmeticMenu {

    private val categorySlots = mapOf(
        CosmeticCategory.ARMOR_SKIN to Pair(11, Material.LEATHER_CHESTPLATE),
        CosmeticCategory.KILL_EFFECT to Pair(12, Material.REDSTONE),
        CosmeticCategory.TRAIL to Pair(13, Material.BLAZE_POWDER),
        CosmeticCategory.WIN_EFFECT to Pair(14, Material.FIREWORK_ROCKET),
        CosmeticCategory.PROJECTILE_TRAIL to Pair(15, Material.ARROW),
    )

    fun openCategoryMenu(player: Player) {
        val gui = gui(player.translateRaw("orbit.cosmetic.menu.title"), rows = 3) {
            categorySlots.forEach { (category, config) ->
                val (slot, material) = config
                slot(slot, itemStack(material) {
                    name(player.translateRaw(category.displayKey))
                }) { p -> openCosmeticList(p, category) }
            }
            fill(Material.GRAY_STAINED_GLASS_PANE)
        }
        player.openGui(gui)
    }

    fun openCosmeticList(player: Player, category: CosmeticCategory) {
        val playerData = CosmeticStore.load(player.uuid) ?: CosmeticPlayerData()
        val cosmetics = CosmeticRegistry.byCategory(category)

        val gui = paginatedGui(player.translateRaw(category.displayKey), rows = 6) {
            border(Material.GRAY_STAINED_GLASS_PANE)

            cosmetics.forEach { definition ->
                val owned = definition.id in playerData.owned
                val equipped = playerData.equipped[category.name] == definition.id
                val material = Material.fromKey(definition.material) ?: Material.BARRIER

                item(buildCosmeticItem(player, definition, material, owned, equipped)) { p ->
                    if (!owned) return@item
                    if (equipped) {
                        CosmeticStore.executeOnKey(p.uuid, EquipCosmeticProcessor(category.name, null))
                    } else {
                        CosmeticStore.executeOnKey(p.uuid, EquipCosmeticProcessor(category.name, definition.id))
                    }
                    openCosmeticList(p, category)
                }
            }

            staticSlot(49, itemStack(Material.ARROW) {
                name("<gray>Back")
            }) { p -> openCategoryMenu(p) }
        }
        gui.open(player)
    }

    private fun buildCosmeticItem(
        player: Player,
        definition: CosmeticDefinition,
        material: Material,
        owned: Boolean,
        equipped: Boolean,
    ) = itemStack(material) {
        val rarityName = player.translateRaw("orbit.cosmetic.rarity.${definition.rarity.name.lowercase()}")
        name("${definition.rarity.colorTag}${player.translateRaw(definition.nameKey)}")
        lore("${definition.rarity.colorTag}$rarityName")
        lore(player.translateRaw(definition.descriptionKey))
        lore("")
        when {
            equipped -> {
                lore("<green>${player.translateRaw("orbit.cosmetic.status.equipped")}")
                lore("<red>${player.translateRaw("orbit.cosmetic.action.unequip")}")
                glowing()
            }
            owned -> {
                lore("<green>${player.translateRaw("orbit.cosmetic.status.owned")}")
                lore("<yellow>${player.translateRaw("orbit.cosmetic.action.equip")}")
            }
            else -> {
                lore("<red>${player.translateRaw("orbit.cosmetic.status.locked")}")
            }
        }
    }
}
