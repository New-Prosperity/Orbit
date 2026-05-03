package me.nebula.orbit.utils.entitybuilder.file

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.damage.DamageElements
import me.nebula.orbit.utils.entitybuilder.EntityBuilder
import me.nebula.orbit.utils.entitybuilder.SmartEntity
import me.nebula.orbit.utils.entitybuilder.Triggers
import me.nebula.orbit.utils.entitybuilder.spawnSmartEntity
import me.nebula.orbit.utils.modelengine.behavior.MountBehavior
import me.nebula.orbit.utils.modelengine.mount.MountControllers
import me.nebula.orbit.utils.modelengine.mount.SeatRegistry
import net.minestom.server.coordinate.Vec
import net.kyori.adventure.key.Key
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.Instance
import net.minestom.server.item.Material
import net.minestom.server.potion.PotionEffect
import net.minestom.server.sound.SoundEvent
import java.util.concurrent.ConcurrentHashMap

data class SeatConfig(
    val bone: String,
    val controller: String,
    val offsetY: Float,
    val width: Float?,
    val height: Float?,
)

class BehaviorFile(
    val id: String,
    val carrierType: EntityType,
    private val raw: JsonObject,
) {
    val invisibleCarrier: Boolean = raw.bool("invisible_carrier", true)
    val seats: List<SeatConfig> = raw.array("seats")?.map { entry ->
        val obj = entry.asObjectOrError()
        SeatConfig(
            bone = obj.requireString("bone"),
            controller = obj.string("controller", "passive"),
            offsetY = obj.float("offset_y", 0f),
            width = if (obj.has("width")) obj.float("width", 0.6f) else null,
            height = if (obj.has("height")) obj.float("height", 0.6f) else null,
        )
    } ?: emptyList()

    fun applyTo(builder: EntityBuilder) {
        raw.string("model")?.let { builder.model(it) }
        raw.get("health")?.takeIf { it.isJsonPrimitive }?.let { builder.health(it.asFloat) }
        raw.get("speed")?.takeIf { it.isJsonPrimitive }?.let { builder.speed(it.asDouble) }
        raw.get("attack_damage")?.takeIf { it.isJsonPrimitive }?.let { builder.attack(it.asDouble) }
        raw.get("knockback_resistance")?.takeIf { it.isJsonPrimitive }?.let {
            builder.knockbackResistance(it.asDouble)
        }
        if (raw.bool("fire_immune", false)) builder.fireImmune()
        raw.get("iframes_after_damage_ticks")?.takeIf { it.isJsonPrimitive }?.let {
            builder.iframesAfterDamage(it.asInt)
        }
        raw.obj("threat_table")?.let { tt ->
            builder.threatTable(
                damageWeight = tt.float("damage_weight", 1f),
                decayPerSecond = tt.float("decay_per_second", 0f),
            )
        }

        raw.get("tether_radius")?.takeIf { it.isJsonPrimitive }?.let { builder.tether(it.asDouble) }
        raw.get("pack_alert_radius")?.takeIf { it.isJsonPrimitive }?.let { builder.packAlert(it.asDouble) }

        raw.obj("despawn")?.let { d ->
            builder.despawn(
                noPlayerRadius = d.double("no_player_radius", 128.0),
                checkPeriod = d.int("check_period", 100),
                persistAfterDamage = d.bool("persist_after_damage", true),
            )
        }

        raw.obj("damage_modifiers")?.let { mods ->
            mods.entrySet().forEach { (key, value) ->
                if (!value.isJsonPrimitive) return@forEach
                val element = DamageElements.byId(key) ?: DamageElements.register(key)
                val mult = value.asFloat
                when {
                    mult <= 0f -> builder.immune(element)
                    mult < 1f -> builder.resistant(element, mult)
                    mult > 1f -> builder.vulnerable(element, mult)
                }
            }
        }

        raw.array("phases")?.forEachIndexed { phaseIndex, phase ->
            val obj = phase.asObjectOrError()
            val threshold = obj.float("threshold", 0.5f)
            builder.phase(threshold) { /* no-op enter — file format triggers via ON_PHASE_CHANGED */ }
            obj.obj("on_enter")?.let { enterExecutor ->
                builder.behavior("__phase_${phaseIndex}_enter") {
                    priority(obj.int("on_enter_priority", 100))
                    wakeOn(Triggers.ON_PHASE_CHANGED)
                    availableInPhase(phaseIndex)
                    executor(ExecutorFactories.create(enterExecutor))
                    obj.string("on_enter_play")?.let { playOnStart(it) }
                    obj.string("on_enter_sound")?.let { soundOnStart(parseSoundEvent(it)) }
                }
            }
        }

        raw.obj("regen")?.let { r ->
            builder.regen(
                amount = r.float("amount", 0f),
                intervalTicks = r.int("interval_ticks", 20),
                inCombat = r.bool("in_combat", false),
            )
        }
        raw.get("in_combat_timeout_ms")?.takeIf { it.isJsonPrimitive }?.let {
            builder.inCombatTimeoutMs(it.asLong)
        }
        raw.array("effect_immunities")?.forEach { entry ->
            val key = entry.asString
            val effect = PotionEffect.fromKey(Key.key(if (':' in key) key else "minecraft:$key"))
                ?: error("Unknown potion effect: $key")
            builder.immuneToEffect(effect)
        }
        raw.array("effect_resistances")?.forEach { entry ->
            val e = entry.asObjectOrError()
            val key = e.requireString("type")
            val effect = PotionEffect.fromKey(Key.key(if (':' in key) key else "minecraft:$key"))
                ?: error("Unknown potion effect: $key")
            builder.resistEffect(
                effect = effect,
                durationMultiplier = e.float("duration_multiplier", 0.5f),
                amplifierAdjustment = e.int("amplifier_adjustment", 0),
                applyChance = e.float("apply_chance", 1f),
            )
        }
        raw.array("tags")?.forEach { entry ->
            builder.tag(entry.asString)
        }
        raw.obj("death_animation")?.let { d ->
            builder.deathAnimation(
                animation = d.requireString("animation"),
                durationTicks = d.int("duration_ticks", 20),
            )
        }

        raw.array("controllers")?.forEach { ctrl ->
            when (ctrl.asString.lowercase()) {
                "walk" -> builder.walkController()
                "look" -> builder.lookController()
                else -> error("Unknown controller: ${ctrl.asString}")
            }
        }

        raw.array("sensors")?.forEach { s ->
            builder.sensor(SensorFactories.create(s.asObjectOrError()))
        }

        raw.array("behaviors")?.forEach { b ->
            applyBehavior(builder, b.asObjectOrError())
        }

        raw.array("loot")?.let { applyLoot(builder, it) }

        raw.string("name_text")?.let { builder.name(it) }
        if (!raw.bool("name_visible", true)) builder.nameVisible(false)
    }

    private fun applyBehavior(builder: EntityBuilder, obj: JsonObject) {
        val id = obj.requireString("id")
        builder.behavior(id) {
            priority(obj.int("priority", 0))
            weight(obj.int("weight", 1))
            period(obj.int("period", 1))
            if (obj.bool("core", false)) core()

            obj.string("play_on_start")?.let { playOnStart(it) }
            obj.string("play_on_stop")?.let { playOnStop(it) }
            obj.string("play_on_interrupt")?.let { playOnInterrupt(it) }
            if (!obj.bool("stop_animation_on_end", true)) keepAnimationOnEnd()
            obj.get("animation_lerp_in")?.takeIf { it.isJsonPrimitive }?.let {
                animationLerp(it.asFloat, obj.float("animation_lerp_out", it.asFloat))
            }
            obj.get("animation_speed")?.takeIf { it.isJsonPrimitive }?.let { animationSpeed(it.asFloat) }

            obj.string("sound_on_start")?.let { soundOnStart(parseSoundEvent(it)) }
            obj.string("sound_on_stop")?.let { soundOnStop(parseSoundEvent(it)) }
            obj.string("sound_on_interrupt")?.let { soundOnInterrupt(parseSoundEvent(it)) }
            obj.get("sound_volume")?.takeIf { it.isJsonPrimitive }?.let { soundVolume(it.asFloat) }
            obj.get("sound_pitch")?.takeIf { it.isJsonPrimitive }?.let { soundPitch(it.asFloat) }

            obj.array("wake_on")?.forEach { t ->
                val name = t.asString
                val trigger = Triggers.byName(name) ?: error("Unknown trigger: $name")
                wakeOn(trigger)
            }

            obj.array("available_in_phases")?.let { arr ->
                val phases = arr.map { it.asInt }.toIntArray()
                if (phases.isNotEmpty()) availableInPhases(*phases)
            }

            obj.obj("evaluator")?.let { evaluator(EvaluatorFactories.create(it)) }
            obj.obj("score")?.let { scorer(ScorerFactories.create(it)) }

            executor(ExecutorFactories.create(obj.requireObj("executor")))
        }
    }

    private fun parseSoundEvent(key: String): SoundEvent {
        val full = if (':' in key) key else "minecraft:$key"
        return SoundEvent.fromKey(Key.key(full))
            ?: error("Unknown sound event: $key")
    }

    private fun applyLoot(builder: EntityBuilder, arr: JsonArray) {
        arr.forEach { entry ->
            val obj = entry.asObjectOrError()
            val materialKey = obj.requireString("material")
            val material = Material.fromKey(Key.key(if (':' in materialKey) materialKey else "minecraft:$materialKey"))
                ?: error("Unknown material: $materialKey")
            builder.drop(
                material = material,
                chance = obj.float("chance", 1f),
                min = obj.int("min", 1),
                max = obj.int("max", 1),
            )
        }
    }

    fun spawn(instance: Instance, position: Pos, customize: EntityBuilder.() -> Unit = {}): SmartEntity {
        val invisible = invisibleCarrier
        return spawnSmartEntity(carrierType, instance, position) {
            applyTo(this)
            customize()
        }.also { entity ->
            if (invisible) entity.isInvisible = true
            applySeats(entity)
        }
    }

    private fun applySeats(entity: SmartEntity) {
        if (seats.isEmpty()) return
        val modeled = entity.modeledEntity ?: return
        val activeModel = modeled.models.values.firstOrNull() ?: return
        for (seat in seats) {
            val bone = activeModel.bones[seat.bone] ?: continue
            val mountBehavior = bone.behavior<MountBehavior>() ?: run {
                val mb = MountBehavior(
                    bone = bone,
                    seatOffset = Vec(0.0, seat.offsetY.toDouble(), 0.0),
                    width = seat.width ?: 0.6f,
                    height = seat.height ?: 0.6f,
                )
                bone.addBehavior(mb)
                mb.onAdd(modeled)
                mb
            }
            SeatRegistry.register(mountBehavior.seatEntityId, SeatRegistry.Binding(
                mountBehavior = mountBehavior,
                modeledEntity = modeled,
                controllerFactory = MountControllers.resolveFactory(seat.controller),
            ))
        }
    }

    companion object {
        fun parse(text: String): BehaviorFile {
            val obj = JsonParser.parseString(text).asJsonObject
            val id = obj.requireString("id")
            val carrierKey = obj.requireString("carrier_type")
            val carrierType = EntityType.fromKey(
                Key.key(if (':' in carrierKey) carrierKey else "minecraft:$carrierKey")
            ) ?: error("Unknown carrier_type: $carrierKey")
            return BehaviorFile(id, carrierType, obj)
        }

        fun load(resources: ResourceManager, path: String): BehaviorFile =
            parse(resources.readText(path))

        fun loadAll(resources: ResourceManager, directory: String): List<BehaviorFile> =
            resources.list(directory, "behavior").map { load(resources, it) }
    }
}

