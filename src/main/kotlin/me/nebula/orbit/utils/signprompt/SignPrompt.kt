package me.nebula.orbit.utils.signprompt

import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

typealias SignCallback = (Player, List<String>) -> Unit

object SignPromptManager {

    private val callbacks = ConcurrentHashMap<UUID, SignCallback>()
    private val promptTag = Tag.Boolean("orbit:sign_prompt:active")

    fun prompt(player: Player, lines: List<String> = listOf("", "", "", ""), callback: SignCallback) {
        callbacks[player.uuid] = callback
        player.setTag(promptTag, true)

        val instance = player.instance ?: return
        val pos = Vec(player.position.x(), player.position.y() - 5.0, player.position.z())
        val sign = Block.fromKey("minecraft:oak_sign") ?: return

        instance.setBlock(pos, sign)
    }

    fun handleResponse(player: Player, lines: List<String>) {
        val callback = callbacks.remove(player.uuid) ?: return
        player.removeTag(promptTag)
        callback(player, lines)
    }

    fun isPrompted(player: Player): Boolean =
        player.getTag(promptTag) ?: false

    fun cancel(player: Player) {
        callbacks.remove(player.uuid)
        player.removeTag(promptTag)
    }
}

fun Player.openSignPrompt(
    lines: List<String> = listOf("", "", "", ""),
    callback: SignCallback,
) = SignPromptManager.prompt(this, lines, callback)
