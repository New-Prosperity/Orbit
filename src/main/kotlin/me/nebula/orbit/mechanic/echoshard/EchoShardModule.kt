package me.nebula.orbit.mechanic.echoshard

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

private const val REQUIRED_ECHO_SHARDS = 8

class EchoShardModule : OrbitModule("echo-shard") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:crafting_table") return@addListener

            val player = event.player
            val inventory = player.inventory

            var compassSlot = -1
            var echoShardCount = 0
            val echoSlots = mutableListOf<Int>()

            for (slot in 0 until inventory.size) {
                val item = inventory.getItemStack(slot)
                if (item.material() == Material.COMPASS && compassSlot == -1) {
                    compassSlot = slot
                }
                if (item.material() == Material.ECHO_SHARD) {
                    echoShardCount += item.amount()
                    echoSlots.add(slot)
                }
            }

            if (compassSlot == -1 || echoShardCount < REQUIRED_ECHO_SHARDS) return@addListener

            inventory.setItemStack(compassSlot, ItemStack.AIR)

            var remaining = REQUIRED_ECHO_SHARDS
            for (slot in echoSlots) {
                if (remaining <= 0) break
                val item = inventory.getItemStack(slot)
                val take = minOf(item.amount(), remaining)
                remaining -= take
                val newAmount = item.amount() - take
                inventory.setItemStack(
                    slot,
                    if (newAmount <= 0) ItemStack.AIR else item.withAmount(newAmount)
                )
            }

            inventory.addItemStack(ItemStack.of(Material.RECOVERY_COMPASS))

            val instance = player.instance ?: return@addListener
            val pos = event.blockPosition
            instance.playSound(
                Sound.sound(SoundEvent.BLOCK_SMITHING_TABLE_USE.key(), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }
}
