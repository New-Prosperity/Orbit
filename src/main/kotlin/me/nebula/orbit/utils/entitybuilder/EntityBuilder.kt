package me.nebula.orbit.utils.entitybuilder

import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

class EntityBuilder @PublishedApi internal constructor(
    private val type: EntityType,
) {
    private var health: Float = 20f
    private var speed: Double = 0.1
    private var attack: Double = 2.0
    private var modelName: String? = null
    private val sensors = mutableListOf<Sensor>()
    private val behaviors = mutableListOf<Behavior>()
    private val controllers = mutableListOf<EntityController>()
    private val equipment = mutableMapOf<EquipmentSlot, ItemStack>()
    private var customName: Component? = null
    private var nameVisible: Boolean = true

    private var onSpawn: ((SmartEntity) -> Unit)? = null
    private var onDamage: ((SmartEntity) -> Unit)? = null
    private var onDeath: ((SmartEntity, Damage?) -> Unit)? = null
    private var onTick: ((SmartEntity) -> Unit)? = null
    private var onTarget: ((SmartEntity, Entity) -> Unit)? = null

    private val loot = mutableListOf<LootEntry>()
    private var sounds = EntitySounds()
    private var knockbackResistance: Double = 0.0
    private var fireImmune: Boolean = false
    private val damageMultipliers = mutableMapOf<String, Float>()
    private val phases = mutableListOf<PhaseConfig>()
    private var tetherRadius: Double = 0.0
    private var packAlertRadius: Double = 0.0
    private var despawnConfig: DespawnConfig? = null

    fun health(value: Float) { health = value }
    fun speed(value: Double) { speed = value }
    fun attack(value: Double) { attack = value }
    fun model(name: String) { modelName = name }

    fun sensor(sensor: Sensor) { sensors += sensor }

    fun nearestPlayerSensor(range: Double = 32.0, period: Int = 20) {
        sensors += NearestPlayerSensor(range, period = period)
    }

    fun nearestEntitySensor(
        range: Double = 16.0,
        target: MemoryKey<Entity> = MemoryKeys.ATTACK_TARGET,
        period: Int = 10,
        predicate: (Entity) -> Boolean = { true },
    ) {
        sensors += NearestEntitySensor(range, target, predicate, period)
    }

    fun behavior(id: String, block: BehaviorBuilder.() -> Unit) {
        behaviors += BehaviorBuilder(id).apply(block).build()
    }

    fun controller(controller: EntityController) { controllers += controller }
    fun walkController() { controllers += WalkController() }
    fun lookController() { controllers += LookController() }

    fun equip(slot: EquipmentSlot, item: ItemStack) { equipment[slot] = item }

    fun name(component: Component) { customName = component }
    fun name(text: String) { customName = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(text) }
    fun nameVisible(visible: Boolean) { nameVisible = visible }

    fun onSpawn(block: (SmartEntity) -> Unit) { onSpawn = block }
    fun onDamage(block: (SmartEntity) -> Unit) { onDamage = block }
    fun onDeath(block: (SmartEntity, Damage?) -> Unit) { onDeath = block }
    fun onTick(block: (SmartEntity) -> Unit) { onTick = block }
    fun onTarget(block: (SmartEntity, Entity) -> Unit) { onTarget = block }

    fun drop(item: ItemStack, chance: Float = 1f, min: Int = 1, max: Int = 1) {
        loot += LootEntry(item, chance, min, max)
    }

    fun drop(material: Material, chance: Float = 1f, min: Int = 1, max: Int = 1) {
        loot += LootEntry(ItemStack.of(material), chance, min, max)
    }

    fun sounds(block: SoundsBuilder.() -> Unit) {
        sounds = SoundsBuilder().apply(block).build()
    }

    fun knockbackResistance(value: Double) { knockbackResistance = value.coerceIn(0.0, 1.0) }
    fun fireImmune() { fireImmune = true }

    fun immune(type: String) { damageMultipliers[type] = 0f }
    fun resistant(type: String, multiplier: Float) { damageMultipliers[type] = multiplier }
    fun vulnerable(type: String, multiplier: Float) { damageMultipliers[type] = multiplier }

    fun phase(healthPercent: Float, onEnter: (SmartEntity) -> Unit) {
        phases += PhaseConfig(healthPercent, onEnter)
    }

    fun tether(radius: Double) { tetherRadius = radius }
    fun packAlert(radius: Double = 16.0) { packAlertRadius = radius }

    fun despawn(noPlayerRadius: Double = 128.0, checkPeriod: Int = 100, persistAfterDamage: Boolean = true) {
        despawnConfig = DespawnConfig(noPlayerRadius, checkPeriod, persistAfterDamage)
    }

    fun hostile(
        attackRange: Double = 2.5,
        attackDamage: Float = 4f,
        attackCooldown: Int = 20,
        sensorRange: Double = 32.0,
    ) {
        nearestEntitySensor(sensorRange, MemoryKeys.ATTACK_TARGET, predicate = { it is Player })
        behavior("panic") {
            priority(4)
            evaluateWhen { it.memory.has(MemoryKeys.PANIC_TICKS) && (it.memory.get(MemoryKeys.PANIC_TICKS) ?: 0) > 0 }
            executor(PanicExecutor())
        }
        behavior("attack") {
            priority(3)
            evaluateWhen { it.memory.has(MemoryKeys.ATTACK_TARGET) }
            executor(MeleeAttackExecutor(attackRange, attackCooldown, attackDamage))
        }
        behavior("idle") { priority(1); weight(1); executor(IdleExecutor()) }
        behavior("roam") { priority(1); weight(3); executor(FlatRoamExecutor()) }
        walkController()
        lookController()
    }

    fun passive(strollRange: Double = 10.0) {
        nearestPlayerSensor(16.0, 40)
        behavior("look_at_player") {
            priority(2)
            evaluateWhen { it.memory.has(MemoryKeys.NEAREST_PLAYER) }
            executor(LookAtTargetExecutor(MemoryKeys.NEAREST_PLAYER))
        }
        behavior("idle") { priority(1); weight(1); executor(IdleExecutor()) }
        behavior("roam") { priority(1); weight(3); executor(FlatRoamExecutor(strollRange)) }
        walkController()
        lookController()
    }

    fun neutral(
        attackRange: Double = 2.5,
        attackDamage: Float = 4f,
        sensorRange: Double = 16.0,
        strollRange: Double = 10.0,
    ) {
        nearestPlayerSensor(sensorRange, 20)
        behavior("retaliate") {
            priority(3)
            evaluateWhen { it.memory.has(MemoryKeys.LAST_ATTACKER) }
            executor(object : BehaviorExecutor {
                override fun onStart(entity: SmartEntity) {
                    entity.memory.get(MemoryKeys.LAST_ATTACKER)?.let {
                        entity.memory.set(MemoryKeys.ATTACK_TARGET, it)
                    }
                }
                override fun execute(entity: SmartEntity): Boolean {
                    val target = entity.memory.get(MemoryKeys.ATTACK_TARGET) as? net.minestom.server.entity.LivingEntity
                    if (target == null || target.isRemoved || target.isDead) {
                        entity.memory.clear(MemoryKeys.LAST_ATTACKER)
                        return false
                    }
                    return true
                }
                override fun onStop(entity: SmartEntity) { entity.memory.clear(MemoryKeys.ATTACK_TARGET) }
            })
        }
        behavior("attack") {
            priority(3)
            evaluateWhen { it.memory.has(MemoryKeys.ATTACK_TARGET) }
            executor(MeleeAttackExecutor(attackRange, 20, attackDamage))
        }
        behavior("look_at_player") {
            priority(2)
            evaluateWhen { it.memory.has(MemoryKeys.NEAREST_PLAYER) }
            executor(LookAtTargetExecutor(MemoryKeys.NEAREST_PLAYER))
        }
        behavior("idle") { priority(1); weight(1); executor(IdleExecutor()) }
        behavior("roam") { priority(1); weight(3); executor(FlatRoamExecutor(strollRange)) }
        walkController()
        lookController()
    }

    @PublishedApi internal fun build(): SmartEntity {
        val group = BehaviorGroup(
            sensors = sensors.toList(),
            coreBehaviors = behaviors.filter { it.core },
            behaviors = behaviors.filter { !it.core },
            controllers = controllers.toList(),
        )
        val entity = SmartEntity(type, group, health, speed, attack, modelName)
        equipment.forEach { (slot, item) -> entity.setEquipment(slot, item) }
        customName?.let {
            entity.customName = it
            entity.isCustomNameVisible = nameVisible
        }
        entity.lootTable = loot.toList()
        entity.sounds = sounds
        entity.knockbackResistance = knockbackResistance
        entity.fireImmune = fireImmune
        entity.damageMultipliers = damageMultipliers.toMap()
        entity.phases = phases.sortedByDescending { it.threshold }
        entity.tetherRadius = tetherRadius
        entity.packAlertRadius = packAlertRadius
        entity.despawnConfig = despawnConfig
        entity.onDeath = onDeath
        entity.onTick = onTick
        entity.onTarget = onTarget
        entity.onDamaged = onDamage?.let { handler -> { e, _ -> handler(e) } }

        if (knockbackResistance > 0.0) {
            entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE).baseValue = knockbackResistance
        }
        return entity
    }

    @PublishedApi internal fun buildAndSpawn(instance: Instance, position: Pos): SmartEntity {
        val entity = build()
        entity.setInstance(instance, position)
        entity.memory.set(MemoryKeys.SPAWN_POSITION, position)
        if (tetherRadius > 0.0) entity.memory.set(MemoryKeys.HOME_POSITION, position)
        entity.setupModel()
        onSpawn?.invoke(entity)
        return entity
    }
}

