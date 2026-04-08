package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material

class BuildShelterGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val blockCount = BUILD_BLOCKS.sumOf { brain.countItem(it) }
        return if (blockCount >= 20) 0.4f else 0f
    }

    override fun shouldActivate(brain: BotBrain): Boolean {
        val blockCount = BUILD_BLOCKS.sumOf { brain.countItem(it) }
        return blockCount >= 20
    }

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val baseX = player.position.blockX() + 2
        val baseY = player.position.blockY()
        val baseZ = player.position.blockZ() + 2

        val material = BUILD_BLOCKS.firstOrNull { brain.hasItem(it) } ?: return listOf(Wait(10))
        val block = materialToBlock(material) ?: return listOf(Wait(10))

        val actions = mutableListOf<BotAction>()
        for (x in 0..2) {
            for (z in 0..2) {
                actions.add(PlaceBlock(Pos(baseX + x.toDouble(), baseY.toDouble(), baseZ + z.toDouble()), block))
            }
        }
        for (x in 0..2) {
            for (z in 0..2) {
                if (x == 0 || x == 2 || z == 0 || z == 2) {
                    for (y in 1..2) {
                        actions.add(PlaceBlock(Pos(baseX + x.toDouble(), baseY + y.toDouble(), baseZ + z.toDouble()), block))
                    }
                }
            }
        }
        for (x in 0..2) {
            for (z in 0..2) {
                actions.add(PlaceBlock(Pos(baseX + x.toDouble(), baseY + 3.0, baseZ + z.toDouble()), block))
            }
        }
        brain.memory.rememberLocation("shelter", player.position)
        return actions
    }

    private fun materialToBlock(material: Material): Block? = when (material) {
        Material.COBBLESTONE -> Block.COBBLESTONE
        Material.DIRT -> Block.DIRT
        Material.OAK_PLANKS -> Block.OAK_PLANKS
        Material.BIRCH_PLANKS -> Block.BIRCH_PLANKS
        Material.SPRUCE_PLANKS -> Block.SPRUCE_PLANKS
        Material.STONE -> Block.STONE
        Material.SAND -> Block.SAND
        Material.SANDSTONE -> Block.SANDSTONE
        else -> null
    }
}

class BridgeGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        if (!hasBuildBlocks(brain)) return 0f
        if (!hasGapAhead(brain)) return 0f
        return 0.4f
    }

    override fun shouldActivate(brain: BotBrain): Boolean =
        hasBuildBlocks(brain) && hasGapAhead(brain)

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val yaw = Math.toRadians(player.position.yaw().toDouble())
        val direction = Vec(-kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw))
        val gapLength = measureGap(brain, direction).coerceIn(1, 16)
        val block = findBuildBlock(brain) ?: return listOf(Wait(10))
        return listOf(BridgeForward(direction, gapLength, block))
    }

    override fun shouldCancel(brain: BotBrain): Boolean = !hasBuildBlocks(brain)

    private fun hasBuildBlocks(brain: BotBrain): Boolean =
        BUILD_BLOCKS.any { brain.countItem(it) >= 4 }

    private fun hasGapAhead(brain: BotBrain): Boolean {
        val player = brain.player
        val instance = player.instance ?: return false
        val yaw = Math.toRadians(player.position.yaw().toDouble())
        val dx = -kotlin.math.sin(yaw)
        val dz = kotlin.math.cos(yaw)
        val checkX = (player.position.x() + dx * 2).toInt()
        val checkZ = (player.position.z() + dz * 2).toInt()
        val floorY = player.position.blockY() - 1
        val block = instance.getBlock(checkX, floorY, checkZ)
        return block.isAir || block.isLiquid
    }

    private fun measureGap(brain: BotBrain, direction: Vec): Int {
        val player = brain.player
        val instance = player.instance ?: return 0
        val floorY = player.position.blockY() - 1
        var length = 0
        for (i in 1..16) {
            val checkX = (player.position.x() + direction.x() * i).toInt()
            val checkZ = (player.position.z() + direction.z() * i).toInt()
            val block = instance.getBlock(checkX, floorY, checkZ)
            if (block.isAir || block.isLiquid) length++
            else break
        }
        return length
    }

    private fun findBuildBlock(brain: BotBrain): Block? {
        for (mat in BUILD_BLOCKS) {
            if (brain.countItem(mat) >= 4) {
                return when (mat) {
                    Material.COBBLESTONE -> Block.COBBLESTONE
                    Material.DIRT -> Block.DIRT
                    Material.OAK_PLANKS -> Block.OAK_PLANKS
                    Material.BIRCH_PLANKS -> Block.BIRCH_PLANKS
                    Material.SPRUCE_PLANKS -> Block.SPRUCE_PLANKS
                    Material.STONE -> Block.STONE
                    Material.SAND -> Block.SAND
                    Material.SANDSTONE -> Block.SANDSTONE
                    else -> null
                }
            }
        }
        return null
    }
}
