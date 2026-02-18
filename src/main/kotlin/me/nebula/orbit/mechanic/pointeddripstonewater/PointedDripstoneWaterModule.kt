package me.nebula.orbit.mechanic.pointeddripstonewater

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.utils.blockindex.BlockPositionIndex
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import kotlin.random.Random

class PointedDripstoneWaterModule : OrbitModule("pointed-dripstone-water") {

    private val index = BlockPositionIndex(setOf("minecraft:pointed_dripstone"), eventNode).install()

    override fun onEnable() {
        super.onEnable()

        index.instancePositions.cleanOnInstanceRemove { it }

        MinecraftServer.getSchedulerManager().buildTask {
            for (instance in MinecraftServer.getInstanceManager().instances) {
                for (player in instance.players) {
                    val nearby = index.positionsNear(instance, player.position.asVec(), 10.0)

                    for (vec in nearby) {
                        val bx = vec.x().toInt()
                        val by = vec.y().toInt()
                        val bz = vec.z().toInt()

                        val block = instance.getBlock(bx, by, bz)
                        val vertical = block.getProperty("vertical_direction") ?: continue
                        if (vertical != "down") continue
                        val thickness = block.getProperty("thickness") ?: continue
                        if (thickness != "tip") continue

                        if (Random.nextFloat() > 0.01f) continue

                        for (searchY in (by + 1)..(by + 11)) {
                            val above = instance.getBlock(bx, searchY, bz)
                            if (above.name() == "minecraft:water" || above.getProperty("waterlogged") == "true") {
                                val belowTip = instance.getBlock(bx, by - 1, bz)
                                if (belowTip.name() == "minecraft:cauldron") {
                                    val level = belowTip.getProperty("level")?.toIntOrNull() ?: 0
                                    if (level < 3) {
                                        val waterCauldron = Block.fromKey("minecraft:water_cauldron")
                                            ?.withProperty("level", (level + 1).toString())
                                        if (waterCauldron != null) {
                                            instance.setBlock(bx, by - 1, bz, waterCauldron)
                                        }
                                    }
                                }
                                break
                            }
                            if (!above.isAir && above.name() != "minecraft:pointed_dripstone" && above.name() != "minecraft:dripstone_block") break
                        }
                    }
                }
            }
        }.repeat(TaskSchedule.tick(200)).schedule()
    }

    override fun onDisable() {
        index.clear()
        super.onDisable()
    }
}
