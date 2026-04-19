package me.nebula.orbit.mode.game

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.notification.notify
import me.nebula.gravity.rating.ApplyRatingChangeProcessor
import me.nebula.gravity.rating.EloCalculator
import me.nebula.gravity.rating.GameModeRating
import me.nebula.gravity.rating.PLACEMENT_MATCHES_REQUIRED
import me.nebula.gravity.rating.RatingStore
import me.nebula.gravity.rating.RatingTier
import me.nebula.gravity.rating.isPlaced
import me.nebula.gravity.rating.placementsRemaining
import me.nebula.gravity.translation.Keys
import me.nebula.orbit.Orbit
import me.nebula.orbit.notification.ToastFrame
import me.nebula.orbit.notification.actionBar
import me.nebula.orbit.notification.title
import me.nebula.orbit.notification.toast
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.user.asNebulaUser
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
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
                    Keys.Orbit.Rating.PlacementProgress,
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
                Keys.Orbit.Rating.Change,
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
        val user = player.asNebulaUser()
        val tierName = "${tier.color}${tier.displayName}"
        notify(user) {
            chat(user.translate(Keys.Orbit.Rating.PlacementComplete, "tier" to tierName, "rating" to rating.toString()))
            title(
                user.translate(Keys.Orbit.Rating.PlacementRevealTitle),
                user.translate(Keys.Orbit.Rating.PlacementRevealSubtitle, "tier" to tierName, "rating" to rating.toString()),
                fadeInTicks = 8,
                stayTicks = 70,
                fadeOutTicks = 16,
            )
            toast(
                title = Component.text(
                    user.translateRaw(Keys.Orbit.Rating.PlacementComplete, "tier" to tierName, "rating" to rating.toString())
                ),
                icon = ItemStack.of(Material.NETHER_STAR),
                frame = ToastFrame.GOAL,
            )
            sound("minecraft:ui.toast.challenge_complete", pitch = 1.2f)
            sound("minecraft:entity.player.levelup")
            sound("minecraft:ui.toast.challenge_complete")
        }
    }

    internal fun notifyTierChange(player: Player, newTier: RatingTier, promoted: Boolean) {
        val user = player.asNebulaUser()
        val key = if (promoted) "orbit.rating.tier_up" else "orbit.rating.tier_down"
        val tierName = "${newTier.color}${newTier.displayName}"
        notify(user) {
            chat(user.translate(key.asTranslationKey(), "tier" to tierName))
            toast(
                title = Component.text(
                    user.translateRaw(key.asTranslationKey(), "tier" to tierName)
                ),
                icon = ItemStack.of(if (promoted) Material.DIAMOND else Material.REDSTONE),
                frame = if (promoted) ToastFrame.GOAL else ToastFrame.TASK,
            )
            sound(
                soundId = if (promoted) "minecraft:ui.toast.challenge_complete" else "minecraft:block.anvil.land",
                volume = 0.8f,
                pitch = if (promoted) 1.3f else 0.8f,
            )
            sound(
                soundId = if (promoted) "minecraft:entity.player.levelup" else "minecraft:entity.villager.no",
                volume = if (promoted) 1.0f else 0.6f,
                pitch = if (promoted) 1.0f else 0.9f,
            )
        }
    }
}
