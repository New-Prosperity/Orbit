package me.nebula.orbit.utils.commandbuilder

import me.nebula.gravity.player.playerNamePredicate
import me.nebula.gravity.player.PlayerStore
import me.nebula.gravity.session.SessionStore
import me.nebula.orbit.nick.NickManager
import me.nebula.orbit.utils.vanish.VanishManager
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import java.util.UUID

object OnlinePlayerCache {

    @Volatile
    var names: List<String> = emptyList()
        private set

    fun refresh() {
        names = SessionStore.all().map { it.playerName }
    }
}

fun suggestPlayers(prefix: String, viewer: Player? = null): List<String> =
    OnlinePlayerCache.names.mapNotNull { realName ->
        val player = MinecraftServer.getConnectionManager().findOnlinePlayer(realName) ?: return@mapNotNull null
        if (viewer != null && !VanishManager.canSee(viewer, player)) return@mapNotNull null
        if (NickManager.isNicked(player)) NickManager.displayName(player) else realName
    }.filter { it.startsWith(prefix, ignoreCase = true) }

fun resolvePlayer(name: String, viewer: Player? = null): Pair<UUID, String>? {
    val byNick = MinecraftServer.getConnectionManager().onlinePlayers
        .firstOrNull { NickManager.displayName(it).equals(name, ignoreCase = true)
            && (viewer == null || VanishManager.canSee(viewer, it)) }
    if (byNick != null) return byNick.uuid to NickManager.displayName(byNick)
    val online = MinecraftServer.getConnectionManager().findOnlinePlayer(name)
    if (online != null && online.username.equals(name, ignoreCase = true)
        && (viewer == null || VanishManager.canSee(viewer, online)))
        return online.uuid to online.username
    val entry = PlayerStore.entries(playerNamePredicate(name)).firstOrNull() ?: return null
    return entry.key to entry.value.name
}
