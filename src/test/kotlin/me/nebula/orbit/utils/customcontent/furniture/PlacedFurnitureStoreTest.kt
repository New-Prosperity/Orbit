package me.nebula.orbit.utils.customcontent.furniture

import io.mockk.mockk
import net.minestom.server.instance.Instance
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlacedFurnitureStoreTest {

    private lateinit var instanceA: Instance
    private lateinit var instanceB: Instance

    @BeforeEach
    fun setup() {
        PlacedFurnitureStore.clearAll()
        instanceA = mockk(relaxed = true)
        instanceB = mockk(relaxed = true)
    }

    @AfterEach
    fun teardown() {
        PlacedFurnitureStore.clearAll()
    }

    private fun makeInstance(
        instance: Instance,
        id: String = "chair",
        anchorX: Int = 0,
        anchorY: Int = 64,
        anchorZ: Int = 0,
        cells: List<Triple<Int, Int, Int>> = listOf(Triple(anchorX, anchorY, anchorZ)),
    ): FurnitureInstance {
        return FurnitureInstance(
            uuid = UUID.randomUUID(),
            definitionId = id,
            instance = instance,
            anchorX = anchorX,
            anchorY = anchorY,
            anchorZ = anchorZ,
            yawDegrees = 0f,
            displayEntityId = -1,
            cellKeys = cells.map { (x, y, z) -> FurnitureInstance.packKey(x, y, z) },
        )
    }

    @Test
    fun `add indexes by uuid cell and chunk`() {
        val f = makeInstance(instanceA, anchorX = 10, anchorZ = 20)
        assertEquals(PlacedFurnitureStore.AddResult.Success, PlacedFurnitureStore.add(f))
        assertEquals(f, PlacedFurnitureStore.byUuid(instanceA, f.uuid))
        assertEquals(f, PlacedFurnitureStore.atCell(instanceA, 10, 64, 20))
        assertTrue(PlacedFurnitureStore.inChunk(instanceA, 0, 1).contains(f))
    }

    @Test
    fun `add rejects overlapping cell`() {
        val a = makeInstance(instanceA, anchorX = 0, anchorZ = 0)
        val b = makeInstance(instanceA, anchorX = 0, anchorZ = 0)
        assertEquals(PlacedFurnitureStore.AddResult.Success, PlacedFurnitureStore.add(a))
        val result = PlacedFurnitureStore.add(b)
        assertTrue(result is PlacedFurnitureStore.AddResult.Conflict)
    }

    @Test
    fun `add rejects duplicate uuid`() {
        val a = makeInstance(instanceA)
        val b = a.copy(anchorX = 5, anchorZ = 5, cellKeys = listOf(FurnitureInstance.packKey(5, 64, 5)))
        assertEquals(PlacedFurnitureStore.AddResult.Success, PlacedFurnitureStore.add(a))
        assertEquals(PlacedFurnitureStore.AddResult.DuplicateUuid, PlacedFurnitureStore.add(b))
    }

    @Test
    fun `remove clears all indexes`() {
        val f = makeInstance(instanceA, anchorX = 3, anchorZ = 3)
        PlacedFurnitureStore.add(f)
        val removed = PlacedFurnitureStore.remove(instanceA, f.uuid)
        assertNotNull(removed)
        assertNull(PlacedFurnitureStore.byUuid(instanceA, f.uuid))
        assertNull(PlacedFurnitureStore.atCell(instanceA, 3, 64, 3))
        assertFalse(PlacedFurnitureStore.inChunk(instanceA, 0, 0).contains(f))
    }

    @Test
    fun `remove returns null for unknown uuid`() {
        assertNull(PlacedFurnitureStore.remove(instanceA, UUID.randomUUID()))
    }

    @Test
    fun `multi-cell footprint indexes every cell`() {
        val cells = listOf(
            Triple(10, 64, 20),
            Triple(11, 64, 20),
            Triple(10, 64, 21),
            Triple(11, 64, 21),
        )
        val f = makeInstance(instanceA, anchorX = 10, anchorZ = 20, cells = cells)
        PlacedFurnitureStore.add(f)
        for ((x, y, z) in cells) {
            assertEquals(f, PlacedFurnitureStore.atCell(instanceA, x, y, z), "cell ($x,$y,$z) should resolve")
        }
        assertEquals(1, PlacedFurnitureStore.count(instanceA))
    }

    @Test
    fun `per-instance isolation`() {
        val a = makeInstance(instanceA, anchorX = 0, anchorZ = 0)
        val b = makeInstance(instanceB, anchorX = 0, anchorZ = 0)
        PlacedFurnitureStore.add(a)
        PlacedFurnitureStore.add(b)
        assertEquals(1, PlacedFurnitureStore.count(instanceA))
        assertEquals(1, PlacedFurnitureStore.count(instanceB))
        assertEquals(a, PlacedFurnitureStore.atCell(instanceA, 0, 64, 0))
        assertEquals(b, PlacedFurnitureStore.atCell(instanceB, 0, 64, 0))
    }

    @Test
    fun `chunk index aggregates multiple furniture in same chunk`() {
        val a = makeInstance(instanceA, anchorX = 2, anchorZ = 3)
        val b = makeInstance(instanceA, anchorX = 5, anchorZ = 7)
        PlacedFurnitureStore.add(a)
        PlacedFurnitureStore.add(b)
        val inChunk = PlacedFurnitureStore.inChunk(instanceA, 0, 0)
        assertEquals(2, inChunk.size)
    }

    @Test
    fun `chunk index splits across chunk boundaries`() {
        val inChunk0 = makeInstance(instanceA, anchorX = 5, anchorZ = 5)
        val inChunk1 = makeInstance(instanceA, anchorX = 17, anchorZ = 5)
        PlacedFurnitureStore.add(inChunk0)
        PlacedFurnitureStore.add(inChunk1)
        assertEquals(1, PlacedFurnitureStore.inChunk(instanceA, 0, 0).size)
        assertEquals(1, PlacedFurnitureStore.inChunk(instanceA, 1, 0).size)
    }

    @Test
    fun `clear removes only the target instance`() {
        val a = makeInstance(instanceA, anchorX = 0, anchorZ = 0)
        val b = makeInstance(instanceB, anchorX = 0, anchorZ = 0)
        PlacedFurnitureStore.add(a)
        PlacedFurnitureStore.add(b)
        PlacedFurnitureStore.clear(instanceA)
        assertEquals(0, PlacedFurnitureStore.count(instanceA))
        assertEquals(1, PlacedFurnitureStore.count(instanceB))
    }

    @Test
    fun `updateDisplayEntityId swaps entity id without losing state`() {
        val f = makeInstance(instanceA)
        PlacedFurnitureStore.add(f)
        PlacedFurnitureStore.updateDisplayEntityId(instanceA, f.uuid, 42)
        assertEquals(42, PlacedFurnitureStore.byUuid(instanceA, f.uuid)?.displayEntityId)
    }

    @Test
    fun `byInteractionEntity resolves when interaction ids are indexed`() {
        val f = FurnitureInstance(
            uuid = UUID.randomUUID(),
            definitionId = "rug",
            instance = instanceA,
            anchorX = 0, anchorY = 64, anchorZ = 0,
            yawDegrees = 0f,
            displayEntityId = -1,
            cellKeys = emptyList(),
            interactionEntityIds = listOf(100, 101),
        )
        PlacedFurnitureStore.add(f)
        assertEquals(f, PlacedFurnitureStore.byInteractionEntity(instanceA, 100))
        assertEquals(f, PlacedFurnitureStore.byInteractionEntity(instanceA, 101))
    }

    @Test
    fun `packKey and unpackKey round trip negative coordinates`() {
        val cases = listOf(
            Triple(0, 0, 0),
            Triple(100, 64, 200),
            Triple(-50, -30, -100),
            Triple(1_000_000, 320, -1_000_000),
        )
        for ((x, y, z) in cases) {
            val key = FurnitureInstance.packKey(x, y, z)
            assertEquals(Triple(x, y, z), FurnitureInstance.unpackKey(key))
        }
    }
}
