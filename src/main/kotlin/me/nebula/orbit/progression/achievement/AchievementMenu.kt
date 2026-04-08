package me.nebula.orbit.progression.achievement

import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.achievement.AchievementCategories
import me.nebula.orbit.utils.achievement.AchievementCategory
import me.nebula.orbit.utils.achievement.AchievementRarity
import me.nebula.orbit.utils.achievement.AchievementRegistry
import me.nebula.orbit.utils.achievement.progressBar
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material

object AchievementMenu {

    private val categoryLayout = listOf(
        Triple(AchievementCategories.GENERAL, 10, Material.BOOK),
        Triple(AchievementCategories.COMBAT, 11, Material.IRON_SWORD),
        Triple(AchievementCategories.SURVIVAL, 12, Material.SHIELD),
        Triple(AchievementCategories.SOCIAL, 13, Material.PLAYER_HEAD),
        Triple(AchievementCategories.EXPLORATION, 14, Material.COMPASS),
        Triple(AchievementCategories.MASTERY, 15, Material.NETHER_STAR),
    )

    private val tierIcons = mapOf(
        1 to "<#cd7f32>",
        2 to "<gray>",
        3 to "<gold>",
        4 to "<light_purple>",
    )

    fun open(player: Player) {
        val totalPoints = AchievementRegistry.points(player)
        val totalCompleted = AchievementRegistry.completedCount(player)
        val totalAchievements = AchievementRegistry.totalCount()

        val categoryGui = gui(player.translateRaw("orbit.achievement.title"), rows = 5) {
            slot(4, itemStack(Material.DIAMOND) {
                name(player.translateRaw("orbit.achievement.summary_title"))
                lore(player.translateRaw("orbit.achievement.summary_points", "points" to totalPoints.toString()))
                lore(player.translateRaw("orbit.achievement.progress",
                    "completed" to totalCompleted.toString(),
                    "total" to totalAchievements.toString(),
                ))
                clean()
            })

            for ((category, slot, material) in categoryLayout) {
                val completed = AchievementRegistry.completedInCategory(player.uuid, category)
                val total = AchievementRegistry.totalInCategory(category)
                val catPoints = AchievementRegistry.pointsInCategory(player.uuid, category)

                slot(slot + 9, itemStack(material) {
                    name(player.translateRaw(category.displayKey))
                    lore(player.translateRaw("orbit.achievement.progress",
                        "completed" to completed.toString(),
                        "total" to total.toString(),
                    ))
                    lore(player.translateRaw("orbit.achievement.category_points", "points" to catPoints.toString()))
                    clean()
                }) { p -> openCategory(p, category) }
            }

            slot(25, itemStack(Material.GOLD_INGOT) {
                name(player.translateRaw("orbit.achievement.milestones_title"))
                for (milestone in AchievementRegistry.MILESTONES) {
                    val claimed = milestone.threshold in AchievementRegistry.claimedMilestones(player.uuid)
                    val milestoneName = player.translateRaw(milestone.nameKey)
                    val prefix = if (claimed) "<green><strikethrough>" else if (totalPoints >= milestone.threshold) "<yellow>" else "<gray>"
                    lore("$prefix$milestoneName <dark_gray>(${milestone.threshold} pts)")
                }
                clean()
            })

            fillDefault()
        }
        player.openGui(categoryGui)
    }

