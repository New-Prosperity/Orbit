package me.nebula.orbit.progression.achievement

import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.achievement.AchievementCategories
import me.nebula.orbit.utils.achievement.AchievementCategory
import me.nebula.orbit.utils.achievement.AchievementRegistry
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

    fun open(player: Player) {
        val categoryGui = gui(player.translateRaw("orbit.achievement.title"), rows = 4) {
            for ((category, slot, material) in categoryLayout) {
                val completed = AchievementRegistry.completedInCategory(player.uuid, category)
                val total = AchievementRegistry.totalInCategory(category)

                slot(slot, itemStack(material) {
                    name(player.translateRaw(category.displayKey))
                    lore(player.translateRaw("orbit.achievement.progress",
                        "completed" to completed.toString(),
                        "total" to total.toString(),
                    ))
                    clean()
                }) { p -> openCategory(p, category) }
            }
            fill(Material.GRAY_STAINED_GLASS_PANE)
        }
        player.openGui(categoryGui)
    }

    private fun openCategory(player: Player, category: AchievementCategory) {
        val achievements = AchievementRegistry.byCategory(category)

        val listGui = paginatedGui(player.translateRaw(category.displayKey), rows = 6) {
            border(Material.GRAY_STAINED_GLASS_PANE)

            for (achievement in achievements) {
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
                    }
                    if (completed) glowing()
                    clean()
                })
            }

            staticSlot(49, itemStack(Material.ARROW) {
                name("<gray>${player.translateRaw("orbit.achievement.back")}")
                    clean()
            }) { p -> open(p) }
        }
        listGui.open(player)
    }
}
