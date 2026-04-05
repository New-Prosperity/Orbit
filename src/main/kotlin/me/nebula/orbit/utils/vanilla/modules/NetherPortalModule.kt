package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.packBlockPos
import me.nebula.orbit.utils.vanilla.unpackBlockX
import me.nebula.orbit.utils.vanilla.unpackBlockY
import me.nebula.orbit.utils.vanilla.unpackBlockZ
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material

private const val MIN_WIDTH = 2
private const val MAX_WIDTH = 21
private const val MIN_HEIGHT = 3
private const val MAX_HEIGHT = 21

private enum class Axis { X, Z }

object NetherPortalModule : VanillaModule {

    override val id = "nether-portal"
    override val description = "Detect obsidian portal frames and fill with portal blocks on flint-and-steel ignition"
    override val configParams = listOf(
        ConfigParam.IntParam("maxWidth", "Maximum portal width (interior)", MAX_WIDTH, 2, 21),
        ConfigParam.IntParam("maxHeight", "Maximum portal height (interior)", MAX_HEIGHT, 3, 21),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val maxW = config.getInt("maxWidth", MAX_WIDTH)
        val maxH = config.getInt("maxHeight", MAX_HEIGHT)

        val node = EventNode.all("vanilla-nether-portal")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.player.itemInMainHand.material() != Material.FLINT_AND_STEEL) return@addListener

            val x = event.blockPosition.blockX()
            val y = event.blockPosition.blockY()
            val z = event.blockPosition.blockZ()
            val targetPos = event.blockPosition.relative(event.blockFace)
            val tx = targetPos.blockX()
            val ty = targetPos.blockY()
            val tz = targetPos.blockZ()

            if (!event.instance.getBlock(tx, ty, tz).isAir) return@addListener

