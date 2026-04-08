package me.nebula.orbit.utils.knockback

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KnockbackProfileTest {

    @Test
    fun `default profile values`() {
        val profile = KnockbackProfile("test")
        assertEquals(0.4, profile.horizontal)
        assertEquals(0.4, profile.vertical)
        assertEquals(0.0, profile.extraHorizontal)
        assertEquals(0.0, profile.extraVertical)
        assertEquals(1.0, profile.friction)
    }

    @Test
    fun `builder DSL produces profile with overrides`() {
        val profile = knockbackProfile("hyper") {
            horizontal = 0.8
            vertical = 0.6
            extraHorizontal = 0.2
            extraVertical = 0.1
            friction = 0.95
        }
        assertEquals(0.8, profile.horizontal)
        assertEquals(0.6, profile.vertical)
        assertEquals(0.2, profile.extraHorizontal)
        assertEquals(0.1, profile.extraVertical)
        assertEquals(0.95, profile.friction)
        assertEquals("hyper", profile.name)
    }

    @Test
    fun `builder defaults match constructor defaults`() {
        val builderProfile = knockbackProfile("dsl") {}
        val constructorProfile = KnockbackProfile("dsl")
        assertEquals(constructorProfile.horizontal, builderProfile.horizontal)
        assertEquals(constructorProfile.vertical, builderProfile.vertical)
        assertEquals(constructorProfile.extraHorizontal, builderProfile.extraHorizontal)
        assertEquals(constructorProfile.extraVertical, builderProfile.extraVertical)
        assertEquals(constructorProfile.friction, builderProfile.friction)
    }

    @Test
    fun `KnockbackManager has DEFAULT profile registered`() {
        assertNotNull(KnockbackManager.DEFAULT)
        assertEquals("default", KnockbackManager.DEFAULT.name)
        assertNotNull(KnockbackManager.get("default"))
    }

    @Test
    fun `KnockbackManager register stores profile`() {
        val profile = knockbackProfile("custom-test-${System.nanoTime()}") { horizontal = 0.7 }
        KnockbackManager.register(profile)
        assertEquals(profile, KnockbackManager.get(profile.name))
    }

    @Test
    fun `KnockbackManager get returns null for unknown`() {
        assertNull(KnockbackManager.get("nonexistent-profile-xyz"))
    }

    @Test
    fun `data class equality`() {
        val a = KnockbackProfile("p", horizontal = 0.5, vertical = 0.6)
        val b = KnockbackProfile("p", horizontal = 0.5, vertical = 0.6)
        assertEquals(a, b)
    }
}
