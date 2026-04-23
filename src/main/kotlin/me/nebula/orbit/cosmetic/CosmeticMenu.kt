package me.nebula.orbit.cosmetic

import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.gravity.cosmetic.CosmeticDefinition
import me.nebula.gravity.cosmetic.CosmeticPlayerData
import me.nebula.gravity.cosmetic.CosmeticRarity
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.cosmetic.EquipCosmeticProcessor
import me.nebula.gravity.cosmetic.UnlockCosmeticProcessor
import me.nebula.gravity.economy.AddBalanceProcessor
import me.nebula.gravity.economy.EconomyStore
import me.nebula.orbit.perks.EconomyPerks
import me.nebula.gravity.economy.PurchaseCosmeticProcessor
import me.nebula.orbit.progression.mission.MissionTracker
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.confirmGui
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import java.util.UUID
import me.nebula.gravity.translation.Keys
import me.nebula.ether.utils.translation.asTranslationKey

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
        val gui = gui(player.translateRaw(Keys.Orbit.Cosmetic.Menu.Title), rows = 5) {
            categorySlots.forEach { (category, config) ->
                val (slot, material) = config
                slot(slot, itemStack(material) {
                    name(player.translateRaw(category.displayKey))
                    clean()
                }) { p -> openCosmeticList(p, category) }
            }
            fillDefault()
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
                            despawnCategory(p.uuid, category, p)
                        } else {
                            despawnCategory(p.uuid, category, p)
                            CosmeticStore.executeOnKey(p.uuid, EquipCosmeticProcessor(category.name, definition.id))
                            CosmeticEvents.publishEquip(p, definition)
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

            backButton(49) { p -> openCategoryMenu(p) }
        }
        gui.open(player)
    }

    private fun openConfirmation(player: Player, definition: CosmeticDefinition, category: CosmeticCategory, cost: Int) {
        val material = Material.fromKey(definition.material) ?: Material.BARRIER

        val confirm = confirmGui(
            title = player.translateRaw(Keys.Orbit.Cosmetic.Confirm.Title),
            confirmItem = itemStack(Material.GREEN_WOOL) {
                name("<green>${player.translateRaw(Keys.Orbit.Cosmetic.Confirm.Accept)}")
                lore(player.translateRaw(Keys.Orbit.Cosmetic.Confirm.Cost, "price" to cost.toString()))
                clean()
            },
            cancelItem = itemStack(Material.RED_WOOL) {
                name("<red>${player.translateRaw(Keys.Orbit.Cosmetic.Confirm.Cancel)}")
                clean()
            },
            previewItem = itemStack(material) {
                name("${definition.rarity.colorTag}${player.translateRaw(definition.nameKey)}")
                lore(player.translateRaw(Keys.Orbit.Cosmetic.Confirm.Cost, "price" to cost.toString()))
                clean()
            },
            onConfirm = confirm@{ p ->
                val effectiveCost = EconomyPerks.costAfterCosmeticDiscount(p.uuid, cost.toDouble())
                val purchased = EconomyStore.executeOnKey(p.uuid, PurchaseCosmeticProcessor("coins", effectiveCost))
                if (!purchased) {
                    p.sendMessage(p.translate(Keys.Orbit.Cosmetic.InsufficientFunds))
                    openCosmeticList(p, category)
                    return@confirm
                }
                val unlocked = CosmeticStore.executeOnKey(p.uuid, UnlockCosmeticProcessor(definition.id, definition.maxLevel))
                if (!unlocked) {
                    EconomyStore.executeOnKey(p.uuid, AddBalanceProcessor("coins", effectiveCost))
                    openCosmeticList(p, category)
                    return@confirm
                }
                CosmeticDataCache.invalidate(p.uuid)
                CosmeticEvents.publishUnlock(p, definition)
                p.sendMessage(p.translate(Keys.Orbit.Cosmetic.Purchased, "cosmetic" to p.translateRaw(definition.nameKey)))
                openCosmeticList(p, category)
            },
            onCancel = { p -> openCosmeticList(p, category) },
        )
        player.openGui(confirm)
    }

    private fun purchaseCost(definition: CosmeticDefinition, currentLevel: Int): Int =
        if (definition.maxLevel > 1) definition.price * (currentLevel + 1) else definition.price

    fun despawnCategory(uuid: UUID, category: CosmeticCategory, player: Player) {
        val ctx = CosmeticListener.context
        when (category) {
            CosmeticCategory.PET -> ctx.pets.despawn(uuid)
            CosmeticCategory.COMPANION -> ctx.companions.despawn(uuid)
            CosmeticCategory.MOUNT -> ctx.mounts.despawn(uuid)
            CosmeticCategory.GADGET -> ctx.gadgets.unequip(player)
            else -> {}
        }
    }

    private fun buildCosmeticItem(
        player: Player,
        definition: CosmeticDefinition,
        material: Material,
        owned: Boolean,
        equipped: Boolean,
        level: Int,
    ) = itemStack(material) {
        val rarityName = player.translateRaw("orbit.cosmetic.rarity.${definition.rarity.name.lowercase()}".asTranslationKey())
        val rarityColor = definition.rarity.colorTag
        val isLegendary = definition.rarity == CosmeticRarity.LEGENDARY
        name("$rarityColor${player.translateRaw(definition.nameKey)}")
        lore("$rarityColor<bold>$rarityName</bold>")
        lore(player.translateRaw(definition.descriptionKey))
        if (definition.maxLevel > 1) {
            lore("")
            lore("<white>${player.translateRaw(Keys.Orbit.Cosmetic.Level, "level" to "$level", "max" to "${definition.maxLevel}")}")
        }
        lore("")
        when {
            equipped -> {
                lore("<green>${player.translateRaw(Keys.Orbit.Cosmetic.Status.Equipped)}")
                lore("<red>${player.translateRaw(Keys.Orbit.Cosmetic.Action.Unequip)}")
                glowing()
            }
            owned -> {
                lore("<green>${player.translateRaw(Keys.Orbit.Cosmetic.Status.Owned)}")
                lore("<yellow>${player.translateRaw(Keys.Orbit.Cosmetic.Action.Equip)}")
                if (isLegendary) glowing()
            }
            else -> {
                if (definition.price > 0) {
                    val cost = purchaseCost(definition, level)
                    lore("<gold>${player.translateRaw(Keys.Orbit.Cosmetic.Price, "price" to cost.toString())}")
                    lore("<yellow>${player.translateRaw(Keys.Orbit.Cosmetic.Action.Purchase)}")
                } else {
                    lore("<red>${player.translateRaw(Keys.Orbit.Cosmetic.Status.Locked)}")
                }
                if (isLegendary) glowing()
            }
        }
        clean()
    }
}
