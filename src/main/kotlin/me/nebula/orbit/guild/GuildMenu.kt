package me.nebula.orbit.guild

import me.nebula.gravity.guild.GuildData
import me.nebula.gravity.guild.GuildLevelFormula
import me.nebula.gravity.guild.GuildRole
import me.nebula.gravity.guild.GuildStore
import me.nebula.gravity.guild.SetGuildSettingsProcessor
import me.nebula.gravity.player.PlayerStore
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.gui.openGui
import me.nebula.orbit.utils.gui.paginatedGui
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import me.nebula.gravity.translation.Keys

object GuildMenu {

    fun openInfo(player: Player, guild: GuildData) {
        val color = GuildLevelFormula.tagColor(guild.level)
        val (progress, needed) = GuildLevelFormula.progressInLevel(guild.xp, guild.level)
        val cap = GuildLevelFormula.memberCap(guild.level)

        val gui = gui(player.translateRaw(Keys.Orbit.Guild.Info.Title, "guild" to guild.name), rows = 5) {
            fillDefault()

            slot(4, itemStack(Material.GOLDEN_HELMET) {
                name("<$color>[${guild.tag}] ${guild.name}")
                lore(player.translateRaw(Keys.Orbit.Guild.Info.Level, "level" to guild.level.toString()))
                if (guild.level < GuildLevelFormula.MAX_LEVEL) {
                    lore(player.translateRaw(Keys.Orbit.Guild.Info.Xp, "current" to progress.toString(), "needed" to needed.toString()))
                } else {
                    lore(player.translateRaw(Keys.Orbit.Guild.Info.MaxLevel))
                }
                lore(player.translateRaw(Keys.Orbit.Guild.Info.Members, "count" to guild.members.size.toString(), "max" to cap.toString()))
                clean()
            })

            val owner = guild.members.entries.firstOrNull { it.value == GuildRole.OWNER }
            if (owner != null) {
                val ownerName = PlayerStore.load(owner.key)?.name ?: "?"
                slot(20, itemStack(Material.DIAMOND) {
                    name("<gold>${player.translateRaw(Keys.Orbit.Guild.Info.Owner)}")
                    lore("<white>$ownerName")
                    clean()
                })
            }

            val officers = guild.members.filter { it.value == GuildRole.OFFICER }
            slot(22, itemStack(Material.IRON_INGOT) {
                name("<yellow>${player.translateRaw(Keys.Orbit.Guild.Info.Officers)}")
                for (officerId in officers.keys.take(5)) {
                    lore("<white>${PlayerStore.load(officerId)?.name ?: "?"}")
                }
                if (officers.size > 5) lore("<gray>+${officers.size - 5} more")
                clean()
            })

            slot(24, itemStack(Material.PAPER) {
                name("<green>${player.translateRaw(Keys.Orbit.Guild.Info.MemberCount)}")
                lore("<white>${guild.members.size} / $cap")
                clean()
            })

            backButton(36) { it.closeInventory() }
        }
        player.openGui(gui)
    }

    fun openSettings(player: Player, guildId: Long, guild: GuildData) {
        val settings = guild.settings

        val gui = gui(player.translateRaw(Keys.Orbit.Guild.Settings.Title), rows = 3) {
            fillDefault()

            slot(11, itemStack(if (settings.openInvite) Material.LIME_DYE else Material.GRAY_DYE) {
                name(player.translateRaw(Keys.Orbit.Guild.Settings.OpenInvite))
                lore(if (settings.openInvite) "<green>Enabled" else "<red>Disabled")
                clean()
            }) { p ->
                GuildStore.executeOnKey(guildId, SetGuildSettingsProcessor(settings.copy(openInvite = !settings.openInvite)))
                val updated = GuildStore.load(guildId) ?: return@slot
                openSettings(p, guildId, updated)
            }

            slot(15, itemStack(if (settings.friendlyFire) Material.LIME_DYE else Material.GRAY_DYE) {
                name(player.translateRaw(Keys.Orbit.Guild.Settings.FriendlyFire))
                lore(if (settings.friendlyFire) "<green>Enabled" else "<red>Disabled")
                clean()
            }) { p ->
                GuildStore.executeOnKey(guildId, SetGuildSettingsProcessor(settings.copy(friendlyFire = !settings.friendlyFire)))
                val updated = GuildStore.load(guildId) ?: return@slot
                openSettings(p, guildId, updated)
            }
        }
        player.openGui(gui)
    }

    fun openGuildList(player: Player) {
        val guilds = GuildStore.all().sortedByDescending { it.xp }.take(50)

        val gui = paginatedGui(player.translateRaw(Keys.Orbit.Guild.List.Title), rows = 6) {
            border(Material.GRAY_STAINED_GLASS_PANE)
            for (guild in guilds) {
                val color = GuildLevelFormula.tagColor(guild.level)
                item(itemStack(Material.SHIELD) {
                    name("<$color>[${guild.tag}] ${guild.name}")
                    lore(player.translateRaw(Keys.Orbit.Guild.Info.Level, "level" to guild.level.toString()))
                    lore(player.translateRaw(Keys.Orbit.Guild.Info.Members, "count" to guild.members.size.toString(), "max" to GuildLevelFormula.memberCap(guild.level).toString()))
                    clean()
                }) { p -> openInfo(p, guild) }
            }
        }
        gui.open(player)
    }
}
