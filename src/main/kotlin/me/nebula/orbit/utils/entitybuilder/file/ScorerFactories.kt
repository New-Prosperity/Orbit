package me.nebula.orbit.utils.entitybuilder.file

import com.google.gson.JsonObject
import me.nebula.orbit.utils.entitybuilder.BehaviorScorer
import me.nebula.orbit.utils.entitybuilder.MemoryKey
import me.nebula.orbit.utils.entitybuilder.MemoryKeys
import net.minestom.server.entity.Entity
import java.util.concurrent.ConcurrentHashMap

fun interface ScorerFactory {
    fun create(json: JsonObject): BehaviorScorer
}

object ScorerFactories {
    private val factories = ConcurrentHashMap<String, ScorerFactory>()

    fun register(type: String, factory: ScorerFactory) {
        factories[type] = factory
    }

    fun create(json: JsonObject): BehaviorScorer {
        val type = json.requireString("type")
        val factory = factories[type] ?: error("Unknown scorer type: $type")
        return factory.create(json)
    }

    fun types(): Set<String> = factories.keys.toSet()

    init {
        register("constant") { obj ->
            val value = obj.float("value", 0f)
            BehaviorScorer { value }
        }

        register("linear_health") { obj ->
            val base = obj.float("base", 0f)
            val slope = obj.float("slope", 0f)
            BehaviorScorer { base + slope * it.healthPercent() }
        }

        register("phase_priority") { obj ->
            val priorities = obj.requireObj("priorities")
            val parsed = priorities.entrySet().associate { (k, v) -> k.toInt() to v.asFloat }
            val default = obj.float("default", 0f)
            BehaviorScorer { entity ->
                val phase = entity.memory.get(MemoryKeys.PHASE) ?: 0
                parsed[phase] ?: default
            }
        }

        register("inverse_distance") { obj ->
            val targetKey = obj.string("target_memory_key", "attack_target")
            val multiplier = obj.float("multiplier", 1f)
            val maxScore = obj.float("max_score", 100f)
            BehaviorScorer { entity ->
                @Suppress("UNCHECKED_CAST")
                val key = MemoryKeys.byName(targetKey) as? MemoryKey<Entity>
                    ?: return@BehaviorScorer 0f
                val target = entity.memory.get(key) ?: return@BehaviorScorer 0f
                val dist = entity.position.distance(target.position)
                if (dist <= 0.01) maxScore else (multiplier / dist.toFloat()).coerceAtMost(maxScore)
            }
        }

        register("hp_below_priority") { obj ->
            val threshold = obj.float("threshold", 0.5f)
            val above = obj.float("above", 0f)
            val below = obj.float("below", 10f)
            BehaviorScorer { entity ->
                if (entity.healthPercent() < threshold) below else above
            }
        }

        register("sum") { obj ->
            val parts = obj.array("of")?.map { create(it.asObjectOrError()) } ?: emptyList()
            BehaviorScorer { entity ->
                var total = 0f
                parts.forEach { total += it.score(entity) }
                total
            }
        }

        register("max") { obj ->
            val parts = obj.array("of")?.map { create(it.asObjectOrError()) } ?: emptyList()
            BehaviorScorer { entity ->
                var best = Float.NEGATIVE_INFINITY
                parts.forEach { val s = it.score(entity); if (s > best) best = s }
                if (best == Float.NEGATIVE_INFINITY) 0f else best
            }
        }

        register("distance") { obj ->
            val targetKey = obj.string("target_memory_key", "attack_target")
            val multiplier = obj.float("multiplier", 1f)
            val maxScore = obj.float("max_score", 100f)
            BehaviorScorer { entity ->
                @Suppress("UNCHECKED_CAST")
                val key = MemoryKeys.byName(targetKey) as? MemoryKey<Entity>
                    ?: return@BehaviorScorer 0f
                val target = entity.memory.get(key) ?: return@BehaviorScorer 0f
                val dist = entity.position.distance(target.position).toFloat()
                (dist * multiplier).coerceAtMost(maxScore)
            }
        }

        register("memory_int") { obj ->
            val keyName = obj.requireString("key")
            val multiplier = obj.float("multiplier", 1f)
            val maxScore = obj.float("max_score", Float.MAX_VALUE)
            val default = obj.int("default", 0)
            val key = MemoryKeys.byName(keyName) ?: error("Unknown memory key: $keyName")
            BehaviorScorer { entity ->
                @Suppress("UNCHECKED_CAST")
                val v = entity.memory.get(key as MemoryKey<Int>) ?: default
                (v * multiplier).coerceAtMost(maxScore)
            }
        }

        register("scaled") { obj ->
            val child = create(obj.requireObj("of"))
            val multiplier = obj.float("multiplier", 1f)
            BehaviorScorer { entity -> child.score(entity) * multiplier }
        }

        register("if") { obj ->
            val evaluator = EvaluatorFactories.create(obj.requireObj("evaluator"))
            val thenS = create(obj.requireObj("then"))
            val elseS = obj.obj("else")?.let { create(it) }
            BehaviorScorer { entity ->
                if (evaluator.evaluate(entity)) thenS.score(entity)
                else elseS?.score(entity) ?: 0f
            }
        }
    }
}
