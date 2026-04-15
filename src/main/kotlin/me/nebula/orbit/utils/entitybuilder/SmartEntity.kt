package me.nebula.orbit.utils.entitybuilder

import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.model.ActiveModel
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.Damage
import net.minestom.server.item.ItemStack
import net.kyori.adventure.sound.Sound
import net.minestom.server.sound.SoundEvent

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
    var fireImmune: Boolean = false
    var damageMultipliers: Map<String, Float> = emptyMap()
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
        behaviorGroup.tick(this)
        tickAmbientSound()
        tickPhases()
        tickTether()
        tickDespawn()
        onTick?.invoke(this)
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
        val typeName = damage.type.key().value()
        if (fireImmune && (typeName == "on_fire" || typeName == "in_fire")) return false

        val multiplier = damageMultipliers[typeName]
        val modified = if (multiplier != null) {
            if (multiplier <= 0f) return false
            Damage.fromEntity(damage.attacker ?: this, damage.amount * multiplier)
        } else damage

        val result = super.damage(modified)
        if (result) {
            wasDamaged = true
            sounds.hurt?.let { playEntitySound(it) }
            onDamaged?.invoke(this, modified)

            if (packAlertRadius > 0.0) alertPack(modified)

            memory.set(MemoryKeys.LAST_DAMAGE_TIME, System.currentTimeMillis())
            modified.attacker?.let { memory.set(MemoryKeys.LAST_ATTACKER, it) }
        }
        return result
    }

    override fun kill() {
        sounds.death?.let { playEntitySound(it) }
        dropLoot()
        onDeath?.invoke(this, null)
        super.kill()
    }

    fun setupModel(): ModeledEntity? {
        val name = modelName ?: return null
        val blueprint = ModelEngine.blueprintOrNull(name) ?: return null
        val modeled = ModelEngine.createModeledEntity(this)
        modeled.addModel(name, blueprint)
        modeledEntity = modeled
        return modeled
    }

    fun model(): ActiveModel? {
        val name = modelName ?: return null
        return modeledEntity?.model(name)
    }

    fun playAnimation(name: String, priority: Int = 1) {
        model()?.animationHandler?.play(name, priority.toFloat())
    }

    fun stopAnimation(name: String) {
        model()?.animationHandler?.stop(name)
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
        instance.getNearbyEntities(position, packAlertRadius)
            .filterIsInstance<SmartEntity>()
            .filter { it !== this && it.entityType == entityType }
            .forEach { ally ->
                ally.memory.set(MemoryKeys.ATTACK_TARGET, attacker)
                ally.memory.set(MemoryKeys.PANIC_TICKS, 60)
            }
    }

    private fun dropLoot() {
        if (lootTable.isEmpty()) return
        val instance = instance ?: return
        for (entry in lootTable) {
            if (kotlin.random.Random.nextFloat() > entry.chance) continue
            val count = if (entry.minCount == entry.maxCount) entry.minCount
            else kotlin.random.Random.nextInt(entry.minCount, entry.maxCount + 1)
            if (count <= 0) continue
            val item = entry.item.withAmount(count)
            val drop = net.minestom.server.entity.ItemEntity(item)
            val offset = Vec(
                kotlin.random.Random.nextDouble(-0.3, 0.3),
                0.3,
                kotlin.random.Random.nextDouble(-0.3, 0.3),
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
        super.remove()
    }
}
