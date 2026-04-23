package me.nebula.orbit.loadout

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.loadout.BonusTier
import me.nebula.gravity.loadout.Loadout
import me.nebula.gravity.loadout.LoadoutBonusDefinition
import me.nebula.gravity.loadout.LoadoutCatalog
import me.nebula.gravity.loadout.LoadoutItemDefinition
import me.nebula.gravity.loadout.LoadoutPresetDefinition
import me.nebula.gravity.loadout.UnlockRequirement
import me.nebula.gravity.loadout.ValidationIssue
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

object LoadoutMenu {

    private const val TITLE_KEY = "orbit.loadout.menu.title"
    private const val ITEMS_TAB_KEY = "orbit.loadout.menu.items_tab"
    private const val BONUSES_TAB_KEY = "orbit.loadout.menu.bonuses_tab"
    private const val PRESETS_TAB_KEY = "orbit.loadout.menu.presets_tab"
    private const val SAVE_KEY = "orbit.loadout.menu.save"
    private const val DISCARD_KEY = "orbit.loadout.menu.discard"
    private const val CLOSE_KEY = "orbit.loadout.menu.close"
    private const val ITEM_BUDGET_KEY = "orbit.loadout.menu.item_budget"
    private const val BONUS_BUDGET_KEY = "orbit.loadout.menu.bonus_budget"
    private const val SLOT_ACTIVE_KEY = "orbit.loadout.menu.slot_active"
    private const val SLOT_SAVED_KEY = "orbit.loadout.menu.slot_saved"
    private const val SLOT_EMPTY_KEY = "orbit.loadout.menu.slot_empty"

    fun open(player: Player, modeId: String) {
        LoadoutSessionRegistry.open(player, modeId)
        openMain(player)
    }

    fun openMain(player: Player) {
        val session = LoadoutSessionRegistry.current(player.uuid) ?: return
        val validation = session.validate()
        val title = player.translateRaw(TITLE_KEY.asTranslationKey())

        val g = gui(title, rows = 6) {
            border(Material.GRAY_STAINED_GLASS_PANE)

            slot(4, itemBudgetStack(player, session.capacity.itemPoints, validation.itemPointsUsed))
            slot(8, bonusBudgetStack(player, session.capacity.bonusPoints, validation.bonusPointsUsed))

            val totalSlots = session.capacity.slotCount.coerceAtLeast(1)
            val prefs = me.nebula.gravity.loadout.LoadoutPreferenceManager.getPreferences(player.uuid, session.modeId)
            for (i in 0 until totalSlots) {
                val slotPos = 18 + i
                val isActive = i == session.slotIndex
                val isSaved = prefs.hasSlot(i)
                slot(slotPos, slotIndicator(player, i, isActive, isSaved)) { p ->
                    session.switchSlot(i)
                    openMain(p)
                }
            }

            slot(29, tabIcon(player, ITEMS_TAB_KEY, Material.DIAMOND_SWORD)) { p -> openItemsTab(p) }
            slot(31, tabIcon(player, BONUSES_TAB_KEY, Material.AMETHYST_SHARD)) { p -> openBonusesTab(p) }
            slot(33, tabIcon(player, PRESETS_TAB_KEY, Material.CHEST)) { p -> openPresetsTab(p) }

            slot(48, actionIcon(player, SAVE_KEY, Material.EMERALD_BLOCK)) { p -> saveAndClose(p) }
            slot(49, actionIcon(player, DISCARD_KEY, Material.REDSTONE_BLOCK)) { p -> discardAndClose(p) }
            slot(50, actionIcon(player, CLOSE_KEY, Material.BARRIER)) { p -> p.closeInventory() }
        }
        player.openGui(g)
    }

    fun openItemsTab(player: Player) {
        val session = LoadoutSessionRegistry.current(player.uuid) ?: return
        val items = LoadoutCatalog.itemsForMode(session.modeId)
        val conflicts = session.validate().conflictingEntryIds

        val g = paginatedGui(player.translateRaw(ITEMS_TAB_KEY.asTranslationKey()), rows = 6) {
            border(Material.GRAY_STAINED_GLASS_PANE)
            for (def in items) item(itemCatalogStack(player, session, def, conflicts)) { p ->
                handleItemClick(p, session, def)
            }
            backButton(49) { p -> openMain(p) }
        }
        g.open(player)
    }

    fun openBonusesTab(player: Player) {
        val session = LoadoutSessionRegistry.current(player.uuid) ?: return
        val bonuses = LoadoutCatalog.bonusesForMode(session.modeId)
            .sortedWith(compareBy({ it.tier.ordinal }, { it.id }))
        val conflicts = session.validate().conflictingEntryIds

        val g = paginatedGui(player.translateRaw(BONUSES_TAB_KEY.asTranslationKey()), rows = 6) {
            border(Material.GRAY_STAINED_GLASS_PANE)
            for (def in bonuses) item(bonusCatalogStack(player, session, def, conflicts)) { p ->
                handleBonusClick(p, session, def)
            }
            backButton(49) { p -> openMain(p) }
        }
        g.open(player)
    }

