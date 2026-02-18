package me.nebula.orbit.mechanic.skull

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private data class SkullKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val SKULL_OWNER_TAG = Tag.String("mechanic:skull:owner_uuid")

private val SKULL_BLOCKS = setOf(
    "minecraft:player_head",
    "minecraft:player_wall_head",
    "minecraft:skeleton_skull",
    "minecraft:skeleton_wall_skull",
    "minecraft:wither_skeleton_skull",
    "minecraft:wither_skeleton_wall_skull",
    "minecraft:zombie_head",
    "minecraft:zombie_wall_head",
    "minecraft:creeper_head",
    "minecraft:creeper_wall_head",
    "minecraft:piglin_head",
    "minecraft:piglin_wall_head",
    "minecraft:dragon_head",
    "minecraft:dragon_wall_head",
)

private val SKULL_DROP_MATERIALS = mapOf(
    "minecraft:player_head" to Material.PLAYER_HEAD,
    "minecraft:player_wall_head" to Material.PLAYER_HEAD,
    "minecraft:skeleton_skull" to Material.SKELETON_SKULL,
    "minecraft:skeleton_wall_skull" to Material.SKELETON_SKULL,
    "minecraft:wither_skeleton_skull" to Material.WITHER_SKELETON_SKULL,
    "minecraft:wither_skeleton_wall_skull" to Material.WITHER_SKELETON_SKULL,
    "minecraft:zombie_head" to Material.ZOMBIE_HEAD,
    "minecraft:zombie_wall_head" to Material.ZOMBIE_HEAD,
    "minecraft:creeper_head" to Material.CREEPER_HEAD,
    "minecraft:creeper_wall_head" to Material.CREEPER_HEAD,
    "minecraft:piglin_head" to Material.PIGLIN_HEAD,
    "minecraft:piglin_wall_head" to Material.PIGLIN_HEAD,
    "minecraft:dragon_head" to Material.DRAGON_HEAD,
    "minecraft:dragon_wall_head" to Material.DRAGON_HEAD,
)

class SkullModule : OrbitModule("skull") {

    private val skullOwners = ConcurrentHashMap<SkullKey, UUID>()

    override fun onEnable() {
        super.onEnable()
        skullOwners.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in SKULL_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = SkullKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val item = event.player.getItemInHand(event.hand)
            val ownerString = item.getTag(SKULL_OWNER_TAG)
            if (ownerString != null) {
                runCatching { UUID.fromString(ownerString) }.getOrNull()?.let { uuid ->
                    skullOwners[key] = uuid
                }
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() !in SKULL_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = SkullKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val dropMaterial = SKULL_DROP_MATERIALS[event.block.name()] ?: return@addListener
            val ownerUuid = skullOwners.remove(key)

            var drop = ItemStack.of(dropMaterial)
            if (ownerUuid != null && dropMaterial == Material.PLAYER_HEAD) {
                drop = drop.withTag(SKULL_OWNER_TAG, ownerUuid.toString())
            }

            val itemEntity = ItemEntity(drop)
            itemEntity.setPickupDelay(Duration.ofMillis(500))
            itemEntity.setInstance(instance, pos.add(0.5, 0.5, 0.5))

            itemEntity.scheduler().buildTask { itemEntity.remove() }
                .delay(net.minestom.server.timer.TaskSchedule.minutes(5))
                .schedule()
        }
    }

    override fun onDisable() {
        skullOwners.clear()
        super.onDisable()
    }
}
