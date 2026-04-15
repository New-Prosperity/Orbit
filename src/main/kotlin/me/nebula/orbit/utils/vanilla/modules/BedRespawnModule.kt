package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerRespawnEvent
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag

private val BED_NAMES = setOf(
    "minecraft:white_bed", "minecraft:orange_bed", "minecraft:magenta_bed",
    "minecraft:light_blue_bed", "minecraft:yellow_bed", "minecraft:lime_bed",
    "minecraft:pink_bed", "minecraft:gray_bed", "minecraft:light_gray_bed",
    "minecraft:cyan_bed", "minecraft:purple_bed", "minecraft:blue_bed",
    "minecraft:brown_bed", "minecraft:green_bed", "minecraft:red_bed",
    "minecraft:black_bed",
)

private val TAG_BED_X = Tag.Double("nebula:bed_x")
private val TAG_BED_Y = Tag.Double("nebula:bed_y")
private val TAG_BED_Z = Tag.Double("nebula:bed_z")
private val TAG_BED_SET = Tag.Boolean("nebula:bed_set")

object BedRespawnModule : VanillaModule {

    override val id = "bed-respawn"
    override val description = "Right-click beds to set spawn point, respawn at bed location"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-bed-respawn")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() !in BED_NAMES) return@addListener

            val x = event.blockPosition.blockX() + 0.5
            val y = event.blockPosition.blockY() + 0.5625
            val z = event.blockPosition.blockZ() + 0.5

            event.player.setTag(TAG_BED_X, x)
            event.player.setTag(TAG_BED_Y, y)
            event.player.setTag(TAG_BED_Z, z)
            event.player.setTag(TAG_BED_SET, true)
        }

        node.addListener(PlayerRespawnEvent::class.java) { event ->
            val bedSet = event.player.getTag(TAG_BED_SET) ?: false
            if (!bedSet) return@addListener

            val x = event.player.getTag(TAG_BED_X) ?: return@addListener
            val y = event.player.getTag(TAG_BED_Y) ?: return@addListener
            val z = event.player.getTag(TAG_BED_Z) ?: return@addListener

            val inst = event.player.instance ?: return@addListener
            val bedBlock = inst.getBlock(x.toInt(), (y - 0.5625).toInt(), z.toInt())
            if (bedBlock.name() !in BED_NAMES) {
                event.player.removeTag(TAG_BED_SET)
                return@addListener
            }

            val aboveBed = inst.getBlock(x.toInt(), (y - 0.5625).toInt() + 1, z.toInt())
            if (aboveBed.isSolid) return@addListener

            event.respawnPosition = Pos(x, y, z)
        }

        return node
    }
}
