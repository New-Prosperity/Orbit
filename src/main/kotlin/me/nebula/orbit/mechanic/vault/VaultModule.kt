package me.nebula.orbit.mechanic.vault

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class VaultKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val VAULT_REWARDS = listOf(
    Material.DIAMOND to 1..3,
    Material.EMERALD to 2..5,
    Material.GOLDEN_APPLE to 1..2,
    Material.IRON_INGOT to 3..8,
    Material.GOLD_INGOT to 2..6,
    Material.ENDER_PEARL to 1..3,
    Material.ARROW to 8..16,
    Material.EXPERIENCE_BOTTLE to 2..6,
)

class VaultModule : OrbitModule("vault") {

    private val claimedPlayers = ConcurrentHashMap<VaultKey, MutableSet<UUID>>()

    override fun onEnable() {
        super.onEnable()
        claimedPlayers.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:vault") return@addListener

            val player = event.player
            val instance = player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = VaultKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val claimed = claimedPlayers.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }
            if (!claimed.add(player.uuid)) return@addListener

            val (material, range) = VAULT_REWARDS[Random.nextInt(VAULT_REWARDS.size)]
            val amount = Random.nextInt(range.first, range.last + 1)
            player.inventory.addItemStack(ItemStack.of(material, amount))

            instance.playSound(
                Sound.sound(SoundEvent.BLOCK_VAULT_OPEN_SHUTTER.key(), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }

    override fun onDisable() {
        claimedPlayers.clear()
        super.onDisable()
    }
}
