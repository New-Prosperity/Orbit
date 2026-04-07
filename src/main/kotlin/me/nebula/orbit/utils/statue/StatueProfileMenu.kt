package me.nebula.orbit.utils.statue

import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.gravity.cosmetic.CosmeticStore
import me.nebula.gravity.leveling.LevelStore
import me.nebula.gravity.player.PlayerStore
import me.nebula.gravity.rank.RankManager
import me.nebula.gravity.rating.EloCalculator
import me.nebula.gravity.rating.RatingStore
import me.nebula.gravity.stats.StatsStore
import me.nebula.orbit.cosmetic.CosmeticRegistry
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.item.Material
import java.util.UUID

object StatueProfileMenu {

    private val categoryMaterials = mapOf(
        CosmeticCategory.ARMOR_SKIN to Material.LEATHER_CHESTPLATE,
        CosmeticCategory.KILL_EFFECT to Material.REDSTONE,
        CosmeticCategory.TRAIL to Material.BLAZE_POWDER,
        CosmeticCategory.WIN_EFFECT to Material.FIREWORK_ROCKET,
        CosmeticCategory.PROJECTILE_TRAIL to Material.ARROW,
        CosmeticCategory.COMPANION to Material.ARMOR_STAND,
        CosmeticCategory.PET to Material.BONE,
        CosmeticCategory.MOUNT to Material.SADDLE,
        CosmeticCategory.SPAWN_EFFECT to Material.ENDER_PEARL,
        CosmeticCategory.DEATH_EFFECT to Material.WITHER_SKELETON_SKULL,
        CosmeticCategory.AURA to Material.NETHER_STAR,
        CosmeticCategory.ELIMINATION_MESSAGE to Material.NAME_TAG,
        CosmeticCategory.JOIN_QUIT_MESSAGE to Material.OAK_SIGN,
        CosmeticCategory.GADGET to Material.BLAZE_ROD,
        CosmeticCategory.GRAVESTONE to Material.MOSSY_COBBLESTONE,
    )

    fun open(viewer: Player, targetUuid: UUID, targetName: String) {
        Thread.startVirtualThread {
            val playerData = PlayerStore.load(targetUuid)
            val rankData = RankManager.rankOf(targetUuid)
            val cosmeticData = CosmeticStore.load(targetUuid)
            val levelData = LevelStore.load(targetUuid)
            val statsData = StatsStore.load(targetUuid)
            val ratingData = RatingStore.load(targetUuid)

            val title = viewer.translateRaw("orbit.statue.profile_title", "player" to targetName)
            val rankColor = rankData?.color ?: "white"
            val rankName = rankData?.name ?: "Member"
            val level = levelData?.level ?: 1

            val gui = gui(title, rows = 6) {
                slot(4, itemStack(Material.PLAYER_HEAD) {
                    name("<$rankColor><bold>$targetName")
                    lore("<gray>$rankName")
                    lore("<aqua>${viewer.translateRaw("orbit.statue.level_label", "level" to level.toString())}")
                    emptyLoreLine()

                    val totalWins = statsData?.stats?.values?.sumOf { it.wins } ?: 0
                    val totalKills = statsData?.stats?.values?.sumOf { it.kills } ?: 0
                    val totalGames = statsData?.stats?.values?.sumOf { it.gamesPlayed } ?: 0
                    lore("<gray>${viewer.translateRaw("orbit.statue.total_wins", "wins" to totalWins.toString())}")
                    lore("<gray>${viewer.translateRaw("orbit.statue.total_kills", "kills" to totalKills.toString())}")
                    lore("<gray>${viewer.translateRaw("orbit.statue.total_games", "games" to totalGames.toString())}")

                    if (totalGames > 0) {
                        val winRate = "%.1f".format((totalWins.toDouble() / totalGames) * 100)
                        lore("<gray>${viewer.translateRaw("orbit.statue.win_rate", "rate" to winRate)}")
                    }

                    val skinTextures = playerData?.let {
                        runCatching {
                            PlayerSkin.fromUuid(targetUuid.toString().replace("-", ""))
                        }.getOrNull()
                    }
                    skinTextures?.let { skull(it.textures()) }
                    clean()
                })

                val cosmeticSlots = listOf(19, 20, 21, 22, 23, 24, 25)
                val equippedCategories = cosmeticData?.equipped?.entries?.toList() ?: emptyList()
                equippedCategories.take(cosmeticSlots.size).forEachIndexed { index, (categoryName, cosmeticId) ->
                    val category = runCatching { CosmeticCategory.valueOf(categoryName) }.getOrNull() ?: return@forEachIndexed
                    val definition = CosmeticRegistry[cosmeticId] ?: return@forEachIndexed
                    val level = cosmeticData?.owned?.get(cosmeticId) ?: 1
                    val material = categoryMaterials[category] ?: Material.BARRIER
                    val rarityName = viewer.translateRaw("orbit.cosmetic.rarity.${definition.rarity.name.lowercase()}")

                    slot(cosmeticSlots[index], itemStack(material) {
                        name("${definition.rarity.colorTag}${viewer.translateRaw(definition.nameKey)}")
                        lore("<gray>${viewer.translateRaw(category.displayKey)}")
                        lore("${definition.rarity.colorTag}$rarityName")
                        if (definition.maxLevel > 1) {
                            lore("<white>${viewer.translateRaw("orbit.cosmetic.level", "level" to "$level", "max" to "${definition.maxLevel}")}")
                        }
                        glowing()
                        clean()
                    })
                }

                val ratingEntries = ratingData?.ratings?.entries?.toList() ?: emptyList()
                val ratingSlots = listOf(37, 38, 39, 40, 41, 42, 43)
                ratingEntries.take(ratingSlots.size).forEachIndexed { index, (gameMode, rating) ->
                    val tier = EloCalculator.tierOf(rating.rating)
                    slot(ratingSlots[index], itemStack(Material.IRON_SWORD) {
                        name("${tier.color}<bold>${viewer.translateRaw("orbit.statue.rating_gamemode", "gamemode" to gameMode)}")
                        lore("${tier.color}${tier.displayName} <gray>(${rating.rating})")
                        lore("<gray>${viewer.translateRaw("orbit.statue.peak_rating", "rating" to rating.peakRating.toString())}")
                        lore("<gray>${viewer.translateRaw("orbit.statue.games_played", "games" to rating.gamesPlayed.toString())}")
                        clean()
                    })
                }

                fillDefault()
                closeButton(49)
            }
            viewer.openGui(gui)
        }
    }
}
