package me.nebula.orbit.utils.team

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.scoreboard.Team
import java.util.concurrent.ConcurrentHashMap

private val miniMessage = MiniMessage.miniMessage()

object TeamManager {

    private val teams = ConcurrentHashMap<String, Team>()
    private val teamManager get() = MinecraftServer.getTeamManager()

    fun create(name: String, block: TeamBuilder.() -> Unit = {}): Team {
        require(!teams.containsKey(name)) { "Team '$name' already exists" }
        val builder = TeamBuilder(name).apply(block)
        val team = teamManager.createBuilder(name)
            .teamDisplayName(builder.displayName ?: Component.text(name))
            .teamColor(builder.color)
            .prefix(builder.prefix ?: Component.empty())
            .suffix(builder.suffix ?: Component.empty())
            .build()
        teams[name] = team
        return team
    }

    fun get(name: String): Team? = teams[name]
    fun require(name: String): Team = requireNotNull(teams[name]) { "Team '$name' not found" }

    fun delete(name: String) {
        val team = teams.remove(name) ?: return
        team.members.toList().forEach { team.removeMember(it) }
        teamManager.deleteTeam(team)
    }

    fun all(): Map<String, Team> = teams.toMap()

    fun playerTeam(player: Player): Team? =
        teams.values.firstOrNull { player.username in it.members }
}

class TeamBuilder @PublishedApi internal constructor(private val name: String) {

    var displayName: Component? = null
    var color: NamedTextColor = NamedTextColor.WHITE
    var prefix: Component? = null
    var suffix: Component? = null

    fun displayName(text: String) { displayName = miniMessage.deserialize(text) }
    fun prefix(text: String) { prefix = miniMessage.deserialize(text) }
    fun suffix(text: String) { suffix = miniMessage.deserialize(text) }
}

fun Player.joinTeam(team: Team) = team.addMember(username)
fun Player.leaveTeam(team: Team) = team.removeMember(username)
