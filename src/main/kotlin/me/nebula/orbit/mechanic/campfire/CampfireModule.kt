package me.nebula.orbit.mechanic.campfire

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private data class CampfireKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private data class CampfireSlot(val item: Material, val cookTimeMs: Long, val resultItem: Material)

private val CAMPFIRE_RECIPES = mapOf(
    Material.BEEF to Material.COOKED_BEEF,
    Material.PORKCHOP to Material.COOKED_PORKCHOP,
    Material.CHICKEN to Material.COOKED_CHICKEN,
    Material.MUTTON to Material.COOKED_MUTTON,
    Material.RABBIT to Material.COOKED_RABBIT,
    Material.COD to Material.COOKED_COD,
    Material.SALMON to Material.COOKED_SALMON,
    Material.POTATO to Material.BAKED_POTATO,
    Material.KELP to Material.DRIED_KELP,
)

private val SOUL_CAMPFIRE_BLOCKS = setOf("minecraft:soul_campfire")
private val CAMPFIRE_BLOCKS = setOf("minecraft:campfire", "minecraft:soul_campfire")

private const val COOK_TIME_MS = 30_000L
private const val CAMPFIRE_DAMAGE = 1.0f
private const val SOUL_CAMPFIRE_DAMAGE = 2.0f

class CampfireModule : OrbitModule("campfire") {

    private val campfireSlots = ConcurrentHashMap<CampfireKey, MutableList<CampfireSlot>>()
    private val lastDamageTag = Tag.Long("mechanic:campfire:last_damage")

    override fun onEnable() {
        super.onEnable()
        campfireSlots.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            if (block.name() !in CAMPFIRE_BLOCKS) return@addListener
            if (block.getProperty("lit") == "false") return@addListener

            val held = event.player.getItemInMainHand()
            val result = CAMPFIRE_RECIPES[held.material()] ?: return@addListener

            val key = CampfireKey(
                System.identityHashCode(event.player.instance),
                event.blockPosition.blockX(),
                event.blockPosition.blockY(),
                event.blockPosition.blockZ()
            )

            val slots = campfireSlots.getOrPut(key) { mutableListOf() }
            if (slots.size >= 4) return@addListener

            slots += CampfireSlot(held.material(), System.currentTimeMillis(), result)

            val slot = event.player.heldSlot.toInt()
            if (held.amount() > 1) {
                event.player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
            } else {
                event.player.inventory.setItemStack(slot, ItemStack.AIR)
            }
        }

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val feetBlock = instance.getBlock(player.position)
            val belowBlock = instance.getBlock(player.position.sub(0.0, 1.0, 0.0))

            val standingOn = when {
                feetBlock.name() in CAMPFIRE_BLOCKS && feetBlock.getProperty("lit") == "true" -> feetBlock
                belowBlock.name() in CAMPFIRE_BLOCKS && belowBlock.getProperty("lit") == "true" -> belowBlock
                else -> null
            } ?: return@addListener

            val now = System.currentTimeMillis()
            val lastDamage = player.getTag(lastDamageTag) ?: 0L
            if (now - lastDamage < 500L) return@addListener

            val damage = if (standingOn.name() in SOUL_CAMPFIRE_BLOCKS) SOUL_CAMPFIRE_DAMAGE else CAMPFIRE_DAMAGE
            player.damage(Damage(DamageType.IN_FIRE, null, null, null, damage))
            player.setTag(lastDamageTag, now)
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val now = System.currentTimeMillis()
            val iterator = campfireSlots.entries.iterator()
            while (iterator.hasNext()) {
                val (key, slots) = iterator.next()
                val readySlots = slots.filter { now - it.cookTimeMs >= COOK_TIME_MS }
                if (readySlots.isEmpty()) continue

                slots.removeAll(readySlots)
                if (slots.isEmpty()) iterator.remove()

                for (slot in readySlots) {
                    val instance = MinecraftServer.getInstanceManager().instances
                        .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: continue
                    val pos = Pos(key.x + 0.5, key.y + 1.0, key.z + 0.5)
                    val itemEntity = Entity(EntityType.ITEM)
                    itemEntity.setInstance(instance, pos)
                    itemEntity.scheduler().buildTask { itemEntity.remove() }
                        .delay(TaskSchedule.minutes(5)).schedule()
                }
            }
        }.repeat(TaskSchedule.tick(20)).schedule()
    }

    override fun onDisable() {
        campfireSlots.clear()
        super.onDisable()
    }
}
