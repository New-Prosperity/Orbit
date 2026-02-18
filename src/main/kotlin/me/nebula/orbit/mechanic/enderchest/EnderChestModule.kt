package me.nebula.orbit.mechanic.enderchest

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.sound.SoundEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class EnderChestModule : OrbitModule("ender-chest") {

    private val enderChests = ConcurrentHashMap<UUID, Inventory>()

    override fun onEnable() {
        super.onEnable()

        onPlayerDisconnect { enderChests.remove(it.uuid) }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:ender_chest") return@addListener

            val inventory = enderChests.computeIfAbsent(event.player.uuid) {
                Inventory(InventoryType.CHEST_3_ROW, event.player.translate("orbit.mechanic.ender_chest.title"))
            }
            event.player.openInventory(inventory)

            val pos = event.blockPosition
            event.player.instance?.playSound(
                Sound.sound(SoundEvent.BLOCK_ENDER_CHEST_OPEN.key(), Sound.Source.BLOCK, 0.5f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }

    override fun onDisable() {
        enderChests.clear()
        super.onDisable()
    }
}
