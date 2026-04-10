package me.nebula.orbit.mode.game.battleroyale

import me.nebula.orbit.utils.mapvote.MapVoteManager
import me.nebula.orbit.utils.mapvote.VoteCategory
import me.nebula.orbit.utils.mapvote.VoteOption
import net.minestom.server.entity.Player
import java.util.UUID

object BattleRoyaleVoteManager {

    private val delegate = MapVoteManager(titleKey = "orbit.game.br.vote.title") {
        SeasonConfig.current.voteCategories.map { def ->
            VoteCategory(
                id = def.id,
                nameKey = def.nameKey,
                material = def.material,
                defaultIndex = def.defaultIndex,
                options = def.options.map {
                    VoteOption(
                        nameKey = it.nameKey,
                        material = it.material,
                        value = it.value,
                        mapIcon = it.mapIcon,
                        descriptionKey = it.descriptionKey,
                    )
                },
            )
        }
    }

    fun vote(player: UUID, categoryId: String, optionIndex: Int) =
        delegate.vote(player, categoryId, optionIndex)

    fun getVote(player: UUID, categoryId: String): Int? =
        delegate.getVote(player, categoryId)

    fun resolve(categoryId: String): Int = delegate.resolve(categoryId)

    fun resolveAndRecord(categoryId: String): Int {
        val index = delegate.resolve(categoryId)
        delegate.recordSelection(categoryId, index)
        return index
    }

    fun resolveValue(categoryId: String): Int = delegate.resolveValue(categoryId)

    fun resolveOptionName(player: Player, categoryId: String): String =
        delegate.resolveOptionName(player, categoryId)

    fun clear() = delegate.clear()

    fun openCategoryMenu(player: Player) = delegate.openCategoryMenu(player)

    fun openOptionMenu(player: Player, categoryId: String) =
        delegate.openOptionMenu(player, categoryId)
}
