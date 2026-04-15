package me.nebula.orbit.variant

import kotlin.random.Random

class GameVariantPool(private val variants: List<GameVariant>) {

    fun all(): List<GameVariant> = variants

    fun resolve(id: String): GameVariant? = variants.firstOrNull { it.id == id }

    fun selectRandom(random: Random = Random.Default): GameVariant? {
        val pool = variants.filter { it.random }
        if (pool.isEmpty()) return null
        val totalWeight = pool.sumOf { it.weight.coerceAtLeast(1) }
        if (totalWeight <= 0) return null
        var roll = random.nextInt(totalWeight)
        for (variant in pool) {
            roll -= variant.weight.coerceAtLeast(1)
            if (roll < 0) return variant
        }
        return pool.last()
    }
}
