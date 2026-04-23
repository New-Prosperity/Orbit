package me.nebula.orbit.loadout

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.achievement.AchievementStore
import me.nebula.gravity.battlepass.BattlePassStore
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.loadout.Loadout
import me.nebula.gravity.loadout.LoadoutCapacity
import me.nebula.gravity.loadout.LoadoutCapacityResolver
import me.nebula.gravity.loadout.LoadoutPreferenceManager
import me.nebula.gravity.loadout.LoadoutPreferenceStore
import me.nebula.gravity.loadout.LoadoutPreferences
import me.nebula.gravity.loadout.SaveSlotOutcome
import me.nebula.gravity.loadout.UnlockEvaluator
import me.nebula.gravity.loadout.ValidationResult
import me.nebula.gravity.loadout.LoadoutValidator
import me.nebula.gravity.progression.ModeProgress
import me.nebula.gravity.progression.ModeProgressStore
import net.minestom.server.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerUnlockEvaluator {

    fun forPlayer(): UnlockEvaluator = UnlockEvaluator(
        ownsCosmetic = { uuid, id ->
            CosmeticStore.load(uuid)?.owned?.containsKey(id) == true
        },
        battlePassTier = { uuid, passId ->
            BattlePassStore.load(uuid)?.passes?.get(passId)?.tier ?: 0
        },
        modeLevel = { uuid, modeId ->
            ModeProgressStore.load(ModeProgress.key(uuid, modeId))?.level ?: 1
        },
        modePrestige = { uuid, modeId ->
            ModeProgressStore.load(ModeProgress.key(uuid, modeId))?.prestige ?: 0
        },
        challengeCompleted = { uuid, challengeId ->
            AchievementStore.load(uuid)?.completed?.contains(challengeId) == true
        },
    )
}

class LoadoutEditSession(
    val player: Player,
    val modeId: String,
) {

    val uuid: UUID get() = player.uuid

    val evaluator: UnlockEvaluator = PlayerUnlockEvaluator.forPlayer()

    var slotIndex: Int = 0
        private set

    var draft: Loadout = Loadout(modeId)
        private set

    val capacity: LoadoutCapacity = LoadoutCapacityResolver.resolve(uuid, modeId, evaluator)

    init {
        val prefs = LoadoutPreferenceManager.getPreferences(uuid, modeId)
        slotIndex = prefs.activeSlot.coerceIn(0, LoadoutPreferences.MAX_SLOTS - 1)
        draft = prefs.slot(slotIndex) ?: Loadout(modeId)
    }

    fun switchSlot(newSlot: Int) {
        if (newSlot == slotIndex) return
        val prefs = LoadoutPreferenceManager.getPreferences(uuid, modeId)
        slotIndex = newSlot
        draft = prefs.slot(newSlot) ?: Loadout(modeId)
    }

    fun toggleItem(id: String) {
        draft = if (id in draft.itemIds) {
            draft.copy(itemIds = draft.itemIds - id)
        } else {
            draft.copy(itemIds = draft.itemIds + id, presetId = null)
        }
    }

    fun toggleBonus(id: String) {
        draft = if (id in draft.bonusIds) {
            draft.copy(bonusIds = draft.bonusIds - id)
        } else {
            draft.copy(bonusIds = draft.bonusIds + id, presetId = null)
        }
    }

    fun replaceDraft(loadout: Loadout) {
        require(loadout.modeId == modeId) { "replaceDraft modeId mismatch" }
        draft = loadout
    }

    fun validate(): ValidationResult = LoadoutValidator.validate(draft, capacity)

    fun save(): SaveSlotOutcome =
        LoadoutPreferenceManager.saveSlot(uuid, modeId, slotIndex, draft, capacity)
}

object LoadoutSessionRegistry {

    private val logger = logger("LoadoutSessionRegistry")
    private val sessions = ConcurrentHashMap<UUID, LoadoutEditSession>()

    fun open(player: Player, modeId: String): LoadoutEditSession {
        val existing = sessions[player.uuid]
        if (existing != null && existing.modeId == modeId) return existing
        val session = LoadoutEditSession(player, modeId)
        sessions[player.uuid] = session
        return session
    }

    fun current(uuid: UUID): LoadoutEditSession? = sessions[uuid]

    fun close(uuid: UUID) { sessions.remove(uuid) }

    fun closeAll() { sessions.clear() }
}
