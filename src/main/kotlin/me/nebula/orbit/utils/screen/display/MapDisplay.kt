package me.nebula.orbit.utils.screen.display

import me.nebula.orbit.utils.screen.ScreenBasis
import me.nebula.orbit.utils.screen.encoder.EncodedChunk
import me.nebula.orbit.utils.screen.encoder.MAP_SIZE
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.network.packet.server.play.MapDataPacket
import net.minestom.server.network.packet.server.play.SpawnEntityPacket
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private val nextEntityId = AtomicInteger(-6_000_000)
private val nextMapId = AtomicInteger(Int.MAX_VALUE)

private const val META_FLAGS = 0
private const val META_ITEM_FRAME_ITEM = 8

class MapDisplay(private val tilesX: Int, private val tilesY: Int) {

    private val entityIds = IntArray(tilesX * tilesY)
    private val mapIds = IntArray(tilesX * tilesY)
    private val previousHashes = LongArray(tilesX * tilesY)
    private val previousData = arrayOfNulls<ByteArray>(tilesX * tilesY)

    fun spawn(player: Player, basis: ScreenBasis) {
        val facing = basis.facing
        val total = tilesX * tilesY

        for (i in 0 until total) {
            entityIds[i] = nextEntityId.getAndDecrement()
            mapIds[i] = nextMapId.getAndDecrement()
        }

        for (row in 0 until tilesY) {
            for (col in 0 until tilesX) {
                val idx = row * tilesX + col
                val worldPos = tileWorldPos(col, row, basis)
                val framePos = Pos(worldPos.x(), worldPos.y(), worldPos.z())

                player.sendPacket(SpawnEntityPacket(
                    entityIds[idx], UUID.randomUUID(), EntityType.ITEM_FRAME,
                    framePos, 0f, facing, Vec.ZERO,
                ))

                val mapItem = ItemStack.of(Material.FILLED_MAP)
                    .with(DataComponents.MAP_ID, mapIds[idx])

                player.sendPacket(EntityMetaDataPacket(entityIds[idx], mapOf(
                    META_FLAGS to Metadata.Byte(0x20.toByte()),
                    META_ITEM_FRAME_ITEM to Metadata.ItemStack(mapItem),
                )))
            }
        }
    }

    fun update(player: Player, chunks: List<EncodedChunk>) {
        for (chunk in chunks) {
            val idx = chunk.gridY * tilesX + chunk.gridX
            if (idx < 0 || idx >= entityIds.size) continue
            if (chunk.hash == previousHashes[idx]) continue
            previousHashes[idx] = chunk.hash

            val prev = previousData[idx]
            previousData[idx] = chunk.data

            if (prev == null) {
                player.sendPacket(MapDataPacket(
                    mapIds[idx],
                    0.toByte(),
                    false,
                    false,
                    emptyList(),
                    MapDataPacket.ColorContent(
                        MAP_SIZE.toByte(),
                        MAP_SIZE.toByte(),
                        0.toByte(),
                        0.toByte(),
                        chunk.data,
                    ),
                ))
                continue
            }

            var minRow = MAP_SIZE
            var maxRow = -1
            for (row in 0 until MAP_SIZE) {
                val offset = row * MAP_SIZE
                for (col in 0 until MAP_SIZE) {
                    if (chunk.data[offset + col] != prev[offset + col]) {
                        if (row < minRow) minRow = row
                        if (row > maxRow) maxRow = row
                        break
                    }
                }
            }

            if (minRow > maxRow) continue

            val rowCount = maxRow - minRow + 1
            val subData = ByteArray(MAP_SIZE * rowCount)
            System.arraycopy(chunk.data, minRow * MAP_SIZE, subData, 0, subData.size)

            player.sendPacket(MapDataPacket(
                mapIds[idx],
                0.toByte(),
                false,
                false,
                emptyList(),
                MapDataPacket.ColorContent(
                    MAP_SIZE.toByte(),
                    rowCount.toByte(),
                    0.toByte(),
                    minRow.toByte(),
                    subData,
                ),
            ))
        }
    }

    fun destroy(player: Player) {
        val ids = entityIds.toList()
        if (ids.isNotEmpty()) {
            player.sendPacket(DestroyEntitiesPacket(ids))
        }
        previousHashes.fill(0)
        previousData.fill(null)
    }

    private fun tileWorldPos(col: Int, row: Int, basis: ScreenBasis): Vec {
        val localX = col + 0.5 - tilesX / 2.0
        val localY = tilesY / 2.0 - row - 0.5
        return basis.center
            .add(basis.right.mul(localX))
            .add(basis.up.mul(localY))
    }
}
