package me.nebula.orbit.mechanic.cattaming

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val TAMED_TAG = Tag.Boolean("mechanic:cat_taming:tamed").defaultValue(false)
private val OWNER_UUID_TAG = Tag.String("mechanic:cat_taming:owner")
private val SITTING_TAG = Tag.Boolean("mechanic:cat_taming:sitting").defaultValue(false)

private const val TAME_CHANCE = 0.33
private const val FOLLOW_RANGE = 12.0
private const val FOLLOW_SPEED = 14.0
private const val SCAN_INTERVAL_TICKS = 10

private val TAME_FOODS = setOf(Material.COD, Material.SALMON)

class CatTamingModule : OrbitModule("cat-taming") {

    private var tickTask: Task? = null
    private val tamedCats: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            val entity = event.target
            if (entity.entityType != EntityType.CAT) return@addListener
            val player = event.player

            if (entity.getTag(TAMED_TAG)) {
                if (entity.getTag(OWNER_UUID_TAG) == player.uuid.toString() && player.isSneaking) {
                    val sitting = !entity.getTag(SITTING_TAG)
                    entity.setTag(SITTING_TAG, sitting)
                }
                return@addListener
            }

            val heldItem = player.getItemInMainHand()
            if (heldItem.material() !in TAME_FOODS) return@addListener

            consumeItem(player)

            if (Random.nextDouble() < TAME_CHANCE) {
                entity.setTag(TAMED_TAG, true)
                entity.setTag(OWNER_UUID_TAG, player.uuid.toString())
                entity.setTag(SITTING_TAG, false)
                tamedCats.add(entity)
            }
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(SCAN_INTERVAL_TICKS))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        tamedCats.clear()
        super.onDisable()
    }

    private fun tick() {
        tamedCats.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.CAT) return@entityLoop
                if (!entity.getTag(TAMED_TAG)) return@entityLoop
                tamedCats.add(entity)
            }
        }

        tamedCats.forEach { cat ->
            if (cat.isRemoved) return@forEach
            if (cat.getTag(SITTING_TAG)) return@forEach

            val instance = cat.instance ?: return@forEach
            val ownerUuid = cat.getTag(OWNER_UUID_TAG) ?: return@forEach
            val owner = instance.players.firstOrNull { it.uuid.toString() == ownerUuid } ?: return@forEach

            val distance = cat.position.distanceSquared(owner.position)

            if (distance > FOLLOW_RANGE * FOLLOW_RANGE) {
                cat.teleport(owner.position)
            } else if (distance > 4.0) {
                val direction = owner.position.asVec().sub(cat.position.asVec())
                if (direction.length() > 0.1) {
                    cat.velocity = direction.normalize().mul(FOLLOW_SPEED)
                }
            }
        }
    }

    private fun consumeItem(player: Player) {
        val item = player.getItemInMainHand()
        if (item.amount() > 1) {
            player.setItemInMainHand(item.withAmount(item.amount() - 1))
        } else {
            player.setItemInMainHand(ItemStack.AIR)
        }
    }
}
