package me.nebula.orbit.utils.mobspawner

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.entity.EntityDeathEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import me.nebula.orbit.utils.entityai.configureAI
import me.nebula.orbit.utils.itembuilder.itemStack
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

data class DropEntry(val item: ItemStack, val chance: Double)

data class EquipmentConfig(
    val helmet: ItemStack?,
    val chestplate: ItemStack?,
    val leggings: ItemStack?,
    val boots: ItemStack?,
    val mainHand: ItemStack?,
)

class SpawnedMob(
    val entity: EntityCreature,
    val drops: List<DropEntry>,
    val onDeathHandler: ((Player?) -> Unit)?,
)

class DropBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val entries = mutableListOf<DropEntry>()

    fun item(material: Material, amount: Int = 1, chance: Double = 1.0) {
        entries.add(DropEntry(ItemStack.of(material, amount), chance))
    }

    fun item(item: ItemStack, chance: Double = 1.0) {
        entries.add(DropEntry(item, chance))
    }
}

class EquipmentBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var helmet: ItemStack? = null
    @PublishedApi internal var chestplate: ItemStack? = null
    @PublishedApi internal var leggings: ItemStack? = null
    @PublishedApi internal var boots: ItemStack? = null
    @PublishedApi internal var mainHand: ItemStack? = null

    fun helmet(item: ItemStack) { helmet = item }
    fun helmet(material: Material) { helmet = ItemStack.of(material) }
    fun chestplate(item: ItemStack) { chestplate = item }
    fun chestplate(material: Material) { chestplate = ItemStack.of(material) }
    fun leggings(item: ItemStack) { leggings = item }
    fun leggings(material: Material) { leggings = ItemStack.of(material) }
    fun boots(item: ItemStack) { boots = item }
    fun boots(material: Material) { boots = ItemStack.of(material) }
    fun mainHand(item: ItemStack) { mainHand = item }
    fun mainHand(material: Material) { mainHand = ItemStack.of(material) }

    @PublishedApi internal fun build(): EquipmentConfig =
        EquipmentConfig(helmet, chestplate, leggings, boots, mainHand)
}

class MobSpawnerBuilder @PublishedApi internal constructor(private val entityType: EntityType) {

    @PublishedApi internal var instance: Instance? = null
    @PublishedApi internal var position: Pos = Pos.ZERO
    @PublishedApi internal var health: Float? = null
    @PublishedApi internal var speed: Double? = null
    @PublishedApi internal var attackDamage: Float? = null
    @PublishedApi internal var hostile: Boolean = true
    @PublishedApi internal var customName: String? = null
    @PublishedApi internal val drops = mutableListOf<DropEntry>()
    @PublishedApi internal var onDeathHandler: ((Player?) -> Unit)? = null
    @PublishedApi internal var equipment: EquipmentConfig? = null
    @PublishedApi internal var onSpawnHandler: ((EntityCreature) -> Unit)? = null

    fun instance(inst: Instance) { instance = inst }
    fun position(pos: Pos) { position = pos }
    fun position(x: Double, y: Double, z: Double) { position = Pos(x, y, z) }
    fun health(hp: Float) { health = hp }
    fun speed(spd: Double) { speed = spd }
    fun attackDamage(dmg: Float) { attackDamage = dmg }
    fun hostile(value: Boolean = true) { hostile = value }
    fun passive() { hostile = false }
    fun customName(name: String) { customName = name }
    fun onDeath(handler: (Player?) -> Unit) { onDeathHandler = handler }
    fun onSpawn(handler: (EntityCreature) -> Unit) { onSpawnHandler = handler }

    inline fun drops(block: DropBuilder.() -> Unit) {
        drops.addAll(DropBuilder().apply(block).entries)
    }

    inline fun equipment(block: EquipmentBuilder.() -> Unit) {
        equipment = EquipmentBuilder().apply(block).build()
    }

    @PublishedApi internal fun build(): SpawnedMob {
        val inst = requireNotNull(instance) { "Instance must be set for mob spawning" }
        val creature = EntityCreature(entityType)

        health?.let {
            creature.getAttribute(Attribute.MAX_HEALTH).baseValue = it.toDouble()
            creature.health = it
        }
        speed?.let { creature.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = it }
        attackDamage?.let { creature.getAttribute(Attribute.ATTACK_DAMAGE).baseValue = it.toDouble() }

        customName?.let {
            creature.customName = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(it)
            creature.isCustomNameVisible = true
        }

        equipment?.let { eq ->
            eq.helmet?.let { creature.helmet = it }
            eq.chestplate?.let { creature.chestplate = it }
            eq.leggings?.let { creature.leggings = it }
            eq.boots?.let { creature.boots = it }
        }

        if (hostile) {
            creature.configureAI { hostile() }
        } else {
            creature.configureAI { passive() }
        }

        creature.setInstance(inst, position)
        onSpawnHandler?.invoke(creature)

        val mob = SpawnedMob(creature, drops.toList(), onDeathHandler)

        if (drops.isNotEmpty() || onDeathHandler != null) {
            creature.eventNode().addListener(EntityDeathEvent::class.java) { event ->
                val killer = (event.entity as? LivingEntity)?.let {
                    MinecraftServer.getConnectionManager().onlinePlayers
                        .minByOrNull { p -> p.position.distance(event.entity.position) }
                }

                drops.forEach { drop ->
                    if (Math.random() <= drop.chance) {
                        val itemEntity = ItemEntity(drop.item)
                        itemEntity.setInstance(inst, event.entity.position)
                    }
                }

                onDeathHandler?.invoke(killer)
            }
        }

        return mob
    }
}

inline fun spawnMob(entityType: EntityType, block: MobSpawnerBuilder.() -> Unit): SpawnedMob =
    MobSpawnerBuilder(entityType).apply(block).build()

