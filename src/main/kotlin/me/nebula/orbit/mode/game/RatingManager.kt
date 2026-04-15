package me.nebula.orbit.mode.game

import me.nebula.gravity.rating.ApplyRatingChangeProcessor
import me.nebula.gravity.rating.EloCalculator
import me.nebula.gravity.rating.GameModeRating
import me.nebula.gravity.rating.PLACEMENT_MATCHES_REQUIRED
import me.nebula.gravity.rating.RatingStore
import me.nebula.gravity.rating.RatingTier
import me.nebula.gravity.rating.isPlaced
import me.nebula.gravity.rating.placementsRemaining
import me.nebula.orbit.Orbit
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.toast.ToastFrame
import me.nebula.orbit.utils.toast.showToast
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.time.Duration
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
            val wasPlaced = pd.gamesPlayed >= PLACEMENT_MATCHES_REQUIRED
            val isNowPlaced = updated.isPlaced()
            val tier = EloCalculator.tierOf(newRating)
            val oldTier = EloCalculator.tierOf(oldRating)
            val changeStr = if (delta >= 0) "<green>+$delta" else "<red>$delta"

            if (!isNowPlaced) {
                val provisional = "${tier.color}${tier.displayName}<gray>*"
                player.sendMessage(player.translate(
                    "orbit.rating.placement_progress",
                    "tier" to provisional,
                    "old" to oldRating.toString(),
                    "new" to newRating.toString(),
                    "change" to changeStr,
                    "placement" to pd.placement.toString(),
                    "lobby" to playerData.size.toString(),
                    "games" to updated.gamesPlayed.toString(),
                    "required" to PLACEMENT_MATCHES_REQUIRED.toString(),
                    "remaining" to updated.placementsRemaining().toString(),
                ))
                continue
            }

            if (!wasPlaced && isNowPlaced) {
                revealPlacement(player, tier, newRating)
                continue
            }

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
                notifyTierChange(player, tier, promoted = true)
            } else if (tier != oldTier && newRating < oldRating) {
                notifyTierChange(player, tier, promoted = false)
            }
        }
    }

    internal fun revealPlacement(player: Player, tier: RatingTier, rating: Int) {
        val tierName = "${tier.color}${tier.displayName}"
        player.sendMessage(player.translate(
            "orbit.rating.placement_complete",
            "tier" to tierName,
            "rating" to rating.toString(),
        ))
        player.showTitle(Title.title(
            player.translate("orbit.rating.placement_reveal_title"),
            player.translate("orbit.rating.placement_reveal_subtitle", "tier" to tierName, "rating" to rating.toString()),
            Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3500), Duration.ofMillis(800)),
        ))
        player.showToast {
            title(player.translateRaw("orbit.rating.placement_complete", "tier" to tierName, "rating" to rating.toString()))
            icon(Material.NETHER_STAR)
            frame(ToastFrame.GOAL)
            sound(SoundEvent.UI_TOAST_CHALLENGE_COMPLETE, volume = 1.0f, pitch = 1.2f)
        }
        player.playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP.key(), Sound.Source.PLAYER, 1.0f, 1.0f))
        player.playSound(Sound.sound(SoundEvent.UI_TOAST_CHALLENGE_COMPLETE.key(), Sound.Source.MASTER, 1.0f, 1.0f))
    }

    internal fun notifyTierChange(player: Player, newTier: RatingTier, promoted: Boolean) {
        val key = if (promoted) "orbit.rating.tier_up" else "orbit.rating.tier_down"
        val tierName = "${newTier.color}${newTier.displayName}"
        player.sendMessage(player.translate(key, "tier" to tierName))
        player.showToast {
            title(player.translateRaw(key, "tier" to tierName))
            icon(if (promoted) Material.DIAMOND else Material.REDSTONE)
            frame(if (promoted) ToastFrame.GOAL else ToastFrame.TASK)
            sound(
                if (promoted) SoundEvent.UI_TOAST_CHALLENGE_COMPLETE else SoundEvent.BLOCK_ANVIL_LAND,
                volume = 0.8f,
                pitch = if (promoted) 1.3f else 0.8f,
            )
        }
        val ambient = if (promoted) {
            Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP.key(), Sound.Source.PLAYER, 1.0f, 1.0f)
        } else {
            Sound.sound(SoundEvent.ENTITY_VILLAGER_NO.key(), Sound.Source.PLAYER, 0.6f, 0.9f)
        }
        player.playSound(ambient)
    }
}
