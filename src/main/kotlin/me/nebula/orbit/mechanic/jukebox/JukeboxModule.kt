package me.nebula.orbit.mechanic.jukebox

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import java.util.concurrent.ConcurrentHashMap

private data class JukeboxKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val DISC_SOUNDS = mapOf(
    Material.MUSIC_DISC_13 to SoundEvent.MUSIC_DISC_13,
    Material.MUSIC_DISC_CAT to SoundEvent.MUSIC_DISC_CAT,
    Material.MUSIC_DISC_BLOCKS to SoundEvent.MUSIC_DISC_BLOCKS,
    Material.MUSIC_DISC_CHIRP to SoundEvent.MUSIC_DISC_CHIRP,
    Material.MUSIC_DISC_FAR to SoundEvent.MUSIC_DISC_FAR,
    Material.MUSIC_DISC_MALL to SoundEvent.MUSIC_DISC_MALL,
    Material.MUSIC_DISC_MELLOHI to SoundEvent.MUSIC_DISC_MELLOHI,
    Material.MUSIC_DISC_STAL to SoundEvent.MUSIC_DISC_STAL,
    Material.MUSIC_DISC_STRAD to SoundEvent.MUSIC_DISC_STRAD,
    Material.MUSIC_DISC_WARD to SoundEvent.MUSIC_DISC_WARD,
    Material.MUSIC_DISC_WAIT to SoundEvent.MUSIC_DISC_WAIT,
    Material.MUSIC_DISC_PIGSTEP to SoundEvent.MUSIC_DISC_PIGSTEP,
)

class JukeboxModule : OrbitModule("jukebox") {

    private val insertedDiscs = ConcurrentHashMap<JukeboxKey, Material>()

    override fun onEnable() {
        super.onEnable()
        insertedDiscs.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:jukebox") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = JukeboxKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val existingDisc = insertedDiscs[key]
            if (existingDisc != null) {
                insertedDiscs.remove(key)
                val item = net.minestom.server.entity.ItemEntity(ItemStack.of(existingDisc))
                item.setPickupDelay(java.time.Duration.ofMillis(500))
                item.setInstance(instance, net.minestom.server.coordinate.Pos(
                    pos.x() + 0.5, pos.y() + 1.0, pos.z() + 0.5,
                ))
                instance.setBlock(pos, event.block.withProperty("has_record", "false"))
                return@addListener
            }

            val held = event.player.getItemInMainHand()
            val soundEvent = DISC_SOUNDS[held.material()] ?: return@addListener

            insertedDiscs[key] = held.material()
            instance.setBlock(pos, event.block.withProperty("has_record", "true"))

            val slot = event.player.heldSlot.toInt()
            if (held.amount() > 1) {
                event.player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
            } else {
                event.player.inventory.setItemStack(slot, ItemStack.AIR)
            }

            instance.playSound(
                Sound.sound(soundEvent.key(), Sound.Source.RECORD, 4f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }

    override fun onDisable() {
        insertedDiscs.clear()
        super.onDisable()
    }
}
