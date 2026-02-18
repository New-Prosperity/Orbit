package me.nebula.orbit.mechanic.phantom

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val INSOMNIA_TICKS_TAG = Tag.Integer("mechanic:phantom:insomnia_ticks").defaultValue(0)
private val LAST_SLEEP_TAG = Tag.Long("mechanic:phantom:last_sleep").defaultValue(0L)
private val PHANTOM_TARGET_TAG = Tag.Integer("mechanic:phantom:target_id").defaultValue(-1)
private val SWOOP_PHASE_TAG = Tag.Integer("mechanic:phantom:swoop_phase").defaultValue(0)

private const val INSOMNIA_THRESHOLD = 72000
private const val SPAWN_INTERVAL_TICKS = 1200
private const val ATTACK_DAMAGE = 4f
private const val ATTACK_RANGE = 2.0
private const val SWOOP_SPEED = 20.0

class PhantomModule : OrbitModule("phantom") {

    private var tickTask: Task? = null
    private val trackedPhantoms: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(20))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedPhantoms.forEach { it.remove() }
        trackedPhantoms.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedPhantoms.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            if (player.isDead || player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return@forEach

            val insomnia = player.getTag(INSOMNIA_TICKS_TAG) + 20
            player.setTag(INSOMNIA_TICKS_TAG, insomnia)

            val instance = player.instance ?: return@forEach
            val time = instance.time % 24000

            if (insomnia >= INSOMNIA_THRESHOLD && time in 13000..23000) {
                if (insomnia % SPAWN_INTERVAL_TICKS < 20) {
                    spawnPhantom(player)
                }
            }
        }

        trackedPhantoms.forEach { phantom ->
            if (phantom.isRemoved) return@forEach
            updatePhantom(phantom)
        }
    }

    private fun spawnPhantom(player: Player) {
        val instance = player.instance ?: return
        val phantom = Entity(EntityType.PHANTOM)
        phantom.setNoGravity(true)
        phantom.setTag(PHANTOM_TARGET_TAG, player.entityId)
        phantom.setTag(SWOOP_PHASE_TAG, 0)

        val spawnPos = Pos(
            player.position.x() + Random.nextDouble(-10.0, 10.0),
            player.position.y() + Random.nextDouble(20.0, 30.0),
            player.position.z() + Random.nextDouble(-10.0, 10.0),
        )
        phantom.setInstance(instance, spawnPos)
        trackedPhantoms.add(phantom)

        phantom.scheduler().buildTask {
            phantom.remove()
            trackedPhantoms.remove(phantom)
        }.delay(TaskSchedule.minutes(2)).schedule()
    }

    private fun updatePhantom(phantom: Entity) {
        val instance = phantom.instance ?: return
        val targetId = phantom.getTag(PHANTOM_TARGET_TAG)
        val target = instance.entities.firstOrNull { it.entityId == targetId } as? Player ?: return

        if (target.isDead || target.isRemoved) {
            phantom.remove()
            return
        }

        val phase = phantom.getTag(SWOOP_PHASE_TAG)
        val targetPos = target.position.add(0.0, target.eyeHeight, 0.0)
        val distance = phantom.position.distance(targetPos)

        if (phase == 0) {
            val circlePos = Pos(
                target.position.x() + Random.nextDouble(-8.0, 8.0),
                target.position.y() + Random.nextDouble(10.0, 15.0),
                target.position.z() + Random.nextDouble(-8.0, 8.0),
            )
            val direction = circlePos.asVec().sub(phantom.position.asVec()).normalize()
            phantom.velocity = direction.mul(SWOOP_SPEED * 0.5)

            if (Random.nextInt(5) == 0) {
                phantom.setTag(SWOOP_PHASE_TAG, 1)
            }
        } else {
            val direction = targetPos.asVec().sub(phantom.position.asVec()).normalize()
            phantom.velocity = direction.mul(SWOOP_SPEED)

            if (distance <= ATTACK_RANGE) {
                target.damage(DamageType.MOB_ATTACK, ATTACK_DAMAGE)
                phantom.setTag(SWOOP_PHASE_TAG, 0)
            }

            if (phantom.position.y() < target.position.y() - 2.0) {
                phantom.setTag(SWOOP_PHASE_TAG, 0)
            }
        }
    }

    fun resetInsomnia(player: Player) {
        player.setTag(INSOMNIA_TICKS_TAG, 0)
        player.setTag(LAST_SLEEP_TAG, System.currentTimeMillis())
    }
}