    private fun openCategory(player: Player, category: AchievementCategory) {
        val achievements = AchievementRegistry.byCategory(category)

        val tierGroups = achievements
            .mapNotNull { ach -> ach.tierGroup?.let { it to ach } }
            .groupBy({ it.first }, { it.second })
        val standalone = achievements.filter { it.tierGroup == null }
        val processed = mutableSetOf<String>()

        val listGui = paginatedGui(player.translateRaw(category.displayKey), rows = 6) {
            border(Material.GRAY_STAINED_GLASS_PANE)

            for (achievement in achievements) {
                if (achievement.id in processed) continue

                val group = achievement.tierGroup
                if (group != null && group !in processed.map { tierGroups.entries.find { e -> e.value.any { a -> a.id == it } }?.key }) {
                    val members = tierGroups[group] ?: listOf(achievement)
                    for (member in members) {
                        processed.add(member.id)
                    }

                    val highestCompleted = members.lastOrNull { AchievementRegistry.isCompleted(player, it.id) }
                    val nextTier = members.firstOrNull { !AchievementRegistry.isCompleted(player, it.id) } ?: members.last()
                    val displayAch = highestCompleted ?: nextTier

                    if (displayAch.hidden && highestCompleted == null) {
                        item(itemStack(Material.COAL_BLOCK) {
                            name("<dark_gray>???")
                            lore(player.translateRaw("orbit.achievement.hidden"))
                            clean()
                        })
                        continue
                    }

                    val allCompleted = members.all { AchievementRegistry.isCompleted(player, it.id) }
                    val material = if (allCompleted) Material.LIME_DYE else displayAch.icon

                    item(itemStack(material) {
                        val achName = player.translateRaw("orbit.achievement.${nextTier.id}.name")
                        name(if (allCompleted) "<green>$achName" else "<white>$achName")

                        for (member in members) {
                            val completed = AchievementRegistry.isCompleted(player, member.id)
                            val tierColor = tierIcons[member.tierLevel] ?: "<white>"
                            val memberName = player.translateRaw("orbit.achievement.${member.id}.name")
                            val status = if (completed) "<green>✔" else "<red>✘"
                            lore("$tierColor $memberName $status")
                        }

                        val progress = AchievementRegistry.getProgress(player, nextTier.id)
                        if (!allCompleted && nextTier.maxProgress > 1) {
                            lore("")
                            lore(player.translateRaw("orbit.achievement.progress",
                                "completed" to progress.toString(),
                                "total" to nextTier.maxProgress.toString(),
                            ))
                            lore("<gray>${progressBar(progress, nextTier.maxProgress, 15)}")
                        }
                        lore("")
                        lore("${nextTier.rarity.colorTag}${player.translateRaw(nextTier.rarity.labelKey)}")
                        lore(player.translateRaw("orbit.achievement.points_value", "points" to nextTier.points.toString()))
                        if (allCompleted) glowing()
                        clean()
                    })
                } else if (group == null) {
                    processed.add(achievement.id)
                    val completed = AchievementRegistry.isCompleted(player, achievement.id)
                    val progress = AchievementRegistry.getProgress(player, achievement.id)

                    if (achievement.hidden && !completed) {
                        item(itemStack(Material.COAL_BLOCK) {
                            name("<dark_gray>???")
                            lore(player.translateRaw("orbit.achievement.hidden"))
                            clean()
                        })
                        continue
                    }

                    val material = if (completed) Material.LIME_DYE else achievement.icon

                    item(itemStack(material) {
                        val achName = player.translateRaw("orbit.achievement.${achievement.id}.name")
                        val achDesc = player.translateRaw("orbit.achievement.${achievement.id}.description")
                        name(if (completed) "<green>$achName" else "<white>$achName")
                        lore("<gray>$achDesc")
                        if (achievement.maxProgress > 1) {
                            lore(player.translateRaw("orbit.achievement.progress",
                                "completed" to progress.toString(),
                                "total" to achievement.maxProgress.toString(),
                            ))
                            lore("<gray>${progressBar(progress, achievement.maxProgress, 15)}")
                        }
                        lore("")
                        lore("${achievement.rarity.colorTag}${player.translateRaw(achievement.rarity.labelKey)}")
                        lore(player.translateRaw("orbit.achievement.points_value", "points" to achievement.points.toString()))
                        if (completed) glowing()
                        clean()
                    })
                }
            }

            staticSlot(49, itemStack(Material.ARROW) {
                name("<gray>${player.translateRaw("orbit.achievement.back")}")
                clean()
            }) { p -> open(p) }
        }
        listGui.open(player)
    }
}