class SoundsBuilder @PublishedApi internal constructor() {
    @PublishedApi internal var ambient: SoundEvent? = null
    @PublishedApi internal var ambientPeriod: Int = 80
    @PublishedApi internal var hurt: SoundEvent? = null
    @PublishedApi internal var death: SoundEvent? = null
    @PublishedApi internal var attack: SoundEvent? = null
    @PublishedApi internal var volume: Float = 1f
    @PublishedApi internal var pitch: Float = 1f

    fun ambient(sound: SoundEvent, period: Int = 80) { ambient = sound; ambientPeriod = period }
    fun hurt(sound: SoundEvent) { hurt = sound }
    fun death(sound: SoundEvent) { death = sound }
    fun attack(sound: SoundEvent) { attack = sound }
    fun volume(v: Float) { volume = v }
    fun pitch(p: Float) { pitch = p }

    @PublishedApi internal fun build(): EntitySounds =
        EntitySounds(ambient, ambientPeriod, hurt, death, attack, volume, pitch)
}

inline fun smartEntity(type: EntityType, block: EntityBuilder.() -> Unit): SmartEntity =
    EntityBuilder(type).apply(block).build()

inline fun spawnSmartEntity(
    type: EntityType,
    instance: Instance,
    position: Pos,
    block: EntityBuilder.() -> Unit,
): SmartEntity = EntityBuilder(type).apply(block).buildAndSpawn(instance, position)
