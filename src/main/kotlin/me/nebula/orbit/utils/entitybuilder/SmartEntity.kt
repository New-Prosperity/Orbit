package me.nebula.orbit.utils.entitybuilder

import me.nebula.orbit.utils.cooldown.EntityNamedCooldown
import me.nebula.orbit.utils.damage.DamageElement
import me.nebula.orbit.utils.damage.DamageElements
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.model.ActiveModel
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.Damage
import net.minestom.server.item.ItemStack
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import kotlin.random.Random

data class LootEntry(val item: ItemStack, val chance: Float = 1f, val minCount: Int = 1, val maxCount: Int = 1)

data class EntitySounds(
    val ambient: SoundEvent? = null,
    val ambientPeriod: Int = 80,
    val hurt: SoundEvent? = null,
    val death: SoundEvent? = null,
    val attack: SoundEvent? = null,
    val volume: Float = 1f,
    val pitch: Float = 1f,
)

data class PhaseConfig(
    val threshold: Float,
    val onEnter: (SmartEntity) -> Unit,
)

data class DespawnConfig(
    val noPlayerRadius: Double = 128.0,
    val checkPeriod: Int = 100,
    val persistAfterDamage: Boolean = true,
)

class SmartEntity(
    type: EntityType,
    val behaviorGroup: BehaviorGroup,
    private val baseHealth: Float = 20f,
    private val baseSpeed: Double = 0.1,
    private val baseAttack: Double = 2.0,
    private val modelName: String? = null,
) : EntityCreature(type) {

    val memory = MemoryStorage()
    var modeledEntity: ModeledEntity? = null
        private set

    var lootTable: List<LootEntry> = emptyList()
    var sounds: EntitySounds = EntitySounds()
    var knockbackResistance: Double = 0.0
    var damageMultipliers: Map<DamageElement, Float> = emptyMap()
    var iframesAfterDamageTicks: Int = 0
    private var iframeTicksRemaining: Int = 0

    var threatTable: ThreatTable? = null
    var threatDamageWeight: Float = 1f
    var threatDecayPerSecond: Float = 0f

    var inCombatTimeoutMs: Long = 5000L
    var regenAmount: Float = 0f
    var regenIntervalTicks: Int = 20
    var regenInCombat: Boolean = false
    var effectImmunities: Set<PotionEffect> = emptySet()
    var effectResistances: Map<PotionEffect, EffectResistance> = emptyMap()
    var deathAnimation: String? = null
    var deathAnimationTicks: Int = 0
    var onDealtDamage: ((SmartEntity, LivingEntity, Float) -> Unit)? = null

    private var regenCounter: Int = 0
    private var lastInCombat: Boolean = false
    private var dying: Boolean = false
    private var deathTicksRemaining: Int = 0
    var phases: List<PhaseConfig> = emptyList()
    var tetherRadius: Double = 0.0
    var packAlertRadius: Double = 0.0
    var despawnConfig: DespawnConfig? = null

    var onDeath: ((SmartEntity, Damage?) -> Unit)? = null
    var onTick: ((SmartEntity) -> Unit)? = null
    var onDamaged: ((SmartEntity, Damage) -> Unit)? = null
    var onTarget: ((SmartEntity, Entity) -> Unit)? = null

    private var lastPhaseIndex: Int = -1
    private var ambientCounter: Int = 0
    private var despawnCounter: Int = 0
    private var wasDamaged: Boolean = false

    internal var currentTrigger: TriggerType<*>? = null
    internal var currentTriggerPayload: Any? = null

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> triggerPayload(trigger: TriggerType<T>): T? =
        if (currentTrigger == trigger) currentTriggerPayload as? T else null

    init {
        getAttribute(Attribute.MAX_HEALTH).baseValue = baseHealth.toDouble()
        health = baseHealth
        getAttribute(Attribute.MOVEMENT_SPEED).baseValue = baseSpeed
        getAttribute(Attribute.ATTACK_DAMAGE).baseValue = baseAttack
        if (knockbackResistance > 0.0) {
            getAttribute(Attribute.KNOCKBACK_RESISTANCE).baseValue = knockbackResistance
        }
    }

    override fun update(time: Long) {
        super.update(time)
        if (dying) {
            if (--deathTicksRemaining <= 0) finalizeDeath()
            return
        }
        if (iframeTicksRemaining > 0) iframeTicksRemaining--
        threatTable?.let { table ->
            if (threatDecayPerSecond > 0f) table.decay(threatDecayPerSecond / 20f)
        }
        tickCombatState()
        tickRegen()
        behaviorGroup.tick(this)
        tickAmbientSound()
        tickPhases()
        tickTether()
        tickDespawn()
        onTick?.invoke(this)
    }

    private fun tickCombatState() {
        val now = inCombat
        if (now != lastInCombat) {
            lastInCombat = now
            fire(if (now) Triggers.ON_COMBAT_ENTER else Triggers.ON_COMBAT_EXIT)
        }
    }

    private fun tickRegen() {
        if (regenAmount <= 0f || regenIntervalTicks <= 0) return
        if (!regenInCombat && inCombat) {
            regenCounter = 0
            return
        }
        if (++regenCounter < regenIntervalTicks) return
        regenCounter = 0
        val maxHp = getAttribute(Attribute.MAX_HEALTH).value.toFloat()
        if (health >= maxHp) return
        health = (health + regenAmount).coerceAtMost(maxHp)
    }

    val iframesActive: Boolean get() = iframeTicksRemaining > 0
    val iframesRemaining: Int get() = iframeTicksRemaining

    val inCombat: Boolean get() {
        val now = System.currentTimeMillis()
        val takenAt = memory.get(MemoryKeys.LAST_DAMAGE_TIME) ?: 0L
        val dealtAt = memory.get(MemoryKeys.LAST_DAMAGE_DEALT_TIME) ?: 0L
        val mostRecent = maxOf(takenAt, dealtAt)
        return mostRecent != 0L && (now - mostRecent) < inCombatTimeoutMs
    }

    val lastDamageTakenAt: Long? get() = memory.get(MemoryKeys.LAST_DAMAGE_TIME)
    val lastDamageDealtAt: Long? get() = memory.get(MemoryKeys.LAST_DAMAGE_DEALT_TIME)
    val msSinceDamageTaken: Long? get() = lastDamageTakenAt?.let { System.currentTimeMillis() - it }
    val msSinceDamageDealt: Long? get() = lastDamageDealtAt?.let { System.currentTimeMillis() - it }
    val damagedThisUpdate: Boolean get() = wasDamaged

    fun memorySnapshot(): Map<String, Any> = memory.snapshot()

    override fun addEffect(potion: Potion) {
        if (effectImmunities.contains(potion.effect())) return
        val resistance = effectResistances[potion.effect()]
        if (resistance != null) {
            if (Random.nextFloat() > resistance.applyChance) return
            val newDuration = (potion.duration() * resistance.durationMultiplier).toInt()
            if (newDuration <= 0) return
            val newAmplifier = (potion.amplifier() + resistance.amplifierAdjustment).coerceAtLeast(0)
            super.addEffect(Potion(potion.effect(), newAmplifier, newDuration))
            return
        }
        super.addEffect(potion)
    }

    override fun updateNewViewer(player: Player) {
        super.updateNewViewer(player)
        modeledEntity?.show(player)
    }

    override fun updateOldViewer(player: Player) {
        super.updateOldViewer(player)
        modeledEntity?.hide(player)
    }

    override fun damage(damage: Damage): Boolean {
        if (iframeTicksRemaining > 0) return false
        val element = DamageElements.resolve(damage.type)
        val multiplier = damageMultipliers[element]
        val modified = if (multiplier != null) {
            if (multiplier <= 0f) return false
            Damage.fromEntity(damage.attacker ?: this, damage.amount * multiplier)
        } else damage

        val result = super.damage(modified)
        if (result) {
            wasDamaged = true
            if (iframesAfterDamageTicks > 0) iframeTicksRemaining = iframesAfterDamageTicks
            sounds.hurt?.let { playEntitySound(it) }
            onDamaged?.invoke(this, modified)

            if (packAlertRadius > 0.0) alertPack(modified)

            memory.set(MemoryKeys.LAST_DAMAGE_TIME, System.currentTimeMillis())
            modified.attacker?.let {
                memory.set(MemoryKeys.LAST_ATTACKER, it)
                threatTable?.add(it, modified.amount * threatDamageWeight)
            }
            fire(Triggers.ON_DAMAGED, modified)
        }
        return result
    }

    fun fire(trigger: TriggerType<*>) {
        behaviorGroup.fire(this, trigger)
    }

    fun <T : Any> fire(trigger: TriggerType<T>, payload: T) {
        behaviorGroup.fire(this, trigger, payload)
    }

    override fun kill() {
        if (dying) return
        if (deathAnimationTicks <= 0) {
            sounds.death?.let { playEntitySound(it) }
            dropLoot()
            onDeath?.invoke(this, null)
            super.kill()
            return
        }
        dying = true
        deathTicksRemaining = deathAnimationTicks
        isInvulnerable = true
        behaviorGroup.stopAll(this)
        deathAnimation?.let { playAnimation(it) }
        sounds.death?.let { playEntitySound(it) }
    }

    val isDying: Boolean get() = dying

    private fun finalizeDeath() {
        dropLoot()
        onDeath?.invoke(this, null)
        super.kill()
    }

    fun setupModel(): ModeledEntity? {
        val name = modelName ?: return null
        val blueprint = ModelEngine.blueprintOrNull(name) ?: return null
        setBoundingBox(blueprint.hitboxWidth.toDouble(), blueprint.hitboxHeight.toDouble(), blueprint.hitboxWidth.toDouble())
        val modeled = ModelEngine.createModeledEntity(this)
        modeled.addModel(name, blueprint)
        modeledEntity = modeled
        viewers.forEach { modeled.show(it) }
        return modeled
    }

    fun model(): ActiveModel? {
        val name = modelName ?: return null
        return modeledEntity?.model(name)
    }

    fun playAnimation(name: String, lerpIn: Float = 0.2f, lerpOut: Float = 0.2f, speed: Float = 1f) {
        model()?.playAnimation(name, lerpIn, lerpOut, speed)
    }

    fun stopAnimation(name: String) {
        model()?.stopAnimation(name)
    }

    fun playEntitySound(event: SoundEvent) {
        instance?.playSound(
            Sound.sound(event.key(), Sound.Source.HOSTILE, sounds.volume, sounds.pitch),
            position.x(), position.y(), position.z(),
        )
    }

    fun healthPercent(): Float {
        val maxHp = getAttribute(Attribute.MAX_HEALTH).value
        return if (maxHp > 0) (health / maxHp).toFloat() else 0f
    }

    private fun tickAmbientSound() {
        val ambient = sounds.ambient ?: return
        if (++ambientCounter >= sounds.ambientPeriod) {
            ambientCounter = 0
            playEntitySound(ambient)
        }
    }

    private fun tickPhases() {
        if (phases.isEmpty()) return
        val pct = healthPercent()
        for (i in phases.indices) {
            if (pct <= phases[i].threshold && i > lastPhaseIndex) {
                lastPhaseIndex = i
                memory.set(MemoryKeys.PHASE, i)
                phases[i].onEnter(this)
                behaviorGroup.cullPhaseInvalid(this)
                fire(Triggers.ON_PHASE_CHANGED)
            }
        }
    }

    private fun tickTether() {
        if (tetherRadius <= 0.0) return
        val home = memory.get(MemoryKeys.HOME_POSITION) ?: return
        if (position.distance(home) > tetherRadius) {
            memory.set(MemoryKeys.MOVE_TARGET, home)
            memory.clear(MemoryKeys.ATTACK_TARGET)
        }
    }

    private fun tickDespawn() {
        val config = despawnConfig ?: return
        if (config.persistAfterDamage && wasDamaged) return
        if (++despawnCounter < config.checkPeriod) return
        despawnCounter = 0
        val instance = instance ?: return
        val hasNearby = instance.getNearbyEntities(position, config.noPlayerRadius).any { it is Player }
        if (!hasNearby) remove()
    }

    private fun alertPack(damage: Damage) {
        val instance = instance ?: return
        val attacker = damage.attacker ?: return
        pack()?.set(MemoryKeys.LAST_KNOWN_POSITION, attacker.position)
        instance.getNearbyEntities(position, packAlertRadius)
            .filterIsInstance<SmartEntity>()
            .filter { it !== this && it.entityType == entityType }
            .forEach { ally ->
                ally.memory.set(MemoryKeys.ATTACK_TARGET, attacker)
                ally.memory.set(MemoryKeys.PANIC_TICKS, 60)
                ally.fire(Triggers.ON_ALLY_DAMAGED, attacker)
            }
    }

    fun pack(): PackBlackboard? = PackBlackboards.forEntity(this)

    private fun dropLoot() {
        if (lootTable.isEmpty()) return
        val instance = instance ?: return
        for (entry in lootTable) {
            if (Random.nextFloat() > entry.chance) continue
            val count = if (entry.minCount == entry.maxCount) entry.minCount
            else Random.nextInt(entry.minCount, entry.maxCount + 1)
            if (count <= 0) continue
            val item = entry.item.withAmount(count)
            val drop = ItemEntity(item)
            val offset = Vec(
                Random.nextDouble(-0.3, 0.3),
                0.3,
                Random.nextDouble(-0.3, 0.3),
            )
            drop.setInstance(instance, position.add(0.0, 0.5, 0.0))
            drop.velocity = offset.mul(5.0)
        }
    }

    override fun remove() {
        behaviorGroup.stopAll(this)
        modeledEntity?.let { ModelEngine.removeModeledEntity(it.owner) }
        modeledEntity = null
        memory.clearAll()
        EntityNamedCooldown.resetAll(this)
        super.remove()
    }
}

fun SmartEntity.isOnCooldown(name: String): Boolean = !EntityNamedCooldown.check(this, name)
fun SmartEntity.useCooldown(name: String): Boolean = EntityNamedCooldown.tryUse(this, name)
fun SmartEntity.useCooldown(name: String, duration: Duration): Boolean =
    EntityNamedCooldown.tryUseFor(this, name, duration)
fun SmartEntity.cooldownRemaining(name: String): Duration = EntityNamedCooldown.remaining(this, name)
fun SmartEntity.resetCooldown(name: String) = EntityNamedCooldown.reset(this, name)