data class WaveMobEntry(val entityType: EntityType, val count: Int, val configure: (MobSpawnerBuilder.() -> Unit)?)

class WaveBuilder @PublishedApi internal constructor(val waveNumber: Int) {

    @PublishedApi internal val mobs = mutableListOf<WaveMobEntry>()

    fun mob(entityType: EntityType, count: Int = 1, configure: (MobSpawnerBuilder.() -> Unit)? = null) {
        mobs.add(WaveMobEntry(entityType, count, configure))
    }
}

class WaveSpawner(
    val waves: Map<Int, List<WaveMobEntry>>,
    val instance: Instance,
    val position: Pos,
    val delay: Duration,
    val onWaveStartHandler: ((Int) -> Unit)?,
    val onWaveEndHandler: ((Int) -> Unit)?,
    val onAllWavesCompleteHandler: (() -> Unit)?,
) {

    @Volatile var currentWave: Int = 0
        private set

    @Volatile var isRunning: Boolean = false
        private set

    private val aliveEntities = CopyOnWriteArrayList<EntityCreature>()
    private var task: Task? = null

    fun start() {
        require(!isRunning) { "Wave spawner already running" }
        isRunning = true
        currentWave = 0
        nextWave()
    }

    fun nextWave() {
        currentWave++
        val waveMobs = waves[currentWave]
        if (waveMobs == null) {
            isRunning = false
            onAllWavesCompleteHandler?.invoke()
            return
        }

        onWaveStartHandler?.invoke(currentWave)
        aliveEntities.clear()

        waveMobs.forEach { entry ->
            repeat(entry.count) {
                val builder = MobSpawnerBuilder(entry.entityType).apply {
                    instance(this@WaveSpawner.instance)
                    position(this@WaveSpawner.position)
                }
                entry.configure?.invoke(builder)
                val mob = builder.build()
                aliveEntities.add(mob.entity)

                mob.entity.eventNode().addListener(EntityDeathEvent::class.java) { _ ->
                    aliveEntities.remove(mob.entity)
                }
            }
        }

        task = MinecraftServer.getSchedulerManager().buildTask {
            if (aliveEntities.none { !it.isDead }) {
                task?.cancel()
                onWaveEndHandler?.invoke(currentWave)
                MinecraftServer.getSchedulerManager().buildTask {
                    nextWave()
                }.delay(TaskSchedule.duration(delay)).schedule()
            }
        }.repeat(TaskSchedule.tick(10)).schedule()
    }

    fun stop() {
        isRunning = false
        task?.cancel()
        task = null
        aliveEntities.forEach { if (!it.isRemoved) it.remove() }
        aliveEntities.clear()
    }

    val totalWaves: Int get() = waves.size
}

class WaveSpawnerBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val waves = mutableMapOf<Int, MutableList<WaveMobEntry>>()
    @PublishedApi internal var instance: Instance? = null
    @PublishedApi internal var position: Pos = Pos.ZERO
    @PublishedApi internal var delay: Duration = Duration.ofSeconds(5)
    @PublishedApi internal var onWaveStartHandler: ((Int) -> Unit)? = null
    @PublishedApi internal var onWaveEndHandler: ((Int) -> Unit)? = null
    @PublishedApi internal var onAllWavesCompleteHandler: (() -> Unit)? = null

    fun instance(inst: Instance) { instance = inst }
    fun position(pos: Pos) { position = pos }
    fun delay(duration: Duration) { delay = duration }
    fun onWaveStart(handler: (Int) -> Unit) { onWaveStartHandler = handler }
    fun onWaveEnd(handler: (Int) -> Unit) { onWaveEndHandler = handler }
    fun onAllWavesComplete(handler: () -> Unit) { onAllWavesCompleteHandler = handler }

    inline fun wave(number: Int, block: WaveBuilder.() -> Unit) {
        val builder = WaveBuilder(number).apply(block)
        waves[number] = builder.mobs.toMutableList()
    }

    @PublishedApi internal fun build(): WaveSpawner {
        val inst = requireNotNull(instance) { "Instance must be set for wave spawner" }
        return WaveSpawner(
            waves = waves.mapValues { it.value.toList() },
            instance = inst,
            position = position,
            delay = delay,
            onWaveStartHandler = onWaveStartHandler,
            onWaveEndHandler = onWaveEndHandler,
            onAllWavesCompleteHandler = onAllWavesCompleteHandler,
        )
    }
}

inline fun waveSpawner(block: WaveSpawnerBuilder.() -> Unit): WaveSpawner =
    WaveSpawnerBuilder().apply(block).build()

class MobSpawnerPoint(
    val entityType: EntityType,
    val instance: Instance,
    val position: Pos,
    val interval: Duration,
    val maxAlive: Int = 5,
    val configure: (MobSpawnerBuilder.() -> Unit)? = null,
) {

    private val alive = CopyOnWriteArrayList<EntityCreature>()
    private var task: Task? = null

    fun start() {
        task = MinecraftServer.getSchedulerManager().buildTask {
            alive.removeIf { it.isDead || it.isRemoved }
            if (alive.size >= maxAlive) return@buildTask
            val mob = MobSpawnerBuilder(entityType).apply {
                instance(this@MobSpawnerPoint.instance)
                position(this@MobSpawnerPoint.position)
                configure?.invoke(this)
            }.build()
            alive.add(mob.entity)
        }.repeat(TaskSchedule.duration(interval)).schedule()
    }

    fun stop() {
        task?.cancel()
        task = null
        alive.forEach { if (!it.isRemoved) it.remove() }
        alive.clear()
    }

    val aliveCount: Int get() = alive.count { !it.isDead && !it.isRemoved }
}
