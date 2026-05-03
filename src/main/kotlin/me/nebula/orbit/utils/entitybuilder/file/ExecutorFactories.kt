package me.nebula.orbit.utils.entitybuilder.file

import com.google.gson.JsonObject
import me.nebula.orbit.utils.entitybuilder.ApplyPotionExecutor
import me.nebula.orbit.utils.entitybuilder.AreaDamageExecutor
import me.nebula.orbit.utils.entitybuilder.BehaviorExecutor
import me.nebula.orbit.utils.entitybuilder.BroadcastMessageExecutor
import me.nebula.orbit.utils.entitybuilder.ChargeAttackExecutor
import me.nebula.orbit.utils.entitybuilder.CircleTargetExecutor
import me.nebula.orbit.utils.entitybuilder.ClearMemoryExecutor
import me.nebula.orbit.utils.entitybuilder.ConeAreaDamageExecutor
import me.nebula.orbit.utils.entitybuilder.CooldownDecorator
import me.nebula.orbit.utils.entitybuilder.EffectApplication
import me.nebula.orbit.utils.entitybuilder.FallbackDecorator
import me.nebula.orbit.utils.entitybuilder.FleeEntityExecutor
import me.nebula.orbit.utils.entitybuilder.FlatRoamExecutor
import me.nebula.orbit.utils.entitybuilder.FollowEntityExecutor
import me.nebula.orbit.utils.entitybuilder.HealExecutor
import me.nebula.orbit.utils.entitybuilder.HitOptions
import me.nebula.orbit.utils.entitybuilder.IdleExecutor
import me.nebula.orbit.utils.entitybuilder.IncrementMemoryIntExecutor
import me.nebula.orbit.utils.entitybuilder.InvertDecorator
import me.nebula.orbit.utils.entitybuilder.LeapAttackExecutor
import me.nebula.orbit.utils.entitybuilder.LookAroundExecutor
import me.nebula.orbit.utils.entitybuilder.LookAtTargetExecutor
import me.nebula.orbit.utils.entitybuilder.LoopDecorator
import me.nebula.orbit.utils.entitybuilder.MeleeAttackExecutor
import me.nebula.orbit.utils.entitybuilder.MemoryKey
import me.nebula.orbit.utils.entitybuilder.MemoryKeys
import me.nebula.orbit.utils.entitybuilder.PanicExecutor
import me.nebula.orbit.utils.entitybuilder.ParallelExecutor
import me.nebula.orbit.utils.entitybuilder.ParallelPolicy
import me.nebula.orbit.utils.entitybuilder.PlaySoundExecutor
import me.nebula.orbit.utils.entitybuilder.RangedAttackExecutor
import me.nebula.orbit.utils.entitybuilder.RepeatForeverDecorator
import me.nebula.orbit.utils.entitybuilder.RetreatTowardExecutor
import me.nebula.orbit.utils.entitybuilder.ReturnHomeExecutor
import me.nebula.orbit.utils.entitybuilder.RingAreaDamageExecutor
import me.nebula.orbit.utils.entitybuilder.SelectorExecutor
import me.nebula.orbit.utils.entitybuilder.SequenceExecutor
import me.nebula.orbit.utils.entitybuilder.SetMemoryIntExecutor
import me.nebula.orbit.utils.entitybuilder.ShieldExecutor
import me.nebula.orbit.utils.entitybuilder.StrafeExecutor
import me.nebula.orbit.utils.entitybuilder.SummonMinionsExecutor
import me.nebula.orbit.utils.entitybuilder.TelegraphExecutor
import me.nebula.orbit.utils.entitybuilder.TelegraphShape
import me.nebula.orbit.utils.entitybuilder.TeleportBehindExecutor
import me.nebula.orbit.utils.entitybuilder.TimeoutDecorator
import me.nebula.orbit.utils.entitybuilder.WaitExecutor
import me.nebula.orbit.utils.entitybuilder.WithChanceDecorator
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.particle.Particle
import net.minestom.server.potion.PotionEffect
import net.minestom.server.sound.SoundEvent
import java.util.concurrent.ConcurrentHashMap

fun interface ExecutorFactory {
    fun create(json: JsonObject): BehaviorExecutor
}

object ExecutorFactories {
    private val factories = ConcurrentHashMap<String, ExecutorFactory>()

    fun register(type: String, factory: ExecutorFactory) {
        factories[type] = factory
    }

    fun create(json: JsonObject): BehaviorExecutor {
        val type = json.requireString("type")
        val factory = factories[type] ?: error("Unknown executor type: $type")
        return factory.create(json)
    }

    fun types(): Set<String> = factories.keys.toSet()

