package me.nebula.orbit.mechanic.pandabehavior

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val GENE_TAG = Tag.String("mechanic:panda:gene").defaultValue("normal")
private val AGGRO_TARGET_TAG = Tag.Integer("mechanic:panda:aggro_target").defaultValue(-1)
private val LAST_ACTION_TAG = Tag.Long("mechanic:panda:last_action").defaultValue(0L)

private const val SCAN_INTERVAL_TICKS = 20
private const val ACTION_COOLDOWN_MS = 3000L
private const val AGGRO_DAMAGE = 6f
private const val AGGRO_RANGE = 2.5

private val PANDA_GENES = listOf("normal", "lazy", "worried", "playful", "aggressive", "weak", "brown")

class PandaBehaviorModule : OrbitModule("panda-behavior") {

    private var tickTask: Task? = null
    private val trackedPandas: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val entity = event.entity
            if (entity.entityType != EntityType.PANDA) return@addListener
            if (entity.getTag(GENE_TAG) != "aggressive") return@addListener

            val attacker = event.damage.attacker ?: return@addListener
            entity.setTag(AGGRO_TARGET_TAG, attacker.entityId)
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(SCAN_INTERVAL_TICKS))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedPandas.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedPandas.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.PANDA) return@entityLoop
                if (entity.getTag(GENE_TAG) == "normal" && Random.nextInt(10) == 0) {
                    entity.setTag(GENE_TAG, PANDA_GENES[Random.nextInt(PANDA_GENES.size)])
                }
                trackedPandas.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedPandas.forEach { panda ->
            if (panda.isRemoved) return@forEach

            val lastAction = panda.getTag(LAST_ACTION_TAG)
            if (now - lastAction < ACTION_COOLDOWN_MS) return@forEach

            when (panda.getTag(GENE_TAG)) {
                "lazy" -> performLazyRoll(panda, now)
                "playful" -> performPlayfulSomersault(panda, now)
                "aggressive" -> performAggressiveAttack(panda, now)
            }
        }
    }

    private fun performLazyRoll(panda: Entity, now: Long) {
        if (Random.nextInt(5) != 0) return
        panda.setTag(LAST_ACTION_TAG, now)

        val rollDir = Vec(
            Random.nextDouble(-1.0, 1.0),
            0.0,
            Random.nextDouble(-1.0, 1.0),
        )
        if (rollDir.length() > 0.1) {
            panda.velocity = rollDir.normalize().mul(6.0)
        }
    }

    private fun performPlayfulSomersault(panda: Entity, now: Long) {
        if (Random.nextInt(8) != 0) return
        panda.setTag(LAST_ACTION_TAG, now)

        panda.velocity = Vec(
            Random.nextDouble(-3.0, 3.0),
            8.0,
            Random.nextDouble(-3.0, 3.0),
        )
    }

    private fun performAggressiveAttack(panda: Entity, now: Long) {
        val targetId = panda.getTag(AGGRO_TARGET_TAG)
        if (targetId < 0) return

        val instance = panda.instance ?: return
        val target = instance.entities.firstOrNull { it.entityId == targetId && !it.isRemoved }
        if (target == null) {
            panda.setTag(AGGRO_TARGET_TAG, -1)
            return
        }

        val distance = panda.position.distanceSquared(target.position)
        if (distance <= AGGRO_RANGE * AGGRO_RANGE) {
            panda.setTag(LAST_ACTION_TAG, now)
            if (target is LivingEntity) {
                target.damage(DamageType.MOB_ATTACK, AGGRO_DAMAGE)
            }
            panda.setTag(AGGRO_TARGET_TAG, -1)
        } else if (distance < 16.0 * 16.0) {
            val direction = target.position.asVec().sub(panda.position.asVec())
            if (direction.length() > 0.1) {
                panda.velocity = direction.normalize().mul(14.0)
            }
        } else {
            panda.setTag(AGGRO_TARGET_TAG, -1)
        }
    }
}
