package me.nebula.orbit.utils.customcontent.furniture

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FurnitureRegistryTest {

    @BeforeEach
    fun setup() {
        FurnitureRegistry.clear()
    }

    @AfterEach
    fun teardown() {
        FurnitureRegistry.clear()
    }

    private fun def(id: String, itemId: String = id): FurnitureDefinition =
        FurnitureDefinition(id = id, itemId = itemId)

    @Test
    fun `register adds a definition and indexes it by itemId`() {
        val d = def("chair", "item_chair")
        FurnitureRegistry.register(d)
        assertEquals(d, FurnitureRegistry["chair"])
        assertEquals(d, FurnitureRegistry.fromItemId("item_chair"))
    }

    @Test
    fun `register rejects duplicate id`() {
        FurnitureRegistry.register(def("chair", "item_chair_a"))
        assertThrows<IllegalArgumentException> {
            FurnitureRegistry.register(def("chair", "item_chair_b"))
        }
    }

    @Test
    fun `register rejects two definitions with the same itemId`() {
        FurnitureRegistry.register(def("chair", "item_chair"))
        assertThrows<IllegalArgumentException> {
            FurnitureRegistry.register(def("couch", "item_chair"))
        }
    }

    @Test
    fun `require throws on missing id`() {
        assertThrows<IllegalStateException> { FurnitureRegistry.require("missing") }
    }

    @Test
    fun `all returns every registered definition`() {
        FurnitureRegistry.register(def("a"))
        FurnitureRegistry.register(def("b"))
        FurnitureRegistry.register(def("c"))
        assertEquals(3, FurnitureRegistry.all().size)
    }

    @Test
    fun `clear removes every registration`() {
        FurnitureRegistry.register(def("a"))
        FurnitureRegistry.register(def("b"))
        FurnitureRegistry.clear()
        assertTrue(FurnitureRegistry.isEmpty())
        assertNull(FurnitureRegistry["a"])
    }

    @Test
    fun `fromItemId returns null for unknown items`() {
        FurnitureRegistry.register(def("chair", "item_chair"))
        assertNull(FurnitureRegistry.fromItemId("item_couch"))
    }

    @Test
    fun `furniture DSL registers the definition`() {
        val d = furniture("lamp") {
            item("item_lamp")
            placeSound("block.glass.place")
            scale(0.9)
        }
        assertNotNull(FurnitureRegistry["lamp"])
        assertEquals("item_lamp", d.itemId)
        assertEquals(0.9, d.scale)
    }

    @Test
    fun `footprint DSL starts with anchor cell and appends`() {
        furniture("table") {
            footprint {
                cell(1, 0, 0)
            }
        }
        val d = FurnitureRegistry.require("table")
        assertEquals(2, d.footprint.size)
        assertTrue(d.footprint.cells.contains(FootprintCell(0, 0, 0)))
        assertTrue(d.footprint.cells.contains(FootprintCell(1, 0, 0)))
    }

    @Test
    fun `footprint DSL clear allows rebuilding from scratch`() {
        assertThrows<IllegalArgumentException> {
            furniture("broken") {
                footprint {
                    clear()
                    cell(1, 0, 0)
                }
            }
        }
    }
}
