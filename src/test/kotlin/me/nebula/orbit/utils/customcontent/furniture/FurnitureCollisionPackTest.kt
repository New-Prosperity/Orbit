package me.nebula.orbit.utils.customcontent.furniture

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class FurnitureCollisionPackTest {

    @BeforeEach
    fun setup() { FurnitureCollisionStates.clear() }

    @AfterEach
    fun teardown() { FurnitureCollisionStates.clear() }

    @Test
    fun `generate returns empty when no allocations`() {
        val entries = FurnitureCollisionPack.generate()
        assertTrue(entries.isEmpty(), "expected empty pack but got ${entries.keys}")
    }
}
