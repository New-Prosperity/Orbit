package me.nebula.orbit.rules

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuleUiWatcherTest {

    @Test
    fun `PVP_ENABLED true maps to pvp_on`() {
        val msg = describeRuleChange(Rules.PVP_ENABLED.id, true)
        assertEquals("orbit.rule.pvp_on", msg?.key?.value)
    }

    @Test
    fun `PVP_ENABLED false maps to pvp_off`() {
        val msg = describeRuleChange(Rules.PVP_ENABLED.id, false)
        assertEquals("orbit.rule.pvp_off", msg?.key?.value)
    }

    @Test
    fun `DAMAGE_ENABLED flips to matching key`() {
        assertEquals("orbit.rule.damage_on", describeRuleChange(Rules.DAMAGE_ENABLED.id, true)?.key?.value)
        assertEquals("orbit.rule.damage_off", describeRuleChange(Rules.DAMAGE_ENABLED.id, false)?.key?.value)
    }

    @Test
    fun `ZONE_SHRINKING flips to matching key`() {
        assertEquals("orbit.rule.zone_on", describeRuleChange(Rules.ZONE_SHRINKING.id, true)?.key?.value)
        assertEquals("orbit.rule.zone_off", describeRuleChange(Rules.ZONE_SHRINKING.id, false)?.key?.value)
    }

    @Test
    fun `unmapped rules return null`() {
        assertNull(describeRuleChange(Rules.XP_MULTIPLIER.id, 2.0))
        assertNull(describeRuleChange(Rules.LOOT_QUANTITY_MULTIPLIER.id, 1.5))
        assertNull(describeRuleChange("unknown_rule_id", true))
    }

    @Test
    fun `non-boolean values for boolean rules default to off branch`() {
        val msg = describeRuleChange(Rules.PVP_ENABLED.id, "truthy")
        assertEquals("orbit.rule.pvp_off", msg?.key?.value,
            "comparison to true is identity-based — any non-true value must fall to the off branch")
    }
}
