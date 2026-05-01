package me.nebula.orbit.utils.entitybuilder.file

import com.google.gson.JsonObject
import me.nebula.orbit.utils.entitybuilder.BehaviorEvaluator
import me.nebula.orbit.utils.entitybuilder.MemoryKey
import me.nebula.orbit.utils.entitybuilder.MemoryKeys
import me.nebula.orbit.utils.entitybuilder.isOnCooldown
import net.minestom.server.entity.Entity
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

fun interface EvaluatorFactory {
    fun create(json: JsonObject): BehaviorEvaluator
}

object EvaluatorFactories {
    private val factories = ConcurrentHashMap<String, EvaluatorFactory>()

    fun register(type: String, factory: EvaluatorFactory) {
        factories[type] = factory
    }

    fun create(json: JsonObject): BehaviorEvaluator {
        val type = json.requireString("type")
        val factory = factories[type] ?: error("Unknown evaluator type: $type")
        return factory.create(json)
    }

    fun types(): Set<String> = factories.keys.toSet()

    init {
        register("always") { _ -> BehaviorEvaluator { true } }
        register("never") { _ -> BehaviorEvaluator { false } }

        register("has") { obj ->
            val key = obj.requireString("key")
            BehaviorEvaluator { it.memory.hasName(key) }
        }

        register("not") { obj ->
            val inner = create(obj.requireObj("of"))
            BehaviorEvaluator { !inner.evaluate(it) }
        }

        register("all") { obj ->
            val list = obj.array("of")?.map { create(it.asObjectOrError()) } ?: emptyList()
            BehaviorEvaluator { e -> list.all { it.evaluate(e) } }
        }

        register("any") { obj ->
            val list = obj.array("of")?.map { create(it.asObjectOrError()) } ?: emptyList()
            BehaviorEvaluator { e -> list.any { it.evaluate(e) } }
        }

        register("phase") { obj ->
            val target = obj.int("value", 0)
            BehaviorEvaluator { (it.memory.get(MemoryKeys.PHASE) ?: 0) == target }
        }

        register("phase_at_least") { obj ->
            val target = obj.int("value", 0)
            BehaviorEvaluator { (it.memory.get(MemoryKeys.PHASE) ?: 0) >= target }
        }

        register("hp_below") { obj ->
            val pct = obj.float("value", 0.5f)
            BehaviorEvaluator { it.healthPercent() < pct }
        }

        register("hp_above") { obj ->
            val pct = obj.float("value", 0.5f)
            BehaviorEvaluator { it.healthPercent() > pct }
        }

        register("random") { obj ->
            val chance = obj.float("chance", 0.5f).coerceIn(0f, 1f)
            BehaviorEvaluator { Random.nextFloat() < chance }
        }

        register("compare_int") { obj ->
            val keyName = obj.requireString("key")
            val op = obj.string("op", ">=").trim()
            val value = obj.int("value", 0)
            val key = MemoryKeys.byName(keyName) ?: error("Unknown memory key: $keyName")
            val cmp = compareIntOp(op)
            BehaviorEvaluator { entity ->
                @Suppress("UNCHECKED_CAST")
                val v = (entity.memory.get(key as MemoryKey<Int>) ?: 0)
                cmp(v, value)
            }
        }

        register("distance_below") { obj ->
            val keyName = obj.string("target_memory_key", "attack_target")
            val threshold = obj.double("value", 8.0)
            val sq = threshold * threshold
            BehaviorEvaluator { entity ->
                @Suppress("UNCHECKED_CAST")
                val key = MemoryKeys.byName(keyName) as? MemoryKey<Entity> ?: return@BehaviorEvaluator false
                val target = entity.memory.get(key) ?: return@BehaviorEvaluator false
                if (target.isRemoved) return@BehaviorEvaluator false
                entity.position.distanceSquared(target.position) < sq
            }
        }

        register("distance_above") { obj ->
            val keyName = obj.string("target_memory_key", "owner")
            val threshold = obj.double("value", 8.0)
            val sq = threshold * threshold
            BehaviorEvaluator { entity ->
                @Suppress("UNCHECKED_CAST")
                val key = MemoryKeys.byName(keyName) as? MemoryKey<Entity> ?: return@BehaviorEvaluator false
                val target = entity.memory.get(key) ?: return@BehaviorEvaluator false
                if (target.isRemoved) return@BehaviorEvaluator false
                entity.position.distanceSquared(target.position) > sq
            }
        }

        register("time_since_below") { obj ->
            val keyName = obj.requireString("time_key")
            val maxMs = obj.long("max_ms", 5000L)
            val key = MemoryKeys.byName(keyName) ?: error("Unknown memory key: $keyName")
            BehaviorEvaluator { entity ->
                @Suppress("UNCHECKED_CAST")
                val ts = entity.memory.get(key as MemoryKey<Long>) ?: return@BehaviorEvaluator false
                (System.currentTimeMillis() - ts) < maxMs
            }
        }

        register("cooldown_ready") { obj ->
            val name = obj.requireString("name")
            BehaviorEvaluator { entity -> !entity.isOnCooldown(name) }
        }

        register("in_combat") { _ ->
            BehaviorEvaluator { it.inCombat }
        }
    }

    private fun compareIntOp(op: String): (Int, Int) -> Boolean = when (op) {
        ">" -> { a, b -> a > b }
        "<" -> { a, b -> a < b }
        ">=" -> { a, b -> a >= b }
        "<=" -> { a, b -> a <= b }
        "==", "=" -> { a, b -> a == b }
        "!=", "<>" -> { a, b -> a != b }
        else -> error("Unknown compare_int op: '$op' (expected one of >, <, >=, <=, ==, !=)")
    }
}
