package me.nebula.orbit.cosmetic

import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.gravity.cosmetic.CosmeticDefinition
import me.nebula.gravity.cosmetic.CosmeticPlayerData
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.cosmetic.EquipCosmeticProcessor
import me.nebula.gravity.cosmetic.UnlockCosmeticProcessor
import me.nebula.gravity.economy.AddBalanceProcessor
import me.nebula.gravity.economy.EconomyStore
import me.nebula.gravity.economy.PurchaseCosmeticProcessor
import me.nebula.orbit.progression.mission.MissionTracker
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
        CosmeticCategory.COMPANION to Pair(20, Material.ARMOR_STAND),
        CosmeticCategory.PET to Pair(21, Material.BONE),
        CosmeticCategory.MOUNT to Pair(22, Material.SADDLE),
        CosmeticCategory.SPAWN_EFFECT to Pair(23, Material.ENDER_PEARL),
        CosmeticCategory.DEATH_EFFECT to Pair(24, Material.WITHER_SKELETON_SKULL),
        CosmeticCategory.AURA to Pair(29, Material.NETHER_STAR),
        CosmeticCategory.ELIMINATION_MESSAGE to Pair(30, Material.NAME_TAG),
        CosmeticCategory.JOIN_QUIT_MESSAGE to Pair(31, Material.OAK_SIGN),
        CosmeticCategory.GADGET to Pair(32, Material.BLAZE_ROD),
        CosmeticCategory.GRAVESTONE to Pair(33, Material.MOSSY_COBBLESTONE),
    )

    fun openCategoryMenu(player: Player) {
        val gui = gui(player.translateRaw("orbit.cosmetic.menu.title"), rows = 5) {
            categorySlots.forEach { (category, config) ->
                val (slot, material) = config
                slot(slot, itemStack(material) {
                    name(player.translateRaw(category.displayKey))
                    clean()
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
                val level = playerData.owned[definition.id] ?: 0
                val owned = level > 0
                val equipped = playerData.equipped[category.name] == definition.id
                val material = Material.fromKey(definition.material) ?: Material.BARRIER

                item(buildCosmeticItem(player, definition, material, owned, equipped, level)) { p ->
                    if (owned) {
                        if (equipped) {
                            CosmeticStore.executeOnKey(p.uuid, EquipCosmeticProcessor(category.name, null))
                        } else {
                            CosmeticStore.executeOnKey(p.uuid, EquipCosmeticProcessor(category.name, definition.id))
                            MissionTracker.onUseCategory(p, category.name)
                        }
                        CosmeticDataCache.invalidate(p.uuid)
                        openCosmeticList(p, category)
                    } else if (definition.price > 0) {
                        val cost = purchaseCost(definition, level)
                        openConfirmation(p, definition, category, cost)
                    }
                }
            }

            staticSlot(49, itemStack(Material.ARROW) {
                name("<gray>Back")
                    clean()
            }) { p -> openCategoryMenu(p) }
        }
        gui.open(player)
    }

    private fun openConfirmation(player: Player, definition: CosmeticDefinition, category: CosmeticCategory, cost: Int) {
        val material = Material.fromKey(definition.material) ?: Material.BARRIER

        val confirmGui = gui(player.translateRaw("orbit.cosmetic.confirm.title"), rows = 3) {
            slot(11, itemStack(Material.GREEN_WOOL) {
                name("<green>${player.translateRaw("orbit.cosmetic.confirm.accept")}")
                lore(player.translateRaw("orbit.cosmetic.confirm.cost", "price" to cost.toString()))
                    clean()
            }) { p ->
                val purchased = EconomyStore.executeOnKey(p.uuid, PurchaseCosmeticProcessor("coins", cost.toDouble()))
                if (!purchased) {
                    p.sendMessage(p.translate("orbit.cosmetic.insufficient_funds"))
                    openCosmeticList(p, category)
                    return@slot
                }
                val unlocked = CosmeticStore.executeOnKey(p.uuid, UnlockCosmeticProcessor(definition.id, definition.maxLevel))
                if (!unlocked) {
                    EconomyStore.executeOnKey(p.uuid, AddBalanceProcessor("coins", cost.toDouble()))
                    openCosmeticList(p, category)
                    return@slot
                }
                CosmeticDataCache.invalidate(p.uuid)
                p.sendMessage(p.translate("orbit.cosmetic.purchased", "cosmetic" to p.translateRaw(definition.nameKey)))
                openCosmeticList(p, category)
            }

            slot(13, itemStack(material) {
                name("${definition.rarity.colorTag}${player.translateRaw(definition.nameKey)}")
                lore(player.translateRaw("orbit.cosmetic.confirm.cost", "price" to cost.toString()))
                    clean()
            })

            slot(15, itemStack(Material.RED_WOOL) {
                name("<red>${player.translateRaw("orbit.cosmetic.confirm.cancel")}")
                    clean()
            }) { p -> openCosmeticList(p, category) }

            fill(Material.GRAY_STAINED_GLASS_PANE)
        }
        player.openGui(confirmGui)
    }

    private fun purchaseCost(definition: CosmeticDefinition, currentLevel: Int): Int =
        if (definition.maxLevel > 1) definition.price * (currentLevel + 1) else definition.price

    private fun buildCosmeticItem(
        player: Player,
        definition: CosmeticDefinition,
        material: Material,
        owned: Boolean,
        equipped: Boolean,
        level: Int,
    ) = itemStack(material) {
        val rarityName = player.translateRaw("orbit.cosmetic.rarity.${definition.rarity.name.lowercase()}")
        name("${definition.rarity.colorTag}${player.translateRaw(definition.nameKey)}")
        lore("${definition.rarity.colorTag}$rarityName")
        lore(player.translateRaw(definition.descriptionKey))
        if (definition.maxLevel > 1) {
            lore("")
            lore("<white>${player.translateRaw("orbit.cosmetic.level", "level" to "$level", "max" to "${definition.maxLevel}")}")
        }
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
                if (definition.price > 0) {
                    val cost = purchaseCost(definition, level)
                    lore("<gold>${player.translateRaw("orbit.cosmetic.price", "price" to cost.toString())}")
                    lore("<yellow>${player.translateRaw("orbit.cosmetic.action.purchase")}")
                } else {
                    lore("<red>${player.translateRaw("orbit.cosmetic.status.locked")}")
                }
            }
        }
    }
}
