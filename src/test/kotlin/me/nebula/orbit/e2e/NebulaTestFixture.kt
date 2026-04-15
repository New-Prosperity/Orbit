package me.nebula.orbit.e2e

import com.hazelcast.config.Config
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance
import me.nebula.ether.Ether
import me.nebula.ether.utils.hazelcast.Store
import me.nebula.ether.utils.translation.TranslationRegistry
import me.nebula.ether.utils.translation.translationRegistry
import me.nebula.orbit.Orbit
import java.util.UUID

/**
 * Boots an embedded single-node Hazelcast member in the test JVM, wires
 * [Ether.hazelcast], and optionally populates [Orbit.translations] with
 * provided key/value entries. Designed for integration tests that need
 * real Hazelcast semantics (replicated-map-backed translations, entry
 * processors, distributed locks) that the in-memory offline backend
 * does not emulate.
 *
 * Typical lifecycle:
 * ```
 * @BeforeAll fun setUp() { NebulaTestFixture.boot() ; NebulaTestFixture.installTranslations(mapOf(...)) }
 * @AfterAll fun tearDown() { NebulaTestFixture.shutdown() }
 * ```
 *
 * Stores are registered via [NebulaTestFixture.registerStore] which
 * delegates to [Store.installHazelcastBackendForTest].
 */
object NebulaTestFixture {

    @Volatile private var hazelcast: HazelcastInstance? = null

    /**
     * Boots a standalone Hazelcast member with multicast + TCP discovery disabled,
     * a random cluster name, and a random auto-assigned network port. Idempotent.
     */
    fun boot(): HazelcastInstance {
        hazelcast?.let { return it }
        synchronized(this) {
            hazelcast?.let { return it }
            System.setProperty("hazelcast.logging.type", "none")
            System.setProperty("hazelcast.phone.home.enabled", "false")
            System.setProperty("hazelcast.shutdownhook.enabled", "false")
            val license = checkNotNull(System.getenv("HAZELCAST_LICENSE")?.ifBlank { null }) {
                "NebulaTestFixture requires HAZELCAST_LICENSE env var — Ether bundles hazelcast-enterprise. Gate integration tests on isAvailable()."
            }
            val config = Config().apply {
                clusterName = "nebula-test-${UUID.randomUUID()}"
                licenseKey = license
                networkConfig.join.multicastConfig.isEnabled = false
                networkConfig.join.tcpIpConfig.isEnabled = false
                networkConfig.setPortAutoIncrement(true)
            }
            val instance = Hazelcast.newHazelcastInstance(config)
            Ether.hazelcast = instance
            hazelcast = instance
            return instance
        }
    }

    /**
     * True when a Hazelcast Enterprise license is available in the environment, which is
     * required to boot the embedded member. Integration tests should gate on this via
     * `org.junit.jupiter.api.Assumptions.assumeTrue(NebulaTestFixture.isAvailable())` so
     * they skip cleanly on hosts without a license.
     */
    fun isAvailable(): Boolean =
        !System.getenv("HAZELCAST_LICENSE").isNullOrBlank()

    /** Shuts down the Hazelcast member. Subsequent [boot] calls will create a fresh instance. */
    fun shutdown() {
        synchronized(this) {
            hazelcast?.shutdown()
            hazelcast = null
        }
    }

    /**
     * Registers a [Store] against the booted Hazelcast instance. Must be called after [boot].
     * Use to make Gravity/Orbit stores available for save/load in tests.
     */
    fun registerStore(store: Store<*, *>) {
        val instance = checkNotNull(hazelcast) { "Call NebulaTestFixture.boot() before registerStore" }
        store.installHazelcastBackendForTest(instance)
    }

    /**
     * Builds a [TranslationRegistry] backed by the fixture's Hazelcast instance, assigns it to
     * [Orbit.translations], and populates it with [entries] under the default locale.
     * [locale] defaults to `"en"`. Must be called after [boot].
     */
    fun installTranslations(entries: Map<String, String>, locale: String = "en") {
        checkNotNull(hazelcast) { "Call NebulaTestFixture.boot() before installTranslations" }
        val registry = translationRegistry { defaultLocale(locale) }
        Orbit.translations = registry
        if (entries.isNotEmpty()) registry.putAll(locale, entries)
    }
}
