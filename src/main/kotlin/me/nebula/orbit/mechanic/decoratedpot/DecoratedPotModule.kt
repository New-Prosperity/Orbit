package me.nebula.orbit.mechanic.decoratedpot

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.util.concurrent.ConcurrentHashMap

private data class PotKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class DecoratedPotModule : OrbitModule("decorated-pot") {

    private val potContents = ConcurrentHashMap<PotKey, ItemStack>()

    override fun onEnable() {
        super.onEnable()

        potContents.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:decorated_pot") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = PotKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val held = event.player.getItemInMainHand()
            if (held.isAir) {
                val stored = potContents.remove(key) ?: return@addListener
                event.player.inventory.addItemStack(stored)
            } else {
                val existing = potContents[key]
                if (existing != null) return@addListener
                potContents[key] = held.withAmount(1)
                val consumed = held.consume(1)
                event.player.setItemInMainHand(consumed)
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() != "minecraft:decorated_pot") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = PotKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            potContents.remove(key)?.let { stored ->
                val drop = ItemEntity(stored)
                drop.setInstance(instance, net.minestom.server.coordinate.Vec(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5))
                drop.setPickupDelay(java.time.Duration.ofMillis(500))
            }
        }
    }

    override fun onDisable() {
        potContents.clear()
        super.onDisable()
    }
}
