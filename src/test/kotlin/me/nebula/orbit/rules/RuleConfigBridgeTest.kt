package me.nebula.orbit.rules

import me.nebula.ether.Ether
import me.nebula.gravity.config.ConfigCatalog
import me.nebula.gravity.config.ConfigScope
import me.nebula.gravity.config.ConfigStore
import me.nebula.gravity.config.ConfigValueType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleConfigBridgeTest {

    private var originalOffline: Boolean = false

    @BeforeEach
    fun setUp() {
        originalOffline = Ether.offline
        Ether.offline = true
        ConfigCatalog.clear()
        ConfigStore.installForTest()
    }

    @AfterEach
    fun tearDown() {
        ConfigCatalog.clear()
        ConfigStore.clear()
        Ether.offline = originalOffline
    }

    @Test
    fun `instance scoped rule does not produce a config entry`() {
        val key = RuleKey("bridge_instance", default = false, scope = RuleScope.INSTANCE)
        assertNull(RuleConfigBridge.asConfigEntry(key))
    }

    @Test
    fun `gamemode scoped bool rule produces GAME_MODE config entry`() {
        val key = RuleKey("bridge_gm_bool", default = true, scope = RuleScope.GAMEMODE)
        val entry = assertNotNull(RuleConfigBridge.asConfigEntry(key))
        assertEquals(ConfigScope.GAME_MODE, entry.scope)
        assertEquals(ConfigValueType.BOOL, entry.type)
        assertEquals(true, entry.default)
        assertEquals("rule.bridge_gm_bool", entry.key)
    }

    @Test
    fun `network scoped rule produces NETWORK config entry`() {
        val key = RuleKey("bridge_net", default = 42, scope = RuleScope.NETWORK)
        val entry = assertNotNull(RuleConfigBridge.asConfigEntry(key))
        assertEquals(ConfigScope.NETWORK, entry.scope)
        assertEquals(ConfigValueType.INT, entry.type)
        assertEquals(42, entry.default)
    }

    @Test
    fun `bridge caches entries`() {
        val key = RuleKey("bridge_cached", default = 1.0, scope = RuleScope.GAMEMODE)
        val first = RuleConfigBridge.asConfigEntry(key)
        val second = RuleConfigBridge.asConfigEntry(key)
        assertTrue(first === second)
    }

    @Test
    fun `bridge supports int long double string bool`() {
        val keys = listOf(
            RuleKey("b_bool", default = false, scope = RuleScope.GAMEMODE),
            RuleKey("b_int", default = 0, scope = RuleScope.GAMEMODE),
            RuleKey("b_long", default = 0L, scope = RuleScope.GAMEMODE),
            RuleKey("b_double", default = 0.0, scope = RuleScope.GAMEMODE),
            RuleKey("b_string", default = "", scope = RuleScope.GAMEMODE),
        )
        for (k in keys) assertNotNull(RuleConfigBridge.asConfigEntry(k))
    }

    @Test
    fun `resolveDefault returns rule default when no config override`() {
        val key = RuleKey("resolve_none", default = true, scope = RuleScope.GAMEMODE)
        RuleConfigBridge.asConfigEntry(key)
        val resolved = RuleConfigBridge.resolveDefault(key, "battleroyale")
        assertEquals(true, resolved)
    }

    @Test
    fun `resolveDefault picks up ConfigStore override`() {
        val key = RuleKey("resolve_override", default = false, scope = RuleScope.GAMEMODE)
        val entry = RuleConfigBridge.asConfigEntry(key)
        assertNotNull(entry)
        ConfigStore.set(entry, true, "battleroyale")
        val resolvedBr = RuleConfigBridge.resolveDefault(key, "battleroyale")
        assertEquals(true, resolvedBr)
        val resolvedHg = RuleConfigBridge.resolveDefault(key, "hungergames")
        assertEquals(false, resolvedHg)
    }

    @Test
    fun `RuleRegistry register also registers catalog entry for bridgeable rules`() {
        ConfigCatalog.clear()
        val key = RuleRegistry.register("auto_bridge_test", default = false, scope = RuleScope.GAMEMODE)
        val bridgeEntry = RuleConfigBridge.asConfigEntry(key)
        assertNotNull(bridgeEntry)
        val fromCatalog = ConfigCatalog["rule.auto_bridge_test"]
        assertNotNull(fromCatalog)
    }

    @Test
    fun `RuleRegistry register skips catalog for INSTANCE scope`() {
        ConfigCatalog.clear()
        val key = RuleRegistry.register("instance_only", default = false, scope = RuleScope.INSTANCE)
        assertNull(RuleConfigBridge.asConfigEntry(key))
        assertNull(ConfigCatalog["rule.instance_only"])
    }

    @Test
    fun `GameRules hydrateDefaultsFor loads gamemode overrides`() {
        val key = RuleRegistry.register("hydrate_gm", default = false, scope = RuleScope.GAMEMODE)
        val entry = RuleConfigBridge.asConfigEntry(key)
        assertNotNull(entry)
        ConfigStore.set(entry, true, "battleroyale")

        val rules = GameRules()
        assertFalse(rules[key])
        rules.hydrateDefaultsFor("battleroyale")
        assertTrue(rules[key])
    }

    @Test
    fun `GameRules hydrateDefaultsFor keeps rule-default when config unset`() {
        val key = RuleRegistry.register("hydrate_default", default = true, scope = RuleScope.GAMEMODE)
        RuleConfigBridge.asConfigEntry(key)

        val rules = GameRules()
        rules.hydrateDefaultsFor("battleroyale")
        assertTrue(rules[key])
    }

    @Test
    fun `GameRules hydrateDefaultsFor fires onChange for overridden values`() {
        val key = RuleRegistry.register("hydrate_fires", default = false, scope = RuleScope.GAMEMODE)
        val entry = RuleConfigBridge.asConfigEntry(key)
        assertNotNull(entry)
        ConfigStore.set(entry, true, "battleroyale")

        val rules = GameRules()
        var fired = false
        rules.onChange(key) { _, new -> if (new == true) fired = true }
        rules.hydrateDefaultsFor("battleroyale")
        assertTrue(fired)
    }

    @Test
    fun `GameRules hydrateDefaultsFor does not affect INSTANCE scoped rules`() {
        val key = RuleRegistry.register("hydrate_inst", default = true, scope = RuleScope.INSTANCE)
        val rules = GameRules()
        rules[key] = false
        rules.hydrateDefaultsFor("battleroyale")
        assertFalse(rules[key])
    }

    @Test
    fun `different gamemode qualifiers produce isolated overrides`() {
        val key = RuleRegistry.register("iso_gm", default = 1.0, scope = RuleScope.GAMEMODE)
        val entry = RuleConfigBridge.asConfigEntry(key)
        assertNotNull(entry)
        ConfigStore.set(entry, 2.5, "battleroyale")
        ConfigStore.set(entry, 0.5, "hungergames")

        val brRules = GameRules().apply { hydrateDefaultsFor("battleroyale") }
        val hgRules = GameRules().apply { hydrateDefaultsFor("hungergames") }
        assertEquals(2.5, brRules[key])
        assertEquals(0.5, hgRules[key])
    }
}