            val filled = tryIgnitePortal(event.instance, tx, ty, tz, maxW, maxH)
            if (filled) {
                event.isCancelled = true
            }
        }

        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val block = event.block
            if (block.name() != "minecraft:nether_portal") return@addListener

            val x = event.blockPosition.blockX()
            val y = event.blockPosition.blockY()
            val z = event.blockPosition.blockZ()
            val axis = block.getProperty("axis") ?: "x"

            destroyPortal(event.instance, x, y, z, if (axis == "x") Axis.X else Axis.Z)
        }

        return node
    }

    private fun tryIgnitePortal(instance: Instance, x: Int, y: Int, z: Int, maxW: Int, maxH: Int): Boolean {
        for (axis in Axis.entries) {
            val result = detectFrame(instance, x, y, z, axis, maxW, maxH)
            if (result != null) {
                fillPortal(instance, result, axis)
                return true
            }
        }
        return false
    }

    private fun detectFrame(
        instance: Instance,
        startX: Int,
        startY: Int,
        startZ: Int,
        axis: Axis,
        maxW: Int,
        maxH: Int,
    ): PortalFrame? {
        val bottomY = findFrameEdge(instance, startX, startY, startZ, 0, -1, 0, axis, maxH) + 1
        if (bottomY > startY) return null

        val leftStart = findFrameEdgeHorizontal(instance, startX, bottomY, startZ, axis, -1, maxW) + 1
        val rightEnd = findFrameEdgeHorizontal(instance, startX, bottomY, startZ, axis, 1, maxW) - 1

        val width = horizontalDistance(leftStart, startZ, rightEnd, startZ, axis) + 1
        if (width < MIN_WIDTH || width > maxW) return null

        val topY = findFrameEdge(instance, startX, startY, startZ, 0, 1, 0, axis, maxH) - 1
        val height = topY - bottomY + 1
        if (height < MIN_HEIGHT || height > maxH) return null

        val (lx, lz) = horizontalPos(leftStart, startZ, axis)
        val (rx, rz) = horizontalPos(rightEnd, startZ, axis)

        if (!verifyFrame(instance, lx, lz, rx, rz, bottomY, topY, axis, width)) return null

        return PortalFrame(lx, lz, bottomY, width, height, axis)
    }

    private fun findFrameEdge(
        instance: Instance,
        x: Int,
        y: Int,
        z: Int,
        dx: Int,
        dy: Int,
        dz: Int,
        axis: Axis,
        max: Int,
    ): Int {
        var cy = y
        for (i in 0 until max) {
            cy += dy
            if (!isPortalInterior(instance, x, cy, z)) return cy
        }
        return cy
    }

    private fun findFrameEdgeHorizontal(
        instance: Instance,
        x: Int,
        y: Int,
        z: Int,
        axis: Axis,
        direction: Int,
        max: Int,
    ): Int {
        var pos = if (axis == Axis.X) x else z
        for (i in 0 until max) {
            pos += direction
            val (cx, cz) = horizontalPos(pos, if (axis == Axis.X) z else x, axis)
            if (!isPortalInterior(instance, cx, y, cz)) return pos
        }
        return pos
    }

    private fun verifyFrame(
        instance: Instance,
        lx: Int,
        lz: Int,
        rx: Int,
        rz: Int,
        bottomY: Int,
        topY: Int,
        axis: Axis,
        width: Int,
    ): Boolean {
        val (startH, endH) = if (axis == Axis.X) (lx to rx) else (lz to rz)
        val fixedCoord = if (axis == Axis.X) lz else lx

        for (h in startH..endH) {
            val (bx, bz) = horizontalPos(h, fixedCoord, axis)
            if (!isObsidian(instance, bx, bottomY - 1, bz)) return false
            if (!isObsidian(instance, bx, topY + 1, bz)) return false
        }

        for (y in bottomY..topY) {
            val (llx, llz) = horizontalPos(startH - 1, fixedCoord, axis)
            val (rrx, rrz) = horizontalPos(endH + 1, fixedCoord, axis)
            if (!isObsidian(instance, llx, y, llz)) return false
            if (!isObsidian(instance, rrx, y, rrz)) return false
        }

        for (h in startH - 1..endH + 1) {
            val (cx, cz) = horizontalPos(h, fixedCoord, axis)
            if (!isObsidian(instance, cx, bottomY - 1, cz) && h != startH - 1 && h != endH + 1) return false
        }

        for (h in startH..endH) {
            for (y in bottomY..topY) {
                val (cx, cz) = horizontalPos(h, fixedCoord, axis)
                val block = instance.getBlock(cx, y, cz)
                if (!block.isAir && block.name() != "minecraft:nether_portal") return false
            }
        }

        return true
    }

    private fun fillPortal(instance: Instance, frame: PortalFrame, axis: Axis) {
        val axisValue = if (axis == Axis.X) "x" else "z"
        val portalBlock = Block.NETHER_PORTAL.withProperty("axis", axisValue)

        val (startH, fixedCoord) = if (axis == Axis.X) {
            frame.originX to frame.originZ
        } else {
            frame.originZ to frame.originX
        }

        for (h in startH until startH + frame.width) {
            for (y in frame.bottomY until frame.bottomY + frame.height) {
                val (cx, cz) = horizontalPos(h, fixedCoord, axis)
                instance.setBlock(cx, y, cz, portalBlock)
            }
        }
    }

    private fun destroyPortal(instance: Instance, startX: Int, startY: Int, startZ: Int, axis: Axis) {
        val visited = HashSet<Long>()
        val queue = ArrayDeque<Long>()
        queue.add(packBlockPos(startX, startY, startZ))

        while (queue.isNotEmpty()) {
            val packed = queue.removeFirst()
            if (!visited.add(packed)) continue

            val x = unpackBlockX(packed)
            val y = unpackBlockY(packed)
            val z = unpackBlockZ(packed)

            val block = instance.getBlock(x, y, z)
            if (block.name() != "minecraft:nether_portal") continue

            instance.setBlock(x, y, z, Block.AIR)

            queue.add(packBlockPos(x, y - 1, z))
            queue.add(packBlockPos(x, y + 1, z))
            if (axis == Axis.X) {
                queue.add(packBlockPos(x - 1, y, z))
                queue.add(packBlockPos(x + 1, y, z))
            } else {
                queue.add(packBlockPos(x, y, z - 1))
                queue.add(packBlockPos(x, y, z + 1))
            }
        }
    }

    private fun isObsidian(instance: Instance, x: Int, y: Int, z: Int): Boolean =
        instance.getBlock(x, y, z).compare(Block.OBSIDIAN)

    private fun isPortalInterior(instance: Instance, x: Int, y: Int, z: Int): Boolean {
        val block = instance.getBlock(x, y, z)
        return block.isAir || block.name() == "minecraft:nether_portal" || block.name() == "minecraft:fire"
    }

    private fun horizontalPos(h: Int, fixed: Int, axis: Axis): Pair<Int, Int> =
        if (axis == Axis.X) h to fixed else fixed to h

    private fun horizontalDistance(x1: Int, z1: Int, x2: Int, z2: Int, axis: Axis): Int =
        if (axis == Axis.X) x2 - x1 else z2 - z1

    private data class PortalFrame(
        val originX: Int,
        val originZ: Int,
        val bottomY: Int,
        val width: Int,
        val height: Int,
        val axis: Axis,
    )
}
