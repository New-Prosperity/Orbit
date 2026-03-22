package me.nebula.orbit.mode.game.battleroyale

import me.nebula.gravity.battleroyale.AwardKitXpProcessor
import me.nebula.gravity.battleroyale.BattleRoyaleKitData
import me.nebula.gravity.battleroyale.BattleRoyaleKitStore
import me.nebula.gravity.battleroyale.SelectKitProcessor
import me.nebula.gravity.battleroyale.kitKey
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.itemresolver.ItemResolver
import me.nebula.orbit.utils.kit.Kit
import me.nebula.orbit.utils.kit.kit
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

object BattleRoyaleKitManager {

    private val season: Season get() = SeasonConfig.current
    private val kitDefinitions: List<KitDefinitionConfig> get() = season.kits

    private fun storeKey(player: Player): String = kitKey(season.id, player.uuid)

    fun openKitMenu(player: Player) {
        val data = BattleRoyaleKitStore.load(storeKey(player)) ?: BattleRoyaleKitData()
        val rows = ((kitDefinitions.size + 8) / 9).coerceIn(2, 6)

        gui(player.translateRaw("orbit.game.br.kit.menu_title"), rows) {
            border(Material.BLACK_STAINED_GLASS_PANE)

            kitDefinitions.forEachIndexed { index, def ->
                val slot = 10 + (index / 7) * 9 + (index % 7)
                if (slot >= rows * 9) return@forEachIndexed
                val item = buildKitItem(player, def, data)
                slot(slot, item) { p -> handleKitClick(p, def) }
            }
        }.open(player)
    }

    private fun buildKitItem(player: Player, def: KitDefinitionConfig, data: BattleRoyaleKitData): ItemStack {
        val isUnlocked = !def.locked || def.id in data.unlockedKits
        val isSelected = data.selectedKit == def.id
        val level = data.kitLevels[def.id] ?: if (isUnlocked) 1 else 0
        val xp = data.kitXp[def.id] ?: 0L
        val maxLevel = def.maxLevel
        val xpNeeded = if (level in 1 until maxLevel && def.xpPerLevel.size >= level) def.xpPerLevel[level - 1] else 0L
        val material = runCatching { ItemResolver.resolveMaterial(def.material) }.getOrNull() ?: Material.BARRIER

        return itemStack(if (isUnlocked) material else Material.GRAY_DYE) {
            name(player.translateRaw(def.nameKey))
            lore(player.translateRaw(def.descriptionKey))
            if (!isUnlocked) {
                emptyLoreLine()
                lore(player.translateRaw("orbit.game.br.kit.locked"))
            } else {
                emptyLoreLine()
                if (maxLevel > 1) {
                    lore(player.translateRaw("orbit.game.br.kit.level", "level" to level.toString(), "max" to maxLevel.toString()))
                    if (level < maxLevel && xpNeeded > 0) {
                        lore(player.translateRaw("orbit.game.br.kit.xp_progress", "xp" to xp.toString(), "needed" to xpNeeded.toString()))
                    }
                }
                emptyLoreLine()
                if (isSelected) {
                    lore(player.translateRaw("orbit.game.br.kit.selected"))
                    glowing()
                } else {
                    lore(player.translateRaw("orbit.game.br.kit.click_select"))
                }
            }
            clean()
        }
    }

    private fun handleKitClick(player: Player, def: KitDefinitionConfig) {
        val key = storeKey(player)
        val data = BattleRoyaleKitStore.load(key) ?: BattleRoyaleKitData()
        val isUnlocked = !def.locked || def.id in data.unlockedKits
        if (!isUnlocked) {
            player.sendMessage(player.translate("orbit.game.br.kit.locked"))
            return
        }
        if (data.selectedKit == def.id) {
            BattleRoyaleKitStore.executeOnKey(key, SelectKitProcessor(""))
            player.sendMessage(player.translate("orbit.game.br.kit.deselected", "kit" to player.translateRaw(def.nameKey)))
        } else {
            BattleRoyaleKitStore.executeOnKey(key, SelectKitProcessor(def.id))
            player.sendMessage(player.translate("orbit.game.br.kit.selected_msg", "kit" to player.translateRaw(def.nameKey)))
        }
        openKitMenu(player)
    }

    fun resolveKit(player: Player): Kit {
        val data = BattleRoyaleKitStore.load(storeKey(player)) ?: BattleRoyaleKitData()
        val kitId = data.selectedKit
        val def = kitDefinitions.firstOrNull { it.id == kitId }
        if (def == null) return buildStarterKit()

        val level = data.kitLevels[kitId] ?: 1
        val tierConfig = resolveTier(def, level)
        return buildKitFromTier(def.id, tierConfig)
    }

    private fun resolveTier(def: KitDefinitionConfig, level: Int): KitTierConfig {
        val sorted = def.tiers.keys.sorted()
        val effectiveLevel = sorted.filter { it <= level }.maxOrNull() ?: sorted.firstOrNull() ?: return KitTierConfig()
        return def.tiers[effectiveLevel] ?: KitTierConfig()
    }

    private fun buildKitFromTier(name: String, tier: KitTierConfig): Kit = kit("br-kit-$name") {
        tier.helmet?.let { helmet(it) }
        tier.chestplate?.let { chestplate(it) }
        tier.leggings?.let { leggings(it) }
        tier.boots?.let { boots(it) }
        tier.items.forEach { kitItem ->
            item(kitItem.slot, kitItem.material, kitItem.amount)
        }
    }

    private fun buildStarterKit(): Kit {
        val starter = season.starterKit
        return kit("br-kit-default") {
            starter.helmet?.let { helmet(it) }
            starter.chestplate?.let { chestplate(it) }
            starter.leggings?.let { leggings(it) }
            starter.boots?.let { boots(it) }
            starter.items.forEach { kitItem ->
                item(kitItem.slot, kitItem.material, kitItem.amount)
            }
        }
    }

    fun awardXp(player: Player, source: String) {
        val key = storeKey(player)
        val data = BattleRoyaleKitStore.load(key) ?: BattleRoyaleKitData()
        val kitId = data.selectedKit.ifEmpty { return }
        val def = kitDefinitions.firstOrNull { it.id == kitId } ?: return
        if (def.xpPerLevel.isEmpty()) return

        val amount = season.xpRewards[source] ?: return
        if (amount <= 0) return

        val result = BattleRoyaleKitStore.executeOnKey(
            key,
            AwardKitXpProcessor(kitId, amount, def.xpPerLevel),
        )

        if (result.leveledUp) {
            player.sendMessage(player.translate(
                "orbit.game.br.kit.level_up",
                "kit" to player.translateRaw(def.nameKey),
                "level" to result.newLevel.toString(),
            ))
        }
    }

    fun buildKitSelectorItem(player: Player): ItemStack =
        itemStack(Material.NETHER_STAR) {
            name(player.translateRaw("orbit.game.br.kit.selector"))
            glowing()
        }

}
