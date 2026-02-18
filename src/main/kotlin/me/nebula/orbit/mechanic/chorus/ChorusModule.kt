package me.nebula.orbit.mechanic.chorus

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.kyori.adventure.sound.Sound
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import kotlin.random.Random

class ChorusModule : OrbitModule("chorus") {

    private val cooldownTag = Tag.Long("mechanic:chorus:cooldown")

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.itemStack.material() != Material.CHORUS_FRUIT) return@addListener

            val player = event.player
            if (player.gameMode == GameMode.SPECTATOR) return@addListener

            val now = System.currentTimeMillis()
            val lastUse = player.getTag(cooldownTag) ?: 0L
            if (now - lastUse < 1000L) return@addListener

            val instance = player.instance ?: return@addListener

            repeat(16) {
                val x = player.position.x() + (Random.nextDouble() - 0.5) * 16.0
                val y = (player.position.y() + (Random.nextDouble() - 0.5) * 16.0).coerceIn(-64.0, 319.0)
                val z = player.position.z() + (Random.nextDouble() - 0.5) * 16.0

                val target = Vec(x, y, z)
                if (isSafeLocation(instance, target)) {
                    player.teleport(player.position.withCoord(x, y, z))
                    player.playSound(Sound.sound(
                        SoundEvent.ENTITY_ENDERMAN_TELEPORT.key(),
                        Sound.Source.PLAYER,
                        1f, 1f,
                    ))
                    player.setTag(cooldownTag, now)

                    val hand = event.hand
                    val slot = if (hand == net.minestom.server.entity.PlayerHand.MAIN) player.heldSlot.toInt() else 45
                    val current = player.inventory.getItemStack(slot)
                    if (current.amount() > 1) {
                        player.inventory.setItemStack(slot, current.withAmount(current.amount() - 1))
                    } else {
                        player.inventory.setItemStack(slot, net.minestom.server.item.ItemStack.AIR)
                    }
                    return@addListener
                }
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() != "minecraft:chorus_plant" && event.block.name() != "minecraft:chorus_flower") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val above = Vec(pos.x().toDouble(), pos.y() + 1.0, pos.z().toDouble())
            val aboveBlock = instance.getBlock(above)
            if (aboveBlock.name() == "minecraft:chorus_plant" || aboveBlock.name() == "minecraft:chorus_flower") {
                instance.setBlock(above, Block.AIR)
            }
        }
    }

    private fun isSafeLocation(instance: Instance, pos: Vec): Boolean {
        val feet = instance.getBlock(pos)
        val head = instance.getBlock(Vec(pos.x(), pos.y() + 1.0, pos.z()))
        val below = instance.getBlock(Vec(pos.x(), pos.y() - 1.0, pos.z()))
        return feet.isAir && head.isAir && !below.isAir
    }
}
