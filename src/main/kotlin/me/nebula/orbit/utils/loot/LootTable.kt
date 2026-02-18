package me.nebula.orbit.utils.loot

import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import kotlin.random.Random

data class LootEntry(
    val item: ItemStack,
    val weight: Int = 1,
    val minCount: Int = 1,
    val maxCount: Int = 1,
) {

    fun roll(): ItemStack {
        val count = if (minCount == maxCount) minCount else Random.nextInt(minCount, maxCount + 1)
        return item.withAmount(count)
    }
}

class LootTable(
    val name: String,
    private val entries: List<LootEntry>,
    private val rolls: IntRange = 1..1,
) {

    private val totalWeight = entries.sumOf { it.weight }

    fun roll(): List<ItemStack> {
        if (entries.isEmpty()) return emptyList()
        val rollCount = if (rolls.first == rolls.last) rolls.first else Random.nextInt(rolls.first, rolls.last + 1)
        return (1..rollCount).map { rollOnce() }
    }

    fun rollSingle(): ItemStack? {
        if (entries.isEmpty()) return null
        return rollOnce()
    }

    private fun rollOnce(): ItemStack {
        var remaining = Random.nextInt(totalWeight)
        for (entry in entries) {
            remaining -= entry.weight
            if (remaining < 0) return entry.roll()
        }
        return entries.last().roll()
    }
}

class LootTableBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal val entries = mutableListOf<LootEntry>()
    @PublishedApi internal var rolls = 1..1

    fun entry(item: ItemStack, weight: Int = 1, minCount: Int = 1, maxCount: Int = 1) {
        entries.add(LootEntry(item, weight, minCount, maxCount))
    }

    fun entry(material: Material, weight: Int = 1, minCount: Int = 1, maxCount: Int = 1) {
        entries.add(LootEntry(ItemStack.of(material), weight, minCount, maxCount))
    }

    fun rolls(count: Int) { rolls = count..count }
    fun rolls(range: IntRange) { rolls = range }

    @PublishedApi internal fun build(): LootTable = LootTable(name, entries.toList(), rolls)
}

inline fun lootTable(name: String, block: LootTableBuilder.() -> Unit): LootTable =
    LootTableBuilder(name).apply(block).build()
