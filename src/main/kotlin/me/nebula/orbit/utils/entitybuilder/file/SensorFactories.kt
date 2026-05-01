package me.nebula.orbit.utils.entitybuilder.file

import com.google.gson.JsonObject
import me.nebula.orbit.utils.entitybuilder.ClusterCountSensor
import me.nebula.orbit.utils.entitybuilder.DamageSensor
import me.nebula.orbit.utils.entitybuilder.LastKnownPositionSensor
import me.nebula.orbit.utils.entitybuilder.LineOfSightSensor
import me.nebula.orbit.utils.entitybuilder.LowHealthSensor
import me.nebula.orbit.utils.entitybuilder.MemoryKey
import me.nebula.orbit.utils.entitybuilder.MemoryKeys
import me.nebula.orbit.utils.entitybuilder.NearestEntitySensor
import me.nebula.orbit.utils.entitybuilder.NearestNonOwnerEntitySensor
import me.nebula.orbit.utils.entitybuilder.NearestPlayerSensor
import me.nebula.orbit.utils.entitybuilder.Sensor
import me.nebula.orbit.utils.entitybuilder.ThreatTargetSensor
import me.nebula.orbit.utils.entitybuilder.VisibleNearestPlayerSensor
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import java.util.concurrent.ConcurrentHashMap

fun interface SensorFactory {
    fun create(json: JsonObject): Sensor
}

object SensorFactories {
    private val factories = ConcurrentHashMap<String, SensorFactory>()

    fun register(type: String, factory: SensorFactory) {
        factories[type] = factory
    }

    fun create(json: JsonObject): Sensor {
        val type = json.requireString("type")
        val factory = factories[type] ?: error("Unknown sensor type: $type")
        return factory.create(json)
    }

    fun types(): Set<String> = factories.keys.toSet()

    init {
        register("nearest_player") { obj ->
            NearestPlayerSensor(
                range = obj.double("range", 32.0),
                minRange = obj.double("min_range", 0.0),
                period = obj.int("period", 20),
            )
        }

        register("low_health") { obj ->
            LowHealthSensor(
                threshold = obj.float("threshold", 0.3f),
                panicTicks = obj.int("panic_ticks", 60),
                period = obj.int("period", 10),
            )
        }

        register("damage") { obj ->
            DamageSensor(
                retargetOnDamage = obj.bool("retarget_on_damage", true),
                period = obj.int("period", 1),
            )
        }

        register("nearest_entity") { obj ->
            val playersOnly = obj.bool("players_only", false)
            NearestEntitySensor(
                range = obj.double("range", 16.0),
                target = entityKey(obj.string("target_memory_key", "attack_target")),
                predicate = if (playersOnly) { e -> e is Player } else { _ -> true },
                period = obj.int("period", 10),
            )
        }

        register("nearest_non_owner") { obj ->
            NearestNonOwnerEntitySensor(
                range = obj.double("range", 24.0),
                target = entityKey(obj.string("target_memory_key", "attack_target")),
                ownerKey = entityKey(obj.string("owner_memory_key", "owner")),
                playersOnly = obj.bool("players_only", true),
                period = obj.int("period", 10),
            )
        }

        register("line_of_sight") { obj ->
            LineOfSightSensor(
                targetKey = entityKey(obj.string("target_memory_key", "attack_target")),
                lastPositionKey = pointKey(obj.string("last_position_key", "last_known_position")),
                range = obj.double("range", 32.0),
                gracePeriodTicks = obj.int("grace_period_ticks", 60),
                period = obj.int("period", 5),
            )
        }

        register("last_known_position") { obj ->
            LastKnownPositionSensor(
                targetKey = entityKey(obj.string("target_memory_key", "attack_target")),
                outputKey = pointKey(obj.string("output_key", "last_known_position")),
                period = obj.int("period", 5),
            )
        }

        register("threat_target") { obj ->
            ThreatTargetSensor(
                targetKey = entityKey(obj.string("target_memory_key", "attack_target")),
                fallbackRange = obj.double("fallback_range", 32.0),
                playersOnlyFallback = obj.bool("players_only_fallback", true),
                period = obj.int("period", 10),
            )
        }

        register("visible_nearest_player") { obj ->
            VisibleNearestPlayerSensor(
                range = obj.double("range", 32.0),
                ignoreSneaking = obj.bool("ignore_sneaking", true),
                requireLineOfSight = obj.bool("require_line_of_sight", true),
                period = obj.int("period", 20),
            )
        }

        register("cluster_count") { obj ->
            ClusterCountSensor(
                outputKey = intKey(obj.string("output_key", "nearby_player_count")),
                range = obj.double("range", 24.0),
                playersOnly = obj.bool("players_only", true),
                period = obj.int("period", 40),
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun intKey(name: String): MemoryKey<Int> =
        (MemoryKeys.byName(name) as? MemoryKey<Int>)
            ?: error("Unknown or non-Int memory key: $name")

    @Suppress("UNCHECKED_CAST")
    private fun entityKey(name: String): MemoryKey<Entity> =
        (MemoryKeys.byName(name) as? MemoryKey<Entity>)
            ?: error("Unknown or non-Entity memory key: $name")

    @Suppress("UNCHECKED_CAST")
    private fun pointKey(name: String): MemoryKey<Point> =
        (MemoryKeys.byName(name) as? MemoryKey<Point>)
            ?: error("Unknown or non-Point memory key: $name")
}
