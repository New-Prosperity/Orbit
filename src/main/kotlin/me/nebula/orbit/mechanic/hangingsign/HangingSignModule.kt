package me.nebula.orbit.mechanic.hangingsign

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block

private val HANGING_SIGN_BLOCKS = buildSet {
    val woods = listOf("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped")
    for (wood in woods) {
        add("minecraft:${wood}_hanging_sign")
        add("minecraft:${wood}_wall_hanging_sign")
    }
}

class HangingSignModule : OrbitModule("hanging-sign") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in HANGING_SIGN_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val above = instance.getBlock(pos.blockX(), pos.blockY() + 1, pos.blockZ())
            if (above.isAir) {
                event.isCancelled = true
            }
        }
    }
}
