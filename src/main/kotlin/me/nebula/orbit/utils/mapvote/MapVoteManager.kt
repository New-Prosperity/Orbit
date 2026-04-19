package me.nebula.orbit.utils.mapvote

import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.itemresolver.ItemResolver
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import me.nebula.gravity.translation.Keys

data class VoteCategory(
    val id: String,
    val nameKey: TranslationKey,
    val material: String,
    val defaultIndex: Int = 0,
    val options: List<VoteOption>,
)

data class VoteOption(
    val nameKey: TranslationKey,
    val material: String,
    val value: Int = 0,
    val mapIcon: String? = null,
    val descriptionKey: TranslationKey? = null,
)

class MapVoteManager(
    private val titleKey: TranslationKey = "orbit.vote.title".asTranslationKey(),
    private val recentHistorySize: Int = 3,
    private val recentPenalty: Double = 0.4,
    private val categoriesProvider: () -> List<VoteCategory>,
) {

    private val votes = ConcurrentHashMap<String, ConcurrentHashMap<UUID, Int>>()
    private val recentSelections = ConcurrentHashMap<String, AtomicReference<List<Int>>>()

    fun vote(player: UUID, categoryId: String, optionIndex: Int) {
        votes.computeIfAbsent(categoryId) { ConcurrentHashMap() }[player] = optionIndex
    }

    fun getVote(player: UUID, categoryId: String): Int? =
        votes[categoryId]?.get(player)

    fun recordSelection(categoryId: String, optionIndex: Int) {
        val ref = recentSelections.computeIfAbsent(categoryId) { AtomicReference(emptyList()) }
        while (true) {
            val current = ref.get()
            val updated = (current + optionIndex).takeLast(recentHistorySize)
            if (ref.compareAndSet(current, updated)) return
        }
    }

    fun recentSelections(categoryId: String): List<Int> =
        recentSelections[categoryId]?.get() ?: emptyList()

    fun resolve(categoryId: String): Int {
        val cat = categoriesProvider().firstOrNull { it.id == categoryId } ?: return 0
        val categoryVotes = votes[categoryId]
        if (categoryVotes.isNullOrEmpty()) return cat.defaultIndex

        val recent = recentSelections(categoryId).toSet()
        val weightedTally = HashMap<Int, Double>()
        for ((_, optionIndex) in categoryVotes) {
            val weight = if (optionIndex in recent) recentPenalty else 1.0
            weightedTally.merge(optionIndex, weight, Double::plus)
        }
        return weightedTally.maxByOrNull { it.value }?.key ?: cat.defaultIndex
    }

    fun resolveValue(categoryId: String): Int {
        val cat = categoriesProvider().firstOrNull { it.id == categoryId } ?: return 0
        val index = resolve(categoryId)
        return cat.options.getOrNull(index)?.value ?: 0
    }

    fun resolveOptionName(player: Player, categoryId: String): String {
        val cat = categoriesProvider().firstOrNull { it.id == categoryId } ?: return "?"
        val index = resolve(categoryId)
        val option = cat.options.getOrNull(index) ?: return "?"
        return player.translateRaw(option.nameKey)
    }

    fun clear() = votes.clear()

    fun openCategoryMenu(player: Player) {
        val categories = categoriesProvider()
        gui(player.translateRaw(titleKey), rows = 3) {
            fillDefault()
            val totalWidth = (categories.size - 1).coerceAtLeast(0) * 2
            val startSlot = 9 + (9 - totalWidth) / 2
            categories.forEachIndexed { i, cat ->
                val slotIndex = startSlot + i * 2
                val material = ItemResolver.resolveMaterialOrNull(cat.material) ?: Material.PAPER
                slot(slotIndex, itemStack(material) {
                    name(player.translateRaw(cat.nameKey))
                    val current = getVote(player.uuid, cat.id)
                    if (current != null) {
                        val opt = cat.options.getOrNull(current)
                        opt?.let {
                            lore(player.translateRaw(Keys.Orbit.Vote.Current, "option" to player.translateRaw(it.nameKey)))
                        }
                    }
                    lore(player.translateRaw(Keys.Orbit.Vote.ClickToVote))
                    clean()
                }) { p ->
                    openOptionMenu(p, cat.id)
                }
            }
        }.open(player)
    }

    fun openOptionMenu(player: Player, categoryId: String) {
        val cat = categoriesProvider().firstOrNull { it.id == categoryId } ?: return
        gui(player.translateRaw(cat.nameKey), rows = 3) {
            fillDefault()
            val totalWidth = (cat.options.size - 1).coerceAtLeast(0) * 2
            val startSlot = 9 + (9 - totalWidth) / 2
            cat.options.forEachIndexed { i, option ->
                val slotIndex = startSlot + i * 2
                val currentVote = getVote(player.uuid, cat.id)
                val isSelected = currentVote == i
                val material = ItemResolver.resolveMaterialOrNull(option.material) ?: Material.PAPER
                slot(slotIndex, itemStack(material) {
                    name(player.translateRaw(option.nameKey))
                    option.mapIcon?.let { itemModel("sprites/$it") }
                    option.descriptionKey?.let { lore(player.translateRaw(it)) }
                    if (isSelected) {
                        lore(player.translateRaw(Keys.Orbit.Vote.Selected))
                        glowing()
                    } else {
                        lore(player.translateRaw(Keys.Orbit.Vote.ClickToSelect))
                    }
                    clean()
                }) { p ->
                    vote(p.uuid, cat.id, i)
                    p.sendMessage(p.translate(Keys.Orbit.Vote.Voted,
                        "category" to p.translateRaw(cat.nameKey),
                        "option" to p.translateRaw(option.nameKey),
                    ))
                    openOptionMenu(p, cat.id)
                }
            }
            slot(22, itemStack(Material.ARROW) {
                name(player.translateRaw(Keys.Orbit.Host.Back))
                clean()
            }) { p ->
                openCategoryMenu(p)
            }
        }.open(player)
    }
}