object BehaviorFileRegistry {
    private val byId = ConcurrentHashMap<String, BehaviorFile>()

    fun register(file: BehaviorFile) {
        byId[file.id] = file
    }

    fun get(id: String): BehaviorFile? = byId[id]

    fun all(): Collection<BehaviorFile> = byId.values

    fun isEmpty(): Boolean = byId.isEmpty()

    fun clear() = byId.clear()

    fun ids(): Set<String> = byId.keys.toSet()

    fun reloadOne(resources: ResourceManager, directory: String, id: String): Result<BehaviorFile> {
        val candidates = resources.list(directory, "behavior")
        val match = candidates.firstOrNull { path ->
            val name = path.substringAfterLast('/').removeSuffix(".behavior")
            name.equals(id, ignoreCase = true)
        }
        if (match == null) {
            return Result.failure(IllegalStateException("No .behavior file matches id: $id"))
        }
        return runCatching {
            val file = BehaviorFile.load(resources, match)
            require(file.id == id) {
                "File path '$match' loaded behavior id '${file.id}' but expected '$id'"
            }
            register(file)
            file
        }
    }

    fun reloadFrom(resources: ResourceManager, directory: String): ReloadResult {
        val loaded = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()
        clear()
        runCatching { BehaviorFile.loadAll(resources, directory) }
            .onSuccess { files ->
                for (f in files) {
                    runCatching { register(f) }
                        .onSuccess { loaded += f.id }
                        .onFailure { failed += f.id to (it.message ?: "(no message)") }
                }
            }
            .onFailure { failed += "<scan>" to (it.message ?: "(no message)") }
        return ReloadResult(loaded, failed)
    }

    data class ReloadResult(val loaded: List<String>, val failed: List<Pair<String, String>>)
}
