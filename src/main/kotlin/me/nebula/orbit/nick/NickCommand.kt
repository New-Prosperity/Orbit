package me.nebula.orbit.nick

import com.google.gson.JsonParser
import com.hazelcast.query.Predicates
import me.nebula.gravity.nick.NickData
import me.nebula.gravity.nick.NickPoolManager
import me.nebula.gravity.nick.NickPoolStore
import me.nebula.gravity.nick.NickStore
import me.nebula.orbit.utils.commandbuilder.command
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun nickCommands(): List<Command> = listOf(
    nickCommand(),
    unnickCommand(),
    realNickCommand(),
    nickPoolCommand(),
    forceNickCommand(),
)

private fun nickCommand(): Command = command("nick") {
    permission("nebula.nick")
    wordArgument("name")

    onPlayerExecute {
        val name = argOrNull("name")
        if (name == null) {
            if (NickManager.isNicked(player)) {
                reply("orbit.nick.already_nicked")
                return@onPlayerExecute
            }
            val entry = NickPoolManager.claimRandom()
            if (entry == null) {
                reply("orbit.nick.pool_empty")
                return@onPlayerExecute
            }
            val nickData = NickData(entry.name, entry.skinTextures, entry.skinSignature, entry.identity)
            NickStore.save(player.uuid, nickData)
            NickManager.applyNick(player, nickData)
            reply("orbit.nick.applied", "name" to entry.name)
            return@onPlayerExecute
        }

        if (NickManager.isNicked(player)) {
            reply("orbit.nick.already_nicked")
            return@onPlayerExecute
        }

        if (!NickPoolManager.validateName(name)) {
            reply("orbit.nick.invalid_name")
            return@onPlayerExecute
        }

        val existingNick = NickStore.query(Predicates.equal("nickName", name))
        if (existingNick.isNotEmpty()) {
            reply("orbit.nick.name_taken")
            return@onPlayerExecute
        }

        val entry = NickPoolManager.claimRandom()
        if (entry == null) {
            reply("orbit.nick.pool_empty")
            return@onPlayerExecute
        }

        val nickData = NickData(name, entry.skinTextures, entry.skinSignature, entry.identity)
        NickStore.save(player.uuid, nickData)
        NickManager.applyNick(player, nickData)
        reply("orbit.nick.applied", "name" to name)
    }
}

private fun unnickCommand(): Command = command("unnick") {
    permission("nebula.nick")

    onPlayerExecute {
        if (!NickManager.isNicked(player)) {
            reply("orbit.nick.not_nicked")
            return@onPlayerExecute
        }
        val nickData = NickStore.load(player.uuid)
        if (nickData != null) {
            if (NickPoolStore.load(nickData.nickName) != null) {
                NickPoolManager.release(nickData.nickName)
            }
        }
        NickStore.delete(player.uuid)
        NickManager.removeNick(player)
        reply("orbit.nick.removed")
    }
}

private fun realNickCommand(): Command = command("realnick") {
    permission("nebula.nick.reveal")
    playerArgument("player")

    onPlayerExecute {
        val name = argOrNull("player") ?: return@onPlayerExecute
        val target = MinecraftServer.getConnectionManager().onlinePlayers
            .firstOrNull { NickManager.displayName(it).equals(name, ignoreCase = true) }
        if (target == null) {
            reply("orbit.command.player_not_found", "name" to name)
            return@onPlayerExecute
        }
        if (!NickManager.isNicked(target)) {
            reply("orbit.nick.not_nicked_target")
            return@onPlayerExecute
        }
        reply("orbit.nick.reveal", "nick" to NickManager.displayName(target), "real" to target.username)
    }
}

private fun forceNickCommand(): Command = command("forcenick") {
    permission("nebula.nick.admin")
    playerArgument("player")

    onPlayerExecute {
        val target = targetPlayer() ?: run {
            reply("orbit.command.player_not_found", "name" to (argOrNull("player") ?: ""))
            return@onPlayerExecute
        }
        if (!NickManager.isNicked(target)) {
            reply("orbit.nick.not_nicked_target")
            return@onPlayerExecute
        }
        val nickData = NickStore.load(target.uuid)
        if (nickData != null) {
            if (NickPoolStore.load(nickData.nickName) != null) {
                NickPoolManager.release(nickData.nickName)
            }
        }
        NickStore.delete(target.uuid)
        NickManager.removeNick(target)
        reply("orbit.nick.force_removed", "player" to target.username)
    }
}

private fun fetchMojangSkin(username: String): Pair<String, String> {
    val client = HttpClient.newHttpClient()

    val profileRequest = HttpRequest.newBuilder()
        .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/$username"))
        .GET()
        .build()
    val profileResponse = client.send(profileRequest, HttpResponse.BodyHandlers.ofString())
    require(profileResponse.statusCode() == 200) { "Mojang profile lookup failed for $username" }

    val profileJson = JsonParser.parseString(profileResponse.body()).asJsonObject
    val id = profileJson.get("id").asString

    val sessionRequest = HttpRequest.newBuilder()
        .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/$id?unsigned=false"))
        .GET()
        .build()
    val sessionResponse = client.send(sessionRequest, HttpResponse.BodyHandlers.ofString())
    require(sessionResponse.statusCode() == 200) { "Mojang session lookup failed for $username" }

    val sessionJson = JsonParser.parseString(sessionResponse.body()).asJsonObject
    val properties = sessionJson.getAsJsonArray("properties")
    val texturesProperty = properties.first { it.asJsonObject.get("name").asString == "textures" }.asJsonObject
    return texturesProperty.get("value").asString to texturesProperty.get("signature").asString
}

private fun nickPoolCommand(): Command = command("nickpool") {
    permission("nebula.nick.admin")

    subCommand("add") {
        wordArgument("username")

        onPlayerExecute {
            val username = requireArg("username") ?: return@onPlayerExecute

            val (skinTextures, skinSignature) = runCatching { fetchMojangSkin(username) }.getOrNull() ?: run {
                reply("orbit.nick.fetch_failed")
                return@onPlayerExecute
            }

            if (!NickPoolManager.validateName(username)) {
                reply("orbit.nick.invalid_name")
                return@onPlayerExecute
            }

            NickPoolManager.addToPool(username, skinTextures, skinSignature)
            reply("orbit.nick.pool_added", "name" to username)
        }
    }

    subCommand("remove") {
        wordArgument("name")

        onPlayerExecute {
            val name = requireArg("name") ?: return@onPlayerExecute
            val entry = NickPoolStore.load(name)
            if (entry == null) {
                reply("orbit.nick.pool_not_found", "name" to name)
                return@onPlayerExecute
            }
            NickPoolManager.removeFromPool(name)
            reply("orbit.nick.pool_removed", "name" to name)
        }
    }

    subCommand("list") {
        onPlayerExecute {
            val entries = NickPoolStore.all()
            if (entries.isEmpty()) {
                reply("orbit.nick.pool_empty")
                return@onPlayerExecute
            }
            replyMM("<gray>--- Nick Pool ---")
            for (entry in entries) {
                val status = if (entry.inUse) "<red>in-use" else "<green>available"
                replyMM("<gray>${entry.name}: $status")
            }
        }
    }

    subCommand("count") {
        onPlayerExecute {
            val total = NickPoolManager.poolSize()
            val available = NickPoolManager.availableCount()
            replyMM("<gray>Pool: <white>$available<gray>/<white>$total <gray>available")
        }
    }
}
