package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.gson.GsonProvider
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FurniturePersistenceTest {

    private val gson = GsonProvider.default

    @Test
    fun `empty userData decodes to null`() {
        assertNull(FurniturePersistence.decode(ByteArray(0)))
    }

    @Test
    fun `foreign magic decodes to null`() {
        val garbage = """{"magic":"something-else","version":1,"pieces":[]}""".toByteArray(Charsets.UTF_8)
        assertNull(FurniturePersistence.decode(garbage))
    }

    @Test
    fun `future version decodes to null`() {
        val future = """{"magic":"${FurniturePersistence.MANIFEST_MAGIC}","version":9999,"pieces":[]}""".toByteArray(Charsets.UTF_8)
        assertNull(FurniturePersistence.decode(future))
    }

    @Test
    fun `malformed json decodes to null`() {
        val junk = "{not valid json".toByteArray(Charsets.UTF_8)
        assertNull(FurniturePersistence.decode(junk))
    }

    @Test
    fun `round trip preserves multi-piece manifest`() {
        val manifest = FurniturePersistence.Manifest(
            pieces = listOf(
                FurniturePersistence.PersistedPiece(
                    uuid = UUID.randomUUID().toString(),
                    definitionId = "oak_chair",
                    anchorX = 10, anchorY = 64, anchorZ = 20,
                    yawDegrees = 37.3f,
                ),
                FurniturePersistence.PersistedPiece(
                    uuid = UUID.randomUUID().toString(),
                    definitionId = "dining_table",
                    anchorX = 11, anchorY = 64, anchorZ = 21,
                    yawDegrees = 90f, pitchDegrees = 15f, rollDegrees = -5f,
                    openCloseOpen = true,
                ),
            ),
        )
        val json = gson.toJson(manifest).toByteArray(Charsets.UTF_8)
        val decoded = FurniturePersistence.decode(json)
        assertNotNull(decoded)
        assertEquals(2, decoded.pieces.size)
        assertEquals("oak_chair", decoded.pieces[0].definitionId)
        assertEquals(37.3f, decoded.pieces[0].yawDegrees)
        assertEquals(true, decoded.pieces[1].openCloseOpen)
        assertEquals(15f, decoded.pieces[1].pitchDegrees)
    }

    @Test
    fun `manifest version defaults match constants`() {
        val manifest = FurniturePersistence.Manifest(pieces = emptyList())
        assertEquals(FurniturePersistence.MANIFEST_MAGIC, manifest.magic)
        assertEquals(FurniturePersistence.MANIFEST_VERSION, manifest.version)
    }

    @Test
    fun `piece defaults omit pitch and roll and open flag`() {
        val piece = FurniturePersistence.PersistedPiece(
            uuid = UUID.randomUUID().toString(),
            definitionId = "lamp",
            anchorX = 0, anchorY = 0, anchorZ = 0,
            yawDegrees = 0f,
        )
        assertEquals(0f, piece.pitchDegrees)
        assertEquals(0f, piece.rollDegrees)
        assertTrue(!piece.openCloseOpen)
    }

    @Test
    fun `v1 manifest still decodes with null inventoryBase64 and owner`() {
        val v1 = """{"magic":"${FurniturePersistence.MANIFEST_MAGIC}","version":1,"pieces":[{"uuid":"00000000-0000-0000-0000-000000000001","definitionId":"chair","anchorX":1,"anchorY":2,"anchorZ":3,"yawDegrees":0.0}]}""".toByteArray(Charsets.UTF_8)
        val decoded = FurniturePersistence.decode(v1)
        assertNotNull(decoded)
        assertEquals(1, decoded.version)
        assertEquals(1, decoded.pieces.size)
        assertNull(decoded.pieces[0].inventoryBase64)
        assertNull(decoded.pieces[0].owner)
    }

    @Test
    fun `v2 manifest round trips inventoryBase64 and owner`() {
        val owner = UUID.randomUUID().toString()
        val manifest = FurniturePersistence.Manifest(
            pieces = listOf(
                FurniturePersistence.PersistedPiece(
                    uuid = UUID.randomUUID().toString(),
                    definitionId = "cabinet",
                    anchorX = 5, anchorY = 64, anchorZ = 10,
                    yawDegrees = 0f,
                    inventoryBase64 = "aGVsbG8=",
                    owner = owner,
                ),
            ),
        )
        val bytes = gson.toJson(manifest).toByteArray(Charsets.UTF_8)
        val decoded = FurniturePersistence.decode(bytes)
        assertNotNull(decoded)
        assertEquals(2, decoded.version)
        assertEquals("aGVsbG8=", decoded.pieces[0].inventoryBase64)
        assertEquals(owner, decoded.pieces[0].owner)
    }

    @Test
    fun `decoded manifest matches original via json round trip`() {
        val original = FurniturePersistence.Manifest(pieces = listOf(
            FurniturePersistence.PersistedPiece(
                uuid = "00000000-0000-0000-0000-000000000001",
                definitionId = "test",
                anchorX = -33554432, anchorY = -2048, anchorZ = 33554431,
                yawDegrees = 179.9f,
            ),
        ))
        val bytes = gson.toJson(original).toByteArray(Charsets.UTF_8)
        val decoded = FurniturePersistence.decode(bytes)
        assertNotNull(decoded)
        assertEquals(original.pieces[0].uuid, decoded.pieces[0].uuid)
        assertEquals(original.pieces[0].anchorX, decoded.pieces[0].anchorX)
        assertEquals(original.pieces[0].anchorZ, decoded.pieces[0].anchorZ)
    }
}
