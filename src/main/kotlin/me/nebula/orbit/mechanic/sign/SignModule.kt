package me.nebula.orbit.mechanic.sign

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import java.util.concurrent.ConcurrentHashMap

private data class SignKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class SignModule : OrbitModule("sign") {

    private val signTexts = ConcurrentHashMap<SignKey, Array<String>>()

    override fun onEnable() {
        super.onEnable()
        signTexts.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (!isSign(event.block)) return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = SignKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            signTexts[key] = arrayOf("", "", "", "")
        }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (!isSign(event.block)) return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = SignKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            val lines = signTexts[key] ?: arrayOf("", "", "", "")

            event.player.sendMessage(event.player.translate("orbit.mechanic.sign.header"))
            lines.forEachIndexed { i, line ->
                event.player.sendMessage(event.player.translate("orbit.mechanic.sign.line", "number" to "${i + 1}", "text" to line))
            }
        }
    }

    override fun onDisable() {
        signTexts.clear()
        super.onDisable()
    }

    private fun isSign(block: Block): Boolean =
        block.name().endsWith("_sign") || block.name().endsWith("_hanging_sign")
}