    fun openPresetsTab(player: Player) {
        val session = LoadoutSessionRegistry.current(player.uuid) ?: return
        val presets = LoadoutCatalog.presetsForMode(session.modeId)

        val g = paginatedGui(player.translateRaw(PRESETS_TAB_KEY.asTranslationKey()), rows = 6) {
            border(Material.GRAY_STAINED_GLASS_PANE)
            for (def in presets) item(presetCatalogStack(player, session, def)) { p ->
                handlePresetClick(p, session, def)
            }
            backButton(49) { p -> openMain(p) }
        }
        g.open(player)
    }

    private fun handleItemClick(player: Player, session: LoadoutEditSession, def: LoadoutItemDefinition) {
        if (!session.capacity.ownsItem(def.id)) return
        session.toggleItem(def.id)
        openItemsTab(player)
    }

    private fun handleBonusClick(player: Player, session: LoadoutEditSession, def: LoadoutBonusDefinition) {
        if (!session.capacity.ownsBonus(def.id)) return
        session.toggleBonus(def.id)
        openBonusesTab(player)
    }

    private fun handlePresetClick(player: Player, session: LoadoutEditSession, def: LoadoutPresetDefinition) {
        if (!session.capacity.ownsPreset(def.id)) return
        val cloned = Loadout(
            modeId = session.modeId,
            itemIds = def.itemIds,
            bonusIds = def.bonusIds,
            presetId = def.id,
        )
        session.replaceDraft(cloned)
        openMain(player)
    }

    private fun saveAndClose(player: Player) {
        val session = LoadoutSessionRegistry.current(player.uuid) ?: return
        val outcome = session.save()
        if (outcome.success) {
            player.playSound(net.kyori.adventure.sound.Sound.sound(
                SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP.key(),
                net.kyori.adventure.sound.Sound.Source.MASTER, 0.7f, 1.4f,
            ))
            LoadoutSessionRegistry.close(player.uuid)
            player.closeInventory()
        } else {
            player.playSound(net.kyori.adventure.sound.Sound.sound(
                SoundEvent.BLOCK_NOTE_BLOCK_BASS.key(),
                net.kyori.adventure.sound.Sound.Source.MASTER, 1.0f, 0.7f,
            ))
            player.sendMessage(player.translate(
                "orbit.loadout.menu.save_failed".asTranslationKey(),
                "reason" to (outcome.reason ?: "validation"),
            ))
        }
    }

    private fun discardAndClose(player: Player) {
        LoadoutSessionRegistry.close(player.uuid)
        player.closeInventory()
    }

    private fun itemBudgetStack(player: Player, budget: Int, used: Int): ItemStack {
        val over = used > budget
        val mat = if (over) Material.RED_CONCRETE else Material.LIME_CONCRETE
        return itemStack(mat) {
            name(player.translateRaw(ITEM_BUDGET_KEY.asTranslationKey(),
                "used" to used.toString(),
                "budget" to budget.toString(),
            ))
            clean()
        }
    }

    private fun bonusBudgetStack(player: Player, budget: Int, used: Int): ItemStack {
        val over = used > budget
        val mat = if (over) Material.RED_CONCRETE else Material.LIME_CONCRETE
        return itemStack(mat) {
            name(player.translateRaw(BONUS_BUDGET_KEY.asTranslationKey(),
                "used" to used.toString(),
                "budget" to budget.toString(),
            ))
            clean()
        }
    }

    private fun slotIndicator(player: Player, slot: Int, active: Boolean, saved: Boolean): ItemStack {
        val mat = when {
            active -> Material.EMERALD
            saved -> Material.GOLD_INGOT
            else -> Material.GRAY_DYE
        }
        val key = when {
            active -> SLOT_ACTIVE_KEY
            saved -> SLOT_SAVED_KEY
            else -> SLOT_EMPTY_KEY
        }
        return itemStack(mat) {
            name(player.translateRaw(key.asTranslationKey(), "slot" to (slot + 1).toString()))
            clean()
        }
    }

    private fun tabIcon(player: Player, key: String, material: Material): ItemStack = itemStack(material) {
        name(player.translateRaw(key.asTranslationKey()))
        clean()
    }

    private fun actionIcon(player: Player, key: String, material: Material): ItemStack = itemStack(material) {
        name(player.translateRaw(key.asTranslationKey()))
        clean()
    }

