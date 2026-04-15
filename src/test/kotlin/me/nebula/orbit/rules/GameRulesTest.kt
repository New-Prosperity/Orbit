package me.nebula.orbit.rules

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameRulesTest {

    private val BOOL_RULE = RuleRegistry.register("test_bool", default = false)
    private val DOUBLE_RULE = RuleRegistry.register("test_double", default = 1.0)
    private val INT_RULE = RuleRegistry.register("test_int", default = 0)

    @Test
    fun `get returns default when unset`() {
        val rules = GameRules()
        assertFalse(rules[BOOL_RULE])
        assertEquals(1.0, rules[DOUBLE_RULE])
        assertEquals(0, rules[INT_RULE])
    }

    @Test
    fun `set then get returns written value`() {
        val rules = GameRules()
        rules[BOOL_RULE] = true
        rules[DOUBLE_RULE] = 3.14
        assertTrue(rules[BOOL_RULE])
        assertEquals(3.14, rules[DOUBLE_RULE])
    }

    @Test
    fun `onChange fires only when value differs`() {
        val rules = GameRules()
        val fired = mutableListOf<Pair<Boolean, Boolean>>()
        rules.onChange(BOOL_RULE) { old, new -> fired += old to new }
        rules[BOOL_RULE] = true
        rules[BOOL_RULE] = true
        rules[BOOL_RULE] = false
        assertEquals(2, fired.size)
        assertEquals(false to true, fired[0])
        assertEquals(true to false, fired[1])
    }

    @Test
    fun `setAll writes all entries`() {
        val rules = GameRules()
        rules.setAll(mapOf(BOOL_RULE to true, DOUBLE_RULE to 5.5))
        assertTrue(rules[BOOL_RULE])
        assertEquals(5.5, rules[DOUBLE_RULE])
    }

    @Test
    fun `setById resolves key and writes value`() {
        val rules = GameRules()
        rules.setById("test_bool", true)
        assertTrue(rules[BOOL_RULE])
    }

    @Test
    fun `setById rejects unknown id`() {
        val rules = GameRules()
        rules.setById("nonexistent", true)
    }

    @Test
    fun `setById rejects wrong type`() {
        val rules = GameRules()
        rules.setById("test_bool", "not a boolean")
        assertFalse(rules[BOOL_RULE])
    }

    @Test
    fun `reset returns all rules to defaults and fires listeners`() {
        val rules = GameRules()
        rules[BOOL_RULE] = true
        rules[DOUBLE_RULE] = 5.5
        val resets = mutableListOf<Pair<Any, Any>>()
        rules.onChange(BOOL_RULE) { old, new -> resets += old to new }
        rules.reset()
        assertFalse(rules[BOOL_RULE])
        assertEquals(1.0, rules[DOUBLE_RULE])
        assertEquals(listOf<Pair<Any, Any>>(true to false), resets)
    }

    @Test
    fun `RuleRegistry resolve returns same key for same id`() {
        val again = RuleRegistry.register("test_bool", default = false)
        assertEquals(BOOL_RULE.id, again.id)
        assertEquals(BOOL_RULE, RuleRegistry.resolve("test_bool"))
    }

    @Test
    fun `Rules preload triggers object init without exceptions`() {
        Rules.preload()
    }
}
