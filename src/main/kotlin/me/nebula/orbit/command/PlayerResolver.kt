package me.nebula.orbit.command

import me.nebula.gravity.player.PlayerNamePredicate
import me.nebula.gravity.player.PlayerStore
import net.minestom.server.MinecraftServer
import java.util.UUID

fun resolvePlayer(name: String): Pair<UUID, String>? {
    val online = MinecraftServer.getConnectionManager().findOnlinePlayer(name)
    if (online != null && online.username.equals(name, ignoreCase = true))
        return online.uuid to online.username
    val entry = PlayerStore.entries(PlayerNamePredicate(name)).firstOrNull() ?: return null
    return entry.key to entry.value.name
}
