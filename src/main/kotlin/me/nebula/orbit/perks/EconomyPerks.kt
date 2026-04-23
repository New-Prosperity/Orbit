package me.nebula.orbit.perks

import me.nebula.gravity.economy.AddBalanceProcessor
import me.nebula.gravity.economy.EconomyStore
import me.nebula.gravity.perks.PerkKey
import me.nebula.gravity.perks.PerkResolver
import me.nebula.gravity.perks.Perks
import java.util.UUID

private const val MAX_DISCOUNT = 0.95

object EconomyPerks {

    fun multiplier(uuid: UUID): Double =
        PerkResolver.resolve(uuid, Perks.COIN_MULTIPLIER)

    fun grantCoins(uuid: UUID, baseAmount: Double): Double {
        if (baseAmount <= 0.0) return 0.0
        val adjusted = baseAmount * multiplier(uuid)
        EconomyStore.executeOnKey(uuid, AddBalanceProcessor("coins", adjusted))
        return adjusted
    }

    fun grantCoinsLong(uuid: UUID, baseAmount: Long): Long =
        grantCoins(uuid, baseAmount.toDouble()).toLong()

    fun costAfterCosmeticDiscount(uuid: UUID, basePrice: Double): Double =
        discount(uuid, basePrice, Perks.COSMETIC_DISCOUNT)

    fun costAfterCosmeticDiscount(uuid: UUID, basePrice: Int): Int =
        costAfterCosmeticDiscount(uuid, basePrice.toDouble()).toInt()

    fun costAfterShopDiscount(uuid: UUID, basePrice: Double): Double =
        discount(uuid, basePrice, Perks.SHOP_DISCOUNT)

    internal fun discount(uuid: UUID, basePrice: Double, key: PerkKey<Double>): Double {
        if (basePrice <= 0.0) return basePrice
        val discount = PerkResolver.resolve(uuid, key).coerceIn(0.0, MAX_DISCOUNT)
        return basePrice * (1.0 - discount)
    }

    fun partyMaxSize(uuid: UUID, baseMax: Int): Int {
        val perkValue = PerkResolver.resolve(uuid, Perks.PARTY_MAX_SIZE)
        return maxOf(baseMax, perkValue)
    }
}
