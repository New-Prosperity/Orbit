package me.nebula.orbit.config

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.config.ConfigEntry
import me.nebula.gravity.config.ConfigScope
import me.nebula.gravity.config.ConfigSerializers
import me.nebula.gravity.config.ConfigValueType
import me.nebula.gravity.config.configEntry
import net.minestom.server.item.Material
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigRendererTest {

    private fun sampleEntry(
        key: String = "sample.test",
        scope: ConfigScope = ConfigScope.NETWORK,
        type: ConfigValueType = ConfigValueType.BOOL,
        secret: Boolean = false,
        deprecated: ConfigEntry.DeprecationInfo? = null,
        tags: Set<String> = emptySet(),
    ): ConfigEntry<Boolean> = configEntry(key) {
        this.scope = scope
        this.type = type
        this.serializer = ConfigSerializers.BOOL
        this.category = "testing"
        this.default = false
        this.tags = tags.toMutableSet()
        this.displayNameKey = "test.display".asTranslationKey()
        this.descriptionKey = "test.description".asTranslationKey()
        this.secret = secret
        this.deprecated = deprecated
    }

    @Test
    fun `scopeIcon returns material for each scope`() {
        ConfigScope.entries.forEach { scope ->
            assertNotNull(ConfigRenderer.scopeIcon(scope))
        }
    }

    @Test
    fun `scopeColor returns MiniMessage tag for each scope`() {
        ConfigScope.entries.forEach { scope ->
            val tag = ConfigRenderer.scopeColor(scope)
            assertTrue(tag.startsWith("<"), "Scope color should be a MiniMessage tag, got: $tag")
        }
    }

    @Test
    fun `typeIcon maps all value types`() {
        ConfigValueType.entries.forEach { type ->
            assertNotNull(ConfigRenderer.typeIcon(type))
        }
    }

    @Test
    fun `render returns default raw and not tuned when no value set`() {
        val entry = sampleEntry()
        val rendered = ConfigRenderer.render(entry, null)
        assertEquals("sample.test", rendered.entryKey)
        assertFalse(rendered.isTuned)
        assertFalse(rendered.isSecret)
        assertFalse(rendered.isDeprecated)
    }

    @Test
    fun `render marks secret`() {
        val entry = sampleEntry(secret = true)
        val rendered = ConfigRenderer.render(entry, null)
        assertTrue(rendered.isSecret)
    }

    @Test
    fun `render marks deprecated`() {
        val entry = sampleEntry(
            deprecated = ConfigEntry.DeprecationInfo(replacementKey = "new.key")
        )
        val rendered = ConfigRenderer.render(entry, null)
        assertTrue(rendered.isDeprecated)
    }

    @Test
    fun `render marks computed from tags`() {
        val entry = sampleEntry(tags = setOf("computed"))
        val rendered = ConfigRenderer.render(entry, null)
        assertTrue(rendered.isComputed)
    }

    @Test
    fun `render marks computed from type`() {
        val entry = sampleEntry(type = ConfigValueType.COMPUTED)
        val rendered = ConfigRenderer.render(entry, null)
        assertTrue(rendered.isComputed)
    }

    @Test
    fun `redactIfSecret returns stars when secret`() {
        val entry = sampleEntry(secret = true)
        assertEquals("***", ConfigRenderer.redactIfSecret(entry, "actual"))
    }

    @Test
    fun `redactIfSecret returns raw when not secret`() {
        val entry = sampleEntry(secret = false)
        assertEquals("true", ConfigRenderer.redactIfSecret(entry, "true"))
    }

    @Test
    fun `redactIfSecret returns default literal when raw is null`() {
        val entry = sampleEntry(secret = false)
        assertEquals("(default)", ConfigRenderer.redactIfSecret(entry, null))
    }

    @Test
    fun `entryItem builds an ItemStack with expected material`() {
        val entry = sampleEntry(type = ConfigValueType.BOOL)
        val item = ConfigRenderer.entryItem(entry)
        assertEquals(Material.LEVER, item.material())
    }

    @Test
    fun `scopeItem renders entry count`() {
        val item = ConfigRenderer.scopeItem(ConfigScope.NETWORK, 5)
        assertEquals(Material.BEACON, item.material())
    }

    @Test
    fun `deprecationBanner returns null for non-deprecated`() {
        val entry = sampleEntry()
        assertNull(ConfigRenderer.deprecationBanner(entry))
    }

    @Test
    fun `deprecationBanner returns barrier for deprecated`() {
        val entry = sampleEntry(deprecated = ConfigEntry.DeprecationInfo(replacementKey = "x"))
        val banner = ConfigRenderer.deprecationBanner(entry)
        assertNotNull(banner)
        assertEquals(Material.BARRIER, banner.material())
    }

    @Test
    fun `isEditableType returns true for basic types`() {
        assertTrue(ConfigRenderer.isEditableType(ConfigValueType.BOOL))
        assertTrue(ConfigRenderer.isEditableType(ConfigValueType.INT))
        assertTrue(ConfigRenderer.isEditableType(ConfigValueType.LONG))
        assertTrue(ConfigRenderer.isEditableType(ConfigValueType.DOUBLE))
        assertTrue(ConfigRenderer.isEditableType(ConfigValueType.STRING))
        assertTrue(ConfigRenderer.isEditableType(ConfigValueType.ENUM))
        assertTrue(ConfigRenderer.isEditableType(ConfigValueType.DURATION))
        assertTrue(ConfigRenderer.isEditableType(ConfigValueType.MATERIAL))
        assertTrue(ConfigRenderer.isEditableType(ConfigValueType.LOCALE))
    }

    @Test
    fun `isEditableType returns false for complex types`() {
        assertFalse(ConfigRenderer.isEditableType(ConfigValueType.WEIGHTED_TABLE))
        assertFalse(ConfigRenderer.isEditableType(ConfigValueType.LIST))
        assertFalse(ConfigRenderer.isEditableType(ConfigValueType.MAP))
        assertFalse(ConfigRenderer.isEditableType(ConfigValueType.COMPOSITE))
        assertFalse(ConfigRenderer.isEditableType(ConfigValueType.COMPUTED))
    }

    @Test
    fun `groupByCategory groups entries correctly`() {
        val a = sampleEntry("a.one")
        val b = sampleEntry("b.one")
        val c = sampleEntry("c.one")
        val entries: List<ConfigEntry<*>> = listOf(a, b, c)
        val grouped = ConfigRenderer.groupByCategory(entries)
        assertEquals(1, grouped.size)
        assertEquals(3, grouped["testing"]?.size)
    }

    @Test
    fun `defaultEditableScopes excludes secrets`() {
        val scopes = ConfigRenderer.defaultEditableScopes()
        assertFalse(ConfigScope.SECRETS in scopes)
        assertTrue(ConfigScope.NETWORK in scopes)
        assertTrue(ConfigScope.PLAYER in scopes)
    }
}