    @Suppress("UNCHECKED_CAST")
    private fun memoryKeyOf(name: String): MemoryKey<Entity> =
        (MemoryKeys.byName(name) as? MemoryKey<Entity>)
            ?: error("Unknown or non-Entity memory key: $name")

    private fun entityTypeOf(key: String): EntityType =
        EntityType.fromKey(Key.key(if (':' in key) key else "minecraft:$key"))
            ?: error("Unknown entity type: $key")

    private fun parseHitOptions(obj: JsonObject): HitOptions {
        val effects = obj.array("effects")?.map { entry ->
            val e = entry.asObjectOrError()
            val key = e.requireString("type")
            val potion = PotionEffect.fromKey(Key.key(if (':' in key) key else "minecraft:$key"))
                ?: error("Unknown potion effect: $key")
            EffectApplication(
                type = potion,
                durationTicks = e.int("duration_ticks", 60),
                amplifier = e.int("amplifier", 0),
                chance = e.float("chance", 1f),
            )
        } ?: emptyList()
        return HitOptions(
            effects = effects,
            knockbackHorizontal = obj.float("knockback_horizontal", 0f),
            knockbackVertical = obj.float("knockback_vertical", 0f),
        )
    }

    init {
        register("idle") { obj ->
            IdleExecutor(
                minTicks = obj.int("min_ticks", 40),
                maxTicks = obj.int("max_ticks", 100),
            )
        }

        register("flat_roam") { obj ->
            FlatRoamExecutor(
                range = obj.double("range", 10.0),
                runTicks = obj.int("run_ticks", 100),
                maxRetries = obj.int("max_retries", 10),
                chainTargets = obj.bool("chain_targets", true),
                avoidKey = obj.string("avoid_memory_key", "").ifEmpty { null }?.let { memoryKeyOf(it) },
                avoidDistance = obj.double("avoid_distance", 0.0),
            )
        }

        register("look_around") { obj ->
            LookAroundExecutor(
                minTicks = obj.int("min_ticks", 20),
                maxTicks = obj.int("max_ticks", 60),
            )
        }

        register("look_at_target") { obj ->
            LookAtTargetExecutor(
                memoryKey = memoryKeyOf(obj.string("target_memory_key", "nearest_player")),
                minTicks = obj.int("min_ticks", 40),
                maxTicks = obj.int("max_ticks", 80),
            )
        }

        register("follow_entity") { obj ->
            FollowEntityExecutor(
                memoryKey = memoryKeyOf(obj.string("target_memory_key", "nearest_player")),
                minRange = obj.double("min_range", 2.0),
                maxRange = obj.double("max_range", 32.0),
            )
        }

        register("flee_entity") { obj ->
            FleeEntityExecutor(
                memoryKey = memoryKeyOf(obj.string("target_memory_key", "attack_target")),
                fleeRange = obj.double("flee_range", 12.0),
                safeDistance = obj.double("safe_distance", 16.0),
            )
        }

        register("retreat_toward") { obj ->
            RetreatTowardExecutor(
                threatKey = memoryKeyOf(obj.string("threat_memory_key", "attack_target")),
                safeKey = memoryKeyOf(obj.string("safe_memory_key", "owner")),
                triggerDistance = obj.double("trigger_distance", 6.0),
                arrivedDistance = obj.double("arrived_distance", 3.0),
                fallbackFleeRange = obj.double("fallback_flee_range", 6.0),
            )
        }

        register("melee_attack") { obj ->
            MeleeAttackExecutor(
                attackRange = obj.double("attack_range", 2.5),
                cooldownTicks = obj.int("cooldown_ticks", 20),
                damage = obj.float("damage", 4f),
                cooldownName = obj.string("cooldown_name", "melee_attack"),
                hitOptions = parseHitOptions(obj),
            )
        }

        register("ranged_attack") { obj ->
            RangedAttackExecutor(
                projectileType = entityTypeOf(obj.string("projectile_type", "arrow")),
                attackRange = obj.double("attack_range", 16.0),
                minRange = obj.double("min_range", 4.0),
                cooldownTicks = obj.int("cooldown_ticks", 40),
                projectileSpeed = obj.double("projectile_speed", 1.5),
                projectileDamage = obj.float("projectile_damage", 4f),
                cooldownName = obj.string("cooldown_name", "ranged_attack"),
                originBone = obj.string("origin_bone", "").ifEmpty { null },
            )
        }

        register("leap_attack") { obj ->
            LeapAttackExecutor(
                leapRange = obj.double("leap_range", 6.0),
                minRange = obj.double("min_range", 3.0),
                horizontalForce = obj.double("horizontal_force", 1.2),
                verticalForce = obj.double("vertical_force", 0.5),
                damage = obj.float("damage", 6f),
                cooldownTicks = obj.int("cooldown_ticks", 60),
                cooldownName = obj.string("cooldown_name", "leap_attack"),
                hitOptions = parseHitOptions(obj),
            )
        }

        register("charge_attack") { obj ->
            ChargeAttackExecutor(
                chargeUpTicks = obj.int("charge_up_ticks", 40),
                chargeSpeed = obj.double("charge_speed", 2.0),
                damage = obj.float("damage", 10f),
                hitRadius = obj.double("hit_radius", 1.5),
                cooldownTicks = obj.int("cooldown_ticks", 100),
                cooldownName = obj.string("cooldown_name", "charge_attack"),
                hitOptions = parseHitOptions(obj),
            )
        }

        register("teleport_behind") { obj ->
            TeleportBehindExecutor(
                cooldownTicks = obj.int("cooldown_ticks", 80),
                distance = obj.double("distance", 3.0),
                cooldownName = obj.string("cooldown_name", "teleport_behind"),
            )
        }

        register("summon_minions") { obj ->
            SummonMinionsExecutor(
                minionType = entityTypeOf(obj.requireString("minion_type")),
                count = obj.int("count", 3),
                radius = obj.double("radius", 4.0),
                cooldownTicks = obj.int("cooldown_ticks", 200),
                cooldownName = obj.string("cooldown_name", "summon_minions"),
            )
        }

        register("area_damage") { obj ->
            AreaDamageExecutor(
                radius = obj.double("radius", 5.0),
                damage = obj.float("damage", 6f),
                cooldownTicks = obj.int("cooldown_ticks", 60),
                cooldownName = obj.string("cooldown_name", "area_damage"),
                hitOptions = parseHitOptions(obj),
            )
        }

        register("cone_damage") { obj ->
            ConeAreaDamageExecutor(
                length = obj.double("length", 6.0),
                angleDegrees = obj.double("angle_degrees", 90.0),
                damage = obj.float("damage", 6f),
                cooldownTicks = obj.int("cooldown_ticks", 60),
                cooldownName = obj.string("cooldown_name", "cone_damage"),
                hitOptions = parseHitOptions(obj),
            )
        }

        register("ring_damage") { obj ->
            RingAreaDamageExecutor(
                innerRadius = obj.double("inner_radius", 2.0),
                outerRadius = obj.double("outer_radius", 6.0),
                damage = obj.float("damage", 6f),
                cooldownTicks = obj.int("cooldown_ticks", 60),
                cooldownName = obj.string("cooldown_name", "ring_damage"),
                hitOptions = parseHitOptions(obj),
            )
        }

        register("shield") { obj ->
            ShieldExecutor(
                durationTicks = obj.int("duration_ticks", 60),
                cooldownTicks = obj.int("cooldown_ticks", 200),
                damageReduction = obj.float("damage_reduction", 0.5f),
                cooldownName = obj.string("cooldown_name", "shield"),
            )
        }

        register("heal") { obj ->
            HealExecutor(
                healAmount = obj.float("heal_amount", 4f),
                cooldownTicks = obj.int("cooldown_ticks", 100),
                cooldownName = obj.string("cooldown_name", "heal"),
            )
        }

        register("strafe") { obj ->
            StrafeExecutor(
                strafeRange = obj.double("strafe_range", 8.0),
                strafeSpeed = obj.double("strafe_speed", 0.1),
                changeDirTicks = obj.int("change_dir_ticks", 40),
            )
        }

        register("circle_target") { obj ->
            CircleTargetExecutor(
                radius = obj.double("radius", 5.0),
                speed = obj.double("speed", 0.08),
            )
        }

        register("panic") { obj ->
            PanicExecutor(range = obj.double("range", 8.0))
        }

        register("return_home") { obj ->
            ReturnHomeExecutor(
                maxDistance = obj.double("max_distance", 30.0),
                arrivedDistance = obj.double("arrived_distance", 2.0),
            )
        }

        register("wait") { obj ->
            WaitExecutor(ticks = obj.int("ticks", 20))
        }

        register("sequence") { obj ->
            val children = obj.array("children")?.map { create(it.asObjectOrError()) } ?: emptyList()
            SequenceExecutor(children)
        }

        register("selector") { obj ->
            val children = obj.array("children")?.map { create(it.asObjectOrError()) } ?: emptyList()
            SelectorExecutor(children)
        }

        register("parallel") { obj ->
            val children = obj.array("children")?.map { create(it.asObjectOrError()) } ?: emptyList()
            val policy = when (obj.string("policy", "all").lowercase()) {
                "any" -> ParallelPolicy.ANY
                else -> ParallelPolicy.ALL
            }
            ParallelExecutor(children, policy)
        }

        register("with_cooldown") { obj ->
            CooldownDecorator(
                child = create(obj.requireObj("child")),
                cooldownName = obj.requireString("name"),
                cooldown = obj.ticksAsDuration("ticks", 100),
            )
        }

        register("with_timeout") { obj ->
            TimeoutDecorator(
                child = create(obj.requireObj("child")),
                ticks = obj.int("ticks", 100),
            )
        }

        register("loop") { obj ->
            LoopDecorator(
                child = create(obj.requireObj("child")),
                times = obj.int("times", 1),
            )
        }

        register("telegraph") { obj ->
            val particleKey = obj.string("particle", "flame")
            val particle = Particle.fromKey(Key.key(if (':' in particleKey) particleKey else "minecraft:$particleKey"))
                ?: error("Unknown particle: $particleKey")
            TelegraphExecutor(
                particle = particle,
                durationTicks = obj.int("duration_ticks", 30),
                shape = parseTelegraphShape(obj.requireObj("shape")),
                yOffset = obj.double("y_offset", 0.1),
            )
        }

        register("set_memory_int") { obj ->
            SetMemoryIntExecutor(
                key = intKey(obj.requireString("key")),
                value = obj.int("value", 0),
            )
        }

        register("increment_memory_int") { obj ->
            IncrementMemoryIntExecutor(
                key = intKey(obj.requireString("key")),
                delta = obj.int("delta", 1),
                default = obj.int("default", 0),
            )
        }

        register("clear_memory") { obj ->
            ClearMemoryExecutor(
                key = MemoryKeys.byName(obj.requireString("key"))
                    ?: error("Unknown memory key: ${obj.requireString("key")}"),
            )
        }

        register("play_sound") { obj ->
            val key = obj.requireString("sound")
            val soundEvent = SoundEvent.fromKey(Key.key(if (':' in key) key else "minecraft:$key"))
                ?: error("Unknown sound: $key")
            PlaySoundExecutor(
                soundEvent = soundEvent,
                volume = obj.float("volume", 1f),
                pitch = obj.float("pitch", 1f),
            )
        }

        register("apply_potion") { obj ->
            val key = obj.requireString("type")
            val effect = PotionEffect.fromKey(Key.key(if (':' in key) key else "minecraft:$key"))
                ?: error("Unknown potion effect: $key")
            ApplyPotionExecutor(
                effect = effect,
                durationTicks = obj.int("duration_ticks", 60),
                amplifier = obj.int("amplifier", 0),
            )
        }

        register("broadcast_message") { obj ->
            val raw = obj.requireString("message")
            val component: Component = runCatching { MiniMessage.miniMessage().deserialize(raw) }
                .getOrElse { Component.text(raw) }
            BroadcastMessageExecutor(
                message = component,
                range = obj.double("range", 32.0),
            )
        }

        register("with_chance") { obj ->
            WithChanceDecorator(
                child = create(obj.requireObj("child")),
                chance = obj.float("chance", 0.5f).coerceIn(0f, 1f),
            )
        }

        register("fallback") { obj ->
            FallbackDecorator(
                primary = create(obj.requireObj("primary")),
                alternative = create(obj.requireObj("alternative")),
            )
        }

        register("repeat_forever") { obj ->
            RepeatForeverDecorator(create(obj.requireObj("child")))
        }

        register("invert") { obj ->
            InvertDecorator(create(obj.requireObj("child")))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun intKey(name: String): MemoryKey<Int> =
        (MemoryKeys.byName(name) as? MemoryKey<Int>)
            ?: error("Unknown or non-Int memory key: $name")

    private fun parseTelegraphShape(obj: JsonObject): TelegraphShape =
        when (val type = obj.requireString("type")) {
            "ring" -> TelegraphShape.Ring(
                radius = obj.double("radius", 5.0),
                points = obj.int("points", 24),
            )
            "filled_circle" -> TelegraphShape.FilledCircle(
                radius = obj.double("radius", 5.0),
                rings = obj.int("rings", 4),
                pointsPerRing = obj.int("points_per_ring", 16),
            )
            "annulus" -> TelegraphShape.Annulus(
                innerRadius = obj.double("inner_radius", 2.0),
                outerRadius = obj.double("outer_radius", 6.0),
                rings = obj.int("rings", 3),
                points = obj.int("points", 24),
            )
            "cone" -> TelegraphShape.Cone(
                length = obj.double("length", 6.0),
                angleDegrees = obj.double("angle_degrees", 90.0),
                rings = obj.int("rings", 5),
                arcPoints = obj.int("arc_points", 12),
            )
            "line_forward" -> TelegraphShape.LineForward(
                length = obj.double("length", 8.0),
                density = obj.double("density", 0.4),
            )
            else -> error("Unknown telegraph shape: $type")
        }
}
