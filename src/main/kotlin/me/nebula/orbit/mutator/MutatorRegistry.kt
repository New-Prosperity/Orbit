package me.nebula.orbit.mutator

import me.nebula.ether.utils.logging.logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object MutatorRegistry {

    private val logger = logger("MutatorRegistry")
    private val registry = ConcurrentHashMap<String, Mutator>()

    fun register(mutator: Mutator) {
        registry[mutator.id] = mutator
    }

    operator fun get(id: String): Mutator? = registry[id]

    fun all(): Collection<Mutator> = registry.values

    fun randomPool(): List<Mutator> = registry.values.filter { it.random }

    fun resolve(ids: List<String>): List<Mutator> =
        ids.mapNotNull { id ->
            registry[id] ?: run {
                logger.warn { "Unknown mutator: $id" }
                null
            }
        }

    fun selectRandom(count: Int): List<Mutator> {
        val pool = randomPool()
        if (pool.isEmpty() || count <= 0) return emptyList()

        val selected = mutableListOf<Mutator>()
        val remaining = pool.toMutableList()
        val usedGroups = mutableSetOf<String>()

        repeat(count.coerceAtMost(remaining.size)) {
            val eligible = remaining.filter { m ->
                m.conflictGroup == null || m.conflictGroup !in usedGroups
            }
            if (eligible.isEmpty()) return@repeat
            val currentWeight = eligible.sumOf { it.weight }
            if (currentWeight <= 0) return@repeat
            val pick = weightedRandom(eligible, currentWeight)
            selected += pick
            remaining -= pick
            pick.conflictGroup?.let { usedGroups += it }
        }
        return selected
    }

    fun loadDefinitions() {
        MutatorDefinitions.ALL.forEach { register(it) }
        logger.info { "Loaded ${registry.size} mutators (${randomPool().size} random)" }
    }

    private fun weightedRandom(pool: List<Mutator>, totalWeight: Int): Mutator {
        var roll = Random.nextInt(totalWeight)
        for (mutator in pool) {
            roll -= mutator.weight
            if (roll < 0) return mutator
        }
        return pool.last()
    }
}
