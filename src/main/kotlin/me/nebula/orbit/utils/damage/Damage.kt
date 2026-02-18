package me.nebula.orbit.utils.damage

import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.instance.Instance
import net.minestom.server.registry.RegistryKey
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class DamageRecord(
    val victimUuid: UUID,
    val attackerUuid: UUID?,
    val amount: Float,
    val type: DamageType,
    val timestamp: Long = System.currentTimeMillis(),
)

object DamageTracker {

    private val history = ConcurrentHashMap<UUID, MutableList<DamageRecord>>()
    private const val MAX_HISTORY = 50

    fun record(victim: LivingEntity, attacker: Entity?, amount: Float, type: DamageType) {
        val uuid = if (victim is Player) victim.uuid else return
        val attackerUuid = if (attacker is Player) attacker.uuid else null
        val record = DamageRecord(uuid, attackerUuid, amount, type)
        history.getOrPut(uuid) { mutableListOf() }.apply {
            add(record)
            if (size > MAX_HISTORY) removeFirst()
        }
    }

    fun getHistory(player: Player): List<DamageRecord> =
        history[player.uuid]?.toList() ?: emptyList()

    fun getLastDamager(player: Player): UUID? =
        history[player.uuid]?.lastOrNull { it.attackerUuid != null }?.attackerUuid

    fun getRecentDamage(player: Player, withinMs: Long = 5000L): List<DamageRecord> {
        val cutoff = System.currentTimeMillis() - withinMs
        return history[player.uuid]?.filter { it.timestamp >= cutoff } ?: emptyList()
    }

    fun getTotalDamage(player: Player, withinMs: Long = 5000L): Float =
        getRecentDamage(player, withinMs).sumOf { it.amount.toDouble() }.toFloat()

    fun clear(player: Player) {
        history.remove(player.uuid)
    }
}

fun Player.damageHistory(): List<DamageRecord> = DamageTracker.getHistory(this)
fun Player.lastDamager(): UUID? = DamageTracker.getLastDamager(this)
fun Player.recentDamage(withinMs: Long = 5000L): List<DamageRecord> = DamageTracker.getRecentDamage(this, withinMs)

private val miniMessage = MiniMessage.miniMessage()

object DamageIndicator {

    private var format: (Float) -> String = { damage ->
        "<red>%.1f".format(damage)
    }
    private var lifetimeTicks = 20
    private var riseSpeed = 0.05

    fun configure(
        format: ((Float) -> String)? = null,
        lifetimeTicks: Int? = null,
        riseSpeed: Double? = null,
    ) {
        format?.let { this.format = it }
        lifetimeTicks?.let { this.lifetimeTicks = it }
        riseSpeed?.let { this.riseSpeed = it }
    }

    fun spawn(instance: Instance, position: Pos, damage: Float) {
        val text = miniMessage.deserialize(format(damage))
        val offsetX = (Random.nextDouble() - 0.5) * 0.6
        val offsetZ = (Random.nextDouble() - 0.5) * 0.6
        val spawnPos = position.add(offsetX, 0.5, offsetZ)

        val entity = Entity(EntityType.TEXT_DISPLAY)
        val meta = entity.entityMeta as TextDisplayMeta
        meta.text = text
        meta.setHasNoGravity(true)

        entity.setInstance(instance, spawnPos)

        var ticks = 0
        entity.scheduler().buildTask {
            ticks++
            if (ticks >= lifetimeTicks) {
                entity.remove()
                return@buildTask
            }
            entity.teleport(entity.position.add(0.0, riseSpeed, 0.0))
        }.repeat(TaskSchedule.tick(1)).schedule()
    }
}

enum class DamageSource {
    MELEE,
    PROJECTILE,
    FALL,
    FIRE,
    EXPLOSION,
    MAGIC,
    VOID,
    ALL,
}

private data class MultiplierKey(val uuid: UUID, val source: DamageSource)

object DamageMultiplierManager {

    private val multipliers = ConcurrentHashMap<MultiplierKey, Double>()
    private val eventNode = EventNode.all("damage-multiplier")
    @Volatile private var installed = false

    fun install() {
        if (installed) return
        installed = true

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            val source = classifyDamage(event.damage.type)
            val multiplier = getEffectiveMultiplier(player.uuid, source)
            if (multiplier != 1.0) {
                event.damage.amount = (event.damage.amount * multiplier).toFloat()
            }
        }

        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
    }

    fun uninstall() {
        if (!installed) return
        installed = false
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
        multipliers.clear()
    }

    fun setMultiplier(player: Player, source: DamageSource, multiplier: Double) {
        require(multiplier >= 0.0) { "Multiplier must be non-negative" }
        multipliers[MultiplierKey(player.uuid, source)] = multiplier
    }

    fun getMultiplier(player: Player, source: DamageSource): Double =
        multipliers[MultiplierKey(player.uuid, source)] ?: 1.0

    fun removeMultiplier(player: Player, source: DamageSource) {
        multipliers.remove(MultiplierKey(player.uuid, source))
    }

    fun clearPlayer(player: Player) {
        DamageSource.entries.forEach { source ->
            multipliers.remove(MultiplierKey(player.uuid, source))
        }
    }

    fun clearAll() = multipliers.clear()

    private fun getEffectiveMultiplier(uuid: UUID, source: DamageSource): Double {
        val specific = multipliers[MultiplierKey(uuid, source)]
        val all = multipliers[MultiplierKey(uuid, DamageSource.ALL)]
        return when {
            specific != null && all != null -> specific * all
            specific != null -> specific
            all != null -> all
            else -> 1.0
        }
    }

    private fun classifyDamage(type: RegistryKey<DamageType>): DamageSource = when (type) {
        DamageType.PLAYER_ATTACK, DamageType.MOB_ATTACK -> DamageSource.MELEE
        DamageType.ARROW, DamageType.TRIDENT -> DamageSource.PROJECTILE
        DamageType.FALL -> DamageSource.FALL
        DamageType.ON_FIRE, DamageType.IN_FIRE, DamageType.LAVA -> DamageSource.FIRE
        DamageType.EXPLOSION, DamageType.PLAYER_EXPLOSION -> DamageSource.EXPLOSION
        DamageType.MAGIC, DamageType.INDIRECT_MAGIC -> DamageSource.MAGIC
        DamageType.OUT_OF_WORLD -> DamageSource.VOID
        else -> DamageSource.ALL
    }
}

fun Player.setDamageMultiplier(source: DamageSource, multiplier: Double) {
    DamageMultiplierManager.setMultiplier(this, source, multiplier)
}

fun Player.getDamageMultiplier(source: DamageSource): Double =
    DamageMultiplierManager.getMultiplier(this, source)

fun Player.removeDamageMultiplier(source: DamageSource) {
    DamageMultiplierManager.removeMultiplier(this, source)
}

fun Player.clearDamageMultipliers() {
    DamageMultiplierManager.clearPlayer(this)
}
