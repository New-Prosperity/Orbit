package me.nebula.orbit.mechanic.endportal

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material

class EndPortalModule : OrbitModule("end-portal") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:end_portal_frame") return@addListener
            if (event.player.getItemInMainHand().material() != Material.ENDER_EYE) return@addListener

            val eye = event.block.getProperty("eye") ?: "false"
            if (eye == "true") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            instance.setBlock(pos, event.block.withProperty("eye", "true"))

            val consumed = event.player.getItemInMainHand().consume(1)
            event.player.setItemInMainHand(consumed)

            checkPortalFormation(instance, pos)
        }
    }

    private fun checkPortalFormation(instance: Instance, placedAt: net.minestom.server.coordinate.Point) {
        for (dx in -4..0) {
            for (dz in -4..0) {
                val originX = placedAt.x() + dx
                val originZ = placedAt.z() + dz
                val y = placedAt.y()

                if (checkFrame(instance, originX, y, originZ)) {
                    fillPortal(instance, originX, y, originZ)
                    return
                }
            }
        }
    }

    private fun checkFrame(instance: Instance, ox: Double, y: Double, oz: Double): Boolean {
        val positions = buildList {
            for (i in 1..3) add(Vec(ox + i, y, oz))
            for (i in 1..3) add(Vec(ox + i, y, oz + 4))
            for (i in 1..3) add(Vec(ox, y, oz + i))
            for (i in 1..3) add(Vec(ox + 4, y, oz + i))
        }

        return positions.all { pos ->
            val block = instance.getBlock(pos)
            block.name() == "minecraft:end_portal_frame" && block.getProperty("eye") == "true"
        }
    }

    private fun fillPortal(instance: Instance, ox: Double, y: Double, oz: Double) {
        val portal = Block.fromKey("minecraft:end_portal") ?: return
        for (x in 1..3) {
            for (z in 1..3) {
                instance.setBlock(Vec(ox + x, y, oz + z), portal)
            }
        }
    }
}
