package me.nebula.orbit.user

import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.gravity.user.NebulaUser
import me.nebula.orbit.translation.translate
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Instance

fun onlineUsers(): Sequence<OrbitOnlineUser> =
    MinecraftServer.getConnectionManager().onlinePlayers.asSequence().map { it.asNebulaUser() }

fun Instance.onlineUsers(): Sequence<OrbitOnlineUser> =
    players.asSequence().map { it.asNebulaUser() }

fun Sequence<NebulaUser>.broadcast(message: Component) {
    for (user in this) user.sendMessage(message)
}

fun Sequence<NebulaUser.Online>.broadcastTranslated(
    key: TranslationKey,
    vararg args: Pair<String, String>,
) {
    for (user in this) user.sendMessage(user.translate(key, *args))
}

fun broadcastAllTranslated(key: TranslationKey, vararg args: Pair<String, String>) {
    onlineUsers().broadcastTranslated(key, *args)
}
