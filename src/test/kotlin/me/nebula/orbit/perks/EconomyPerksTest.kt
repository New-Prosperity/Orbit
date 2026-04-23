package me.nebula.orbit.perks

import me.nebula.gravity.perks.PerkContribution
import me.nebula.gravity.perks.PerkKey
import me.nebula.gravity.perks.PerkResolver
import me.nebula.gravity.perks.Perks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EconomyPerksTest {

    private val uuid = UUID.randomUUID()

    @Suppress("UNCHECKED_CAST")
    private class StaticContribution(
        override val id: String,
        private val values: Map<String, Any?>,
    ) : PerkContribution {
        override fun <T : Any> valueFor(uuid: UUID, key: PerkKey<T>, modeId: String?): T? =
            values[key.id] as T?
    }

    @AfterEach
    fun tearDown() { PerkResolver.unregisterAll() }

    @Test
    fun `multiplier returns 1 when no contribution`() {
        assertEquals(1.0, EconomyPerks.multiplier(uuid), 1e-9)
    }

    @Test
    fun `multiplier combines contributions multiplicatively`() {
        PerkResolver.register(StaticContribution("rank", mapOf(Perks.COIN_MULTIPLIER.id to 1.2)))
        PerkResolver.register(StaticContribution("booster", mapOf(Perks.COIN_MULTIPLIER.id to 1.5)))
        assertEquals(1.8, EconomyPerks.multiplier(uuid), 1e-9)
    }

    @Test
    fun `grantCoins returns zero for non-positive base amount`() {
        PerkResolver.register(StaticContribution("rank", mapOf(Perks.COIN_MULTIPLIER.id to 2.0)))
        assertEquals(0.0, EconomyPerks.grantCoins(uuid, 0.0), 1e-9)
        assertEquals(0.0, EconomyPerks.grantCoins(uuid, -50.0), 1e-9)
    }

    @Test
    fun `costAfterCosmeticDiscount applies max aggregator then subtracts`() {
        PerkResolver.register(StaticContribution("rank", mapOf(Perks.COSMETIC_DISCOUNT.id to 0.10)))
        PerkResolver.register(StaticContribution("event", mapOf(Perks.COSMETIC_DISCOUNT.id to 0.25)))
        assertEquals(75.0, EconomyPerks.costAfterCosmeticDiscount(uuid, 100.0), 1e-9)
    }

    @Test
    fun `costAfterCosmeticDiscount caps discount at 95 percent`() {
        PerkResolver.register(StaticContribution("hack", mapOf(Perks.COSMETIC_DISCOUNT.id to 9.99)))
        assertEquals(5.0, EconomyPerks.costAfterCosmeticDiscount(uuid, 100.0), 1e-9)
    }

    @Test
    fun `costAfterCosmeticDiscount returns base on zero or negative price`() {
        PerkResolver.register(StaticContribution("rank", mapOf(Perks.COSMETIC_DISCOUNT.id to 0.5)))
        assertEquals(0.0, EconomyPerks.costAfterCosmeticDiscount(uuid, 0.0), 1e-9)
        assertEquals(-10.0, EconomyPerks.costAfterCosmeticDiscount(uuid, -10.0), 1e-9)
    }

    @Test
    fun `int overload truncates to int`() {
        PerkResolver.register(StaticContribution("rank", mapOf(Perks.COSMETIC_DISCOUNT.id to 0.5)))
        assertEquals(50, EconomyPerks.costAfterCosmeticDiscount(uuid, 100))
    }

    @Test
    fun `costAfterShopDiscount reads SHOP_DISCOUNT perk`() {
        PerkResolver.register(StaticContribution("vip", mapOf(Perks.SHOP_DISCOUNT.id to 0.20)))
        assertEquals(80.0, EconomyPerks.costAfterShopDiscount(uuid, 100.0), 1e-9)
    }

    @Test
    fun `partyMaxSize returns max of baseline and perk`() {
        PerkResolver.register(StaticContribution("rank", mapOf(Perks.PARTY_MAX_SIZE.id to 6)))
        assertEquals(6, EconomyPerks.partyMaxSize(uuid, 4))
        assertEquals(8, EconomyPerks.partyMaxSize(uuid, 8))
    }

    @Test
    fun `partyMaxSize uses default 4 when no contribution registered`() {
        assertTrue(EconomyPerks.partyMaxSize(uuid, 3) >= 3)
        assertEquals(4, EconomyPerks.partyMaxSize(uuid, 4))
    }
}
