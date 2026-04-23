package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.gson.GsonProvider
import me.nebula.orbit.utils.nebulaworld.NebulaWorld
import me.nebula.orbit.utils.nebulaworld.NebulaWorldReader
import me.nebula.orbit.utils.nebulaworld.NebulaWorldWriter
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FurniturePersistencePipelineTest {

    private val gson = GsonProvider.default

    private fun manifestBytes(pieces: List<FurniturePersistence.PersistedPiece>): ByteArray =
        gson.toJson(FurniturePersistence.Manifest(pieces = pieces)).toByteArray(Charsets.UTF_8)

    @Test
    fun `nebula world round trip preserves furniture manifest bytes`() {
        val payload = manifestBytes(listOf(
            FurniturePersistence.PersistedPiece(
                uuid = "00000000-0000-0000-0000-000000000042",
                definitionId = "oak_chair",
                anchorX = 10, anchorY = 64, anchorZ = 20,
                yawDegrees = 90f,
            ),
        ))
        val world = NebulaWorld(
            dataVersion = 1,
            minSection = -4,
            maxSection = 19,
            userData = payload,
            chunks = emptyMap(),
        )

        val bytes = NebulaWorldWriter.write(world)
        val restored = NebulaWorldReader.read(bytes)

        assertContentEquals(payload, restored.userData)
    }

    @Test
    fun `full pipeline preserves piece fields through nebula write and decode`() {
        val owner = UUID.randomUUID().toString()
        val pieceUuid = UUID.randomUUID().toString()
        val original = FurniturePersistence.PersistedPiece(
            uuid = pieceUuid,
            definitionId = "oak_cabinet",
            anchorX = -128, anchorY = 72, anchorZ = 512,
            yawDegrees = 37.3f,
            pitchDegrees = 12.5f,
            rollDegrees = -7.25f,
            openCloseOpen = true,
            inventoryBase64 = "aGVsbG8gd29ybGQ=",
            owner = owner,
        )
        val world = NebulaWorld(
            dataVersion = 1,
            minSection = -4,
            maxSection = 19,
            userData = manifestBytes(listOf(original)),
            chunks = emptyMap(),
        )

        val bytes = NebulaWorldWriter.write(world)
        val restored = NebulaWorldReader.read(bytes)
        val manifest = FurniturePersistence.decode(restored.userData)

        assertNotNull(manifest)
        assertEquals(1, manifest.pieces.size)
        val got = manifest.pieces[0]
        assertEquals(original.uuid, got.uuid)
        assertEquals(original.definitionId, got.definitionId)
        assertEquals(original.anchorX, got.anchorX)
        assertEquals(original.anchorY, got.anchorY)
        assertEquals(original.anchorZ, got.anchorZ)
        assertEquals(original.yawDegrees, got.yawDegrees)
        assertEquals(original.pitchDegrees, got.pitchDegrees)
        assertEquals(original.rollDegrees, got.rollDegrees)
        assertEquals(original.openCloseOpen, got.openCloseOpen)
        assertEquals(original.inventoryBase64, got.inventoryBase64)
        assertEquals(original.owner, got.owner)
    }

    @Test
    fun `empty manifest userData survives world round trip as empty bytes`() {
        val world = NebulaWorld(
            dataVersion = 1,
            minSection = -4,
            maxSection = 19,
            userData = ByteArray(0),
            chunks = emptyMap(),
        )

        val bytes = NebulaWorldWriter.write(world)
        val restored = NebulaWorldReader.read(bytes)

        assertContentEquals(ByteArray(0), restored.userData)
    }

    @Test
    fun `multi-piece manifest survives world round trip with all pieces intact`() {
        val pieces = listOf(
            FurniturePersistence.PersistedPiece(
                uuid = UUID.randomUUID().toString(),
                definitionId = "oak_chair",
                anchorX = 0, anchorY = 64, anchorZ = 0,
                yawDegrees = 0f,
            ),
            FurniturePersistence.PersistedPiece(
                uuid = UUID.randomUUID().toString(),
                definitionId = "oak_dining_table",
                anchorX = 5, anchorY = 64, anchorZ = 5,
                yawDegrees = 180f,
            ),
            FurniturePersistence.PersistedPiece(
                uuid = UUID.randomUUID().toString(),
                definitionId = "stone_lamp",
                anchorX = 10, anchorY = 65, anchorZ = 10,
                yawDegrees = 45f,
                pitchDegrees = 10f,
            ),
        )
        val world = NebulaWorld(
            dataVersion = 1,
            minSection = -4,
            maxSection = 19,
            userData = manifestBytes(pieces),
            chunks = emptyMap(),
        )

        val restored = NebulaWorldReader.read(NebulaWorldWriter.write(world))
        val manifest = FurniturePersistence.decode(restored.userData)

        assertNotNull(manifest)
        assertEquals(3, manifest.pieces.size)
        assertEquals(pieces.map { it.definitionId }, manifest.pieces.map { it.definitionId })
        assertEquals(pieces.map { it.uuid }, manifest.pieces.map { it.uuid })
    }
}
