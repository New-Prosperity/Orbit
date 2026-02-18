package me.nebula.orbit.mechanic.bed

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag

private val SLEEPING_TAG = Tag.Boolean("mechanic:bed:sleeping").defaultValue(false)

class BedModule : OrbitModule("bed") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            if (!isBed(block)) return@addListener
            if (event.player.gameMode == GameMode.SPECTATOR) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            event.player.respawnPoint = Pos(
                pos.x() + 0.5,
                pos.y() + 0.5625,
                pos.z() + 0.5,
            )

            val time = instance.time % 24000
            if (time in 12542..23459) {
                event.player.setTag(SLEEPING_TAG, true)
                trySleepSkip(instance)
            }
        }
    }

    private fun isBed(block: Block): Boolean =
        block.name().endsWith("_bed")

    private fun trySleepSkip(instance: Instance) {
        val eligible = instance.players.filter { it.gameMode != GameMode.SPECTATOR }
        if (eligible.isEmpty()) return

        val sleeping = eligible.count { it.getTag(SLEEPING_TAG) }
        val threshold = (eligible.size * 0.5).toInt().coerceAtLeast(1)

        if (sleeping >= threshold) {
            instance.time = 0
            eligible.forEach { it.setTag(SLEEPING_TAG, false) }
        }
    }
}
