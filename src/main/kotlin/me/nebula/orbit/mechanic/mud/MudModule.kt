package me.nebula.orbit.mechanic.mud

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private data class MudDryKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val DIRT_BLOCKS = setOf(
    "minecraft:dirt",
    "minecraft:coarse_dirt",
    "minecraft:rooted_dirt",
)

class MudModule : OrbitModule("mud") {

    private val dryingMud = ConcurrentHashMap<MudDryKey, Long>()
    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()
        dryingMud.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val held = event.player.getItemInMainHand()
            if (held.material() != Material.POTION) return@addListener

            val block = event.block
            if (block.name() !in DIRT_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            instance.setBlock(pos, Block.MUD)

            val newCount = held.amount() - 1
            event.player.setItemInMainHand(
                if (newCount <= 0) ItemStack.of(Material.GLASS_BOTTLE)
                else held.withAmount(newCount)
            )
            event.player.inventory.addItemStack(ItemStack.of(Material.GLASS_BOTTLE))

            instance.playSound(
                Sound.sound(SoundEvent.ITEM_BOTTLE_EMPTY.key(), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:mud") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val below = instance.getBlock(pos.add(0, -1, 0))
            if (below.name() == "minecraft:pointed_dripstone") {
                val key = MudDryKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
                dryingMud[key] = System.currentTimeMillis()
            }
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.seconds(10))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        dryingMud.clear()
        super.onDisable()
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val iterator = dryingMud.entries.iterator()
        while (iterator.hasNext()) {
            val (key, placedAt) = iterator.next()
            if (now - placedAt < 60_000L) continue

            val instance = MinecraftServer.getInstanceManager().instances
                .firstOrNull { System.identityHashCode(it) == key.instanceHash }
            if (instance == null) {
                iterator.remove()
                continue
            }

            val block = instance.getBlock(key.x, key.y, key.z)
            if (block.name() != "minecraft:mud") {
                iterator.remove()
                continue
            }

            val below = instance.getBlock(key.x, key.y - 1, key.z)
            if (below.name() != "minecraft:pointed_dripstone") {
                iterator.remove()
                continue
            }

            instance.setBlock(key.x, key.y, key.z, Block.CLAY)
            iterator.remove()
        }
    }
}
