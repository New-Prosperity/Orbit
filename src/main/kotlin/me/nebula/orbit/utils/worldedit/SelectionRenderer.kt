package me.nebula.orbit.utils.worldedit

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SelectionRenderer {

    private val activeSelections = ConcurrentHashMap<UUID, CuboidSelection>()

    fun update(player: Player, region: CuboidSelection?) {
        if (region == null) {
            activeSelections.remove(player.uuid)
            return
        }
        activeSelections[player.uuid] = region
    }

    fun clear(player: Player) {
        activeSelections.remove(player.uuid)
    }

    fun tick() {
        for ((uuid, sel) in activeSelections) {
            val player = MinecraftServer.getConnectionManager()
                .getOnlinePlayerByUuid(uuid) ?: continue
            renderEdges(player, sel)
        }
    }

    private fun renderEdges(player: Player, sel: CuboidSelection) {
        val px = player.position.blockX()
        val pz = player.position.blockZ()
        val maxDist = 32

        val minX = sel.minX.toDouble()
        val minY = sel.minY.toDouble()
        val minZ = sel.minZ.toDouble()
        val maxX = sel.maxX + 1.0
        val maxY = sel.maxY + 1.0
        val maxZ = sel.maxZ + 1.0

        val step = 0.5
        for (edge in edges(minX, minY, minZ, maxX, maxY, maxZ)) {
            val (sx, sy, sz, ex, ey, ez) = edge
            val dx = ex - sx
            val dy = ey - sy
            val dz = ez - sz
            val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
            val steps = (length / step).toInt().coerceAtMost(100)
            for (i in 0..steps) {
                val t = i.toDouble() / steps.coerceAtLeast(1)
                val x = sx + dx * t
                val y = sy + dy * t
                val z = sz + dz * t
                if (kotlin.math.abs(x - px) > maxDist || kotlin.math.abs(z - pz) > maxDist) continue
                player.sendPacket(ParticlePacket(Particle.FLAME, x, y, z, 0f, 0f, 0f, 0f, 0))
            }
        }
    }

    private fun edges(
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
    ): List<Edge> = listOf(
        Edge(x1, y1, z1, x2, y1, z1), Edge(x1, y1, z1, x1, y2, z1),
        Edge(x1, y1, z1, x1, y1, z2), Edge(x2, y2, z2, x1, y2, z2),
        Edge(x2, y2, z2, x2, y1, z2), Edge(x2, y2, z2, x2, y2, z1),
        Edge(x1, y2, z1, x2, y2, z1), Edge(x1, y2, z1, x1, y2, z2),
        Edge(x2, y1, z1, x2, y2, z1), Edge(x2, y1, z1, x2, y1, z2),
        Edge(x1, y1, z2, x2, y1, z2), Edge(x1, y1, z2, x1, y2, z2),
    )

    private data class Edge(val sx: Double, val sy: Double, val sz: Double, val ex: Double, val ey: Double, val ez: Double)
}
