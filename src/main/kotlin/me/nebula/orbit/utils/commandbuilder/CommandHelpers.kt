package me.nebula.orbit.utils.commandbuilder

import me.nebula.gravity.player.PlayerNamePredicate
import me.nebula.gravity.player.PlayerStore
import me.nebula.gravity.session.SessionStore
import net.minestom.server.MinecraftServer
import java.util.UUID

object OnlinePlayerCache {

    @Volatile
    var names: List<String> = emptyList()
        private set

    fun refresh() {
        names = SessionStore.all().map { it.playerName }
    }
}

fun suggestPlayers(prefix: String): List<String> =
    OnlinePlayerCache.names.filter { it.startsWith(prefix, ignoreCase = true) }

fun resolvePlayer(name: String): Pair<UUID, String>? {
    val online = MinecraftServer.getConnectionManager().findOnlinePlayer(name)
    if (online != null && online.username.equals(name, ignoreCase = true))
        return online.uuid to online.username
    val entry = PlayerStore.entries(PlayerNamePredicate(name)).firstOrNull() ?: return null
    return entry.key to entry.value.name
}
