package me.nebula.orbit.mode.game

import me.nebula.gravity.rating.ApplyRatingChangeProcessor
import me.nebula.gravity.rating.EloCalculator
import me.nebula.gravity.rating.GameModeRating
import me.nebula.gravity.rating.RatingStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.translation.translate
import net.minestom.server.MinecraftServer
import java.util.UUID

class RatingManager(private val gameMode: GameMode) {

    fun applyRatingChanges() {
        val gameModeName = Orbit.gameMode ?: return
        val gameDurationTicks = ((System.currentTimeMillis() - gameMode.gameStartTime) / 50).coerceAtLeast(1)

        data class PlayerRatingInput(
            val uuid: UUID,
            val rating: Int,
            val placement: Int,
            val gamesPlayed: Int,
            val consecutiveGains: Int,
            val survivalTicks: Long,
        )

        val playerData = mutableListOf<PlayerRatingInput>()
        for (uuid in gameMode.tracker.all) {
            val placement = gameMode.placementsInternal[uuid] ?: gameMode.initialPlayerCount
            val ratingData = RatingStore.load(uuid)
            val gmRating = ratingData?.ratings?.get(gameModeName) ?: GameModeRating()
            val survivalTicks = gameDurationTicks * placement / gameMode.initialPlayerCount.coerceAtLeast(1)
            playerData.add(PlayerRatingInput(uuid, gmRating.rating, placement, gmRating.gamesPlayed, gmRating.consecutiveGains, survivalTicks))
        }

        if (playerData.size < 2) return

        val lobbyAvg = EloCalculator.lobbyAverage(playerData.map { it.rating })

        for (pd in playerData) {
            val opponents = playerData.filter { it.uuid != pd.uuid }.map { it.rating to it.placement }
            val delta = EloCalculator.calculateDelta(
                playerRating = pd.rating,
                playerPlacement = pd.placement,
                opponents = opponents,
                gamesPlayed = pd.gamesPlayed,
                consecutiveGains = pd.consecutiveGains,
                survivalTicks = pd.survivalTicks,
                totalGameTicks = gameDurationTicks,
            )

            val updated = RatingStore.executeOnKey(pd.uuid, ApplyRatingChangeProcessor(
                gameMode = gameModeName,
                change = delta,
                placement = pd.placement,
                lobbySize = playerData.size,
                lobbyAvgRating = lobbyAvg,
            ))

            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(pd.uuid) ?: continue
            val oldRating = pd.rating
            val newRating = updated.rating
            val changeStr = if (delta >= 0) "<green>+$delta" else "<red>$delta"
            val tier = EloCalculator.tierOf(newRating)
            val oldTier = EloCalculator.tierOf(oldRating)

            player.sendMessage(player.translate(
                "orbit.rating.change",
                "old" to oldRating.toString(),
                "new" to newRating.toString(),
                "change" to changeStr,
                "tier" to "${tier.color}${tier.displayName}",
                "placement" to pd.placement.toString(),
                "lobby" to playerData.size.toString(),
            ))

            if (tier != oldTier && newRating > oldRating) {
                player.sendMessage(player.translate(
                    "orbit.rating.tier_up",
                    "tier" to "${tier.color}${tier.displayName}",
                ))
            } else if (tier != oldTier && newRating < oldRating) {
                player.sendMessage(player.translate(
                    "orbit.rating.tier_down",
                    "tier" to "${tier.color}${tier.displayName}",
                ))
            }
        }
    }
}
