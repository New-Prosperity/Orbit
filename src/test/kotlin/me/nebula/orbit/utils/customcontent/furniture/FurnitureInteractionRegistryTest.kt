package me.nebula.orbit.utils.customcontent.furniture

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FurnitureInteractionRegistryTest {

    @BeforeEach
    fun setup() { FurnitureInteractionRegistry.clear() }

    @AfterEach
    fun teardown() { FurnitureInteractionRegistry.clear() }

    @Test
    fun `register adds a handler`() {
        FurnitureInteractionRegistry.register("door_open") { _, _ -> }
        assertTrue(FurnitureInteractionRegistry.ids().contains("door_open"))
    }

    @Test
    fun `register rejects duplicate handler id`() {
        FurnitureInteractionRegistry.register("door_open") { _, _ -> }
        assertThrows<IllegalArgumentException> {
            FurnitureInteractionRegistry.register("door_open") { _, _ -> }
        }
    }

    @Test
    fun `register rejects blank handler id`() {
        assertThrows<IllegalArgumentException> {
            FurnitureInteractionRegistry.register("") { _, _ -> }
        }
    }

    @Test
    fun `unregister removes`() {
        FurnitureInteractionRegistry.register("x") { _, _ -> }
        FurnitureInteractionRegistry.unregister("x")
        assertNull(FurnitureInteractionRegistry["x"])
    }

    @Test
    fun `get returns registered handler`() {
        var called = false
        val handler = FurnitureCustomHandler { _, _ -> called = true }
        FurnitureInteractionRegistry.register("mark", handler)
        assertEquals(handler, FurnitureInteractionRegistry["mark"])
    }

    @Test
    fun `clear removes all handlers`() {
        FurnitureInteractionRegistry.register("a") { _, _ -> }
        FurnitureInteractionRegistry.register("b") { _, _ -> }
        FurnitureInteractionRegistry.clear()
        assertTrue(FurnitureInteractionRegistry.ids().isEmpty())
    }
}
