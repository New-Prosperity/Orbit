package me.nebula.orbit.utils.randomteleport

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import kotlin.random.Random

data class RandomTeleportResult(
    val success: Boolean,
    val position: Pos?,
    val attempts: Int,
)

class RandomTeleportBuilder @PublishedApi internal constructor(private val player: Player) {

    @PublishedApi internal var minX: Int = -500
    @PublishedApi internal var maxX: Int = 500
    @PublishedApi internal var minZ: Int = -500
    @PublishedApi internal var maxZ: Int = 500
    @PublishedApi internal var minY: Int = -64
    @PublishedApi internal var maxY: Int = 320
    @PublishedApi internal var instance: Instance? = null
    @PublishedApi internal var maxAttempts: Int = 10
    @PublishedApi internal var safeCheck: Boolean = true

    fun minX(value: Int) { minX = value }
    fun maxX(value: Int) { maxX = value }
    fun minZ(value: Int) { minZ = value }
    fun maxZ(value: Int) { maxZ = value }
    fun minY(value: Int) { minY = value }
    fun maxY(value: Int) { maxY = value }
    fun instance(inst: Instance) { instance = inst }
    fun maxAttempts(value: Int) { maxAttempts = value }
    fun safeCheck(value: Boolean) { safeCheck = value }

    @PublishedApi internal fun execute(): RandomTeleportResult {
        val inst = instance ?: player.instance ?: return RandomTeleportResult(false, null, 0)

        for (attempt in 1..maxAttempts) {
            val x = Random.nextInt(minX, maxX + 1)
            val z = Random.nextInt(minZ, maxZ + 1)

            if (!safeCheck) {
                val pos = Pos(x.toDouble() + 0.5, maxY.toDouble(), z.toDouble() + 0.5)
                player.teleport(pos)
                return RandomTeleportResult(true, pos, attempt)
            }

            val safePos = findSafePosition(inst, x, z)
            if (safePos != null) {
                player.teleport(safePos)
                return RandomTeleportResult(true, safePos, attempt)
            }
        }

        return RandomTeleportResult(false, null, maxAttempts)
    }

    private fun findSafePosition(inst: Instance, x: Int, z: Int): Pos? {
        for (y in maxY downTo minY) {
            val below = inst.getBlock(x, y - 1, z)
            val feet = inst.getBlock(x, y, z)
            val head = inst.getBlock(x, y + 1, z)

            if (below.isSolid && !below.isLiquid && feet.isAir && head.isAir) {
                return Pos(x.toDouble() + 0.5, y.toDouble(), z.toDouble() + 0.5)
            }
        }
        return null
    }
}

inline fun randomTeleport(player: Player, block: RandomTeleportBuilder.() -> Unit): RandomTeleportResult =
    RandomTeleportBuilder(player).apply(block).execute()

@JvmName("randomTeleportExtension")
inline fun Player.randomTeleport(block: RandomTeleportBuilder.() -> Unit): RandomTeleportResult =
    RandomTeleportBuilder(this).apply(block).execute()
