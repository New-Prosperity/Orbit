package me.nebula.orbit.e2e

import me.nebula.ether.utils.hazelcast.Store
import me.nebula.orbit.Orbit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.Serializable
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private data class FixtureProbe(val tag: String, val count: Int) : Serializable

private object FixtureProbeStore : Store<UUID, FixtureProbe>(name = "fixture-probe")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NebulaTestFixtureTest {

    @BeforeAll
    fun setUp() {
        Assumptions.assumeTrue(
            NebulaTestFixture.isAvailable(),
            "HAZELCAST_LICENSE env var is required to boot an embedded Hazelcast Enterprise member; skipping.",
        )
        NebulaTestFixture.boot()
        NebulaTestFixture.registerStore(FixtureProbeStore)
        NebulaTestFixture.installTranslations(mapOf(
            "orbit.test.hello" to "hello, world",
            "orbit.test.formatted" to "value is {n}",
        ))
    }

    @AfterAll
    fun tearDown() {
        if (NebulaTestFixture.isAvailable()) NebulaTestFixture.shutdown()
    }

    @Test
    fun `boot yields a live Hazelcast instance`() {
        val instance = NebulaTestFixture.boot()
        assertTrue(instance.lifecycleService.isRunning, "Hazelcast instance should be running")
    }

    @Test
    fun `registered store supports save and load against real Hazelcast`() {
        val key = UUID.randomUUID()
        val entry = FixtureProbe(tag = "roundtrip", count = 42)

        FixtureProbeStore.save(key, entry)

        val loaded = assertNotNull(FixtureProbeStore.load(key))
        assertEquals(entry, loaded)
    }

    @Test
    fun `translations lookup resolves against the installed registry`() {
        val registry = Orbit.translations
        assertEquals("hello, world", registry.require("orbit.test.hello"))
    }

    @Test
    fun `translations formatting replaces placeholders`() {
        val formatted = Orbit.translations.requireFormat("orbit.test.formatted", "n" to 7)
        assertEquals("value is 7", formatted)
    }

    @Test
    fun `boot is idempotent`() {
        val a = NebulaTestFixture.boot()
        val b = NebulaTestFixture.boot()
        assertEquals(a, b, "repeated boot should return the same instance")
    }
}
