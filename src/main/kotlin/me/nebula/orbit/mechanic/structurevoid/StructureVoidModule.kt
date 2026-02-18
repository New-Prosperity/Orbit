package me.nebula.orbit.mechanic.structurevoid

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent

class StructureVoidModule : OrbitModule("structure-void") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:structure_void") return@addListener
            if (event.player.gameMode != GameMode.CREATIVE) {
                event.isCancelled = true
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() != "minecraft:structure_void") return@addListener
            if (event.player.gameMode != GameMode.CREATIVE) {
                event.isCancelled = true
            }
        }
    }
}