    private fun itemCatalogStack(
        player: Player,
        session: LoadoutEditSession,
        def: LoadoutItemDefinition,
        conflicts: Set<String>,
    ): ItemStack {
        val inDraft = def.id in session.draft.itemIds
        val locked = !session.capacity.ownsItem(def.id)
        val conflict = def.id in conflicts
        val material = Material.fromKey(def.material) ?: Material.BARRIER
        return itemStack(material) {
            name(nameLine(player, def.nameKey, locked, inDraft, conflict))
            lore(player.translateRaw(def.descriptionKey))
            lore(player.translateRaw("orbit.loadout.menu.cost".asTranslationKey(), "cost" to def.cost.toString()))
            if (locked) lore(unlockHint(player, def.unlock, session))
            if (inDraft && !conflict) lore(player.translateRaw("orbit.loadout.menu.selected".asTranslationKey()))
            if (conflict) lore(player.translateRaw("orbit.loadout.menu.conflict".asTranslationKey()))
            clean()
        }
    }

    private fun bonusCatalogStack(
        player: Player,
        session: LoadoutEditSession,
        def: LoadoutBonusDefinition,
        conflicts: Set<String>,
    ): ItemStack {
        val inDraft = def.id in session.draft.bonusIds
        val locked = !session.capacity.ownsBonus(def.id)
        val conflict = def.id in conflicts
        val material = Material.fromKey(def.material) ?: Material.BARRIER
        return itemStack(material) {
            name(nameLine(player, def.nameKey, locked, inDraft, conflict))
            lore(player.translateRaw(def.descriptionKey))
            lore(player.translateRaw("orbit.loadout.menu.tier.${def.tier.name.lowercase()}".asTranslationKey()))
            lore(player.translateRaw("orbit.loadout.menu.cost".asTranslationKey(), "cost" to def.cost.toString()))
            if (locked) lore(unlockHint(player, def.unlock, session))
            if (inDraft && !conflict) lore(player.translateRaw("orbit.loadout.menu.selected".asTranslationKey()))
            if (conflict) lore(player.translateRaw("orbit.loadout.menu.conflict".asTranslationKey()))
            clean()
        }
    }

    private fun presetCatalogStack(
        player: Player,
        session: LoadoutEditSession,
        def: LoadoutPresetDefinition,
    ): ItemStack {
        val locked = !session.capacity.ownsPreset(def.id)
        val material = Material.fromKey(def.material) ?: Material.BARRIER
        return itemStack(material) {
            name(if (locked) "<dark_gray>🔒 " else "" +
                "<gold>" + player.translateRaw(def.nameKey))
            lore(player.translateRaw(def.descriptionKey))
            lore(player.translateRaw("orbit.loadout.menu.preset_items".asTranslationKey(),
                "count" to def.itemIds.size.toString(),
            ))
            lore(player.translateRaw("orbit.loadout.menu.preset_bonuses".asTranslationKey(),
                "count" to def.bonusIds.size.toString(),
            ))
            if (locked) lore(unlockHint(player, def.unlock, session))
            clean()
        }
    }

    private fun nameLine(
        player: Player,
        nameKey: me.nebula.ether.utils.translation.TranslationKey,
        locked: Boolean,
        inDraft: Boolean,
        conflict: Boolean,
    ): String {
        val base = player.translateRaw(nameKey)
        return when {
            locked -> "<dark_gray>🔒 $base"
            conflict -> "<red>$base"
            inDraft -> "<green>✓ $base"
            else -> "<white>$base"
        }
    }

    private fun unlockHint(player: Player, requirement: UnlockRequirement, session: LoadoutEditSession): String {
        val visible = session.evaluator.previewVisible(player.uuid, requirement)
            ?: return player.translateRaw("orbit.loadout.menu.unlock.hidden".asTranslationKey())
        return when (visible) {
            UnlockRequirement.Free -> player.translateRaw("orbit.loadout.menu.unlock.free".asTranslationKey())
            is UnlockRequirement.Cosmetic -> player.translateRaw(
                "orbit.loadout.menu.unlock.cosmetic".asTranslationKey(),
                "id" to visible.cosmeticId,
            )
            is UnlockRequirement.BattlePassTier -> player.translateRaw(
                "orbit.loadout.menu.unlock.battlepass".asTranslationKey(),
                "tier" to visible.tier.toString(),
            )
            is UnlockRequirement.ModeLevel -> player.translateRaw(
                "orbit.loadout.menu.unlock.mode_level".asTranslationKey(),
                "mode" to visible.modeId,
                "level" to visible.level.toString(),
            )
            is UnlockRequirement.ModePrestige -> player.translateRaw(
                "orbit.loadout.menu.unlock.mode_prestige".asTranslationKey(),
                "mode" to visible.modeId,
                "prestige" to visible.prestige.toString(),
            )
            is UnlockRequirement.Challenge -> player.translateRaw(
                "orbit.loadout.menu.unlock.challenge".asTranslationKey(),
                "id" to visible.challengeId,
            )
            is UnlockRequirement.Composite -> player.translateRaw(
                "orbit.loadout.menu.unlock.composite".asTranslationKey(),
                "count" to visible.all.size.toString(),
            )
            is UnlockRequirement.Hidden -> player.translateRaw("orbit.loadout.menu.unlock.hidden".asTranslationKey())
        }
    }
}
