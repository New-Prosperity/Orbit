package me.nebula.orbit.mechanic.noteblock

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.utils.customcontent.block.BlockStateAllocator
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.sound.SoundEvent

private val INSTRUMENT_SOUNDS = mapOf(
    "harp" to SoundEvent.BLOCK_NOTE_BLOCK_HARP,
    "basedrum" to SoundEvent.BLOCK_NOTE_BLOCK_BASEDRUM,
    "snare" to SoundEvent.BLOCK_NOTE_BLOCK_SNARE,
    "hat" to SoundEvent.BLOCK_NOTE_BLOCK_HAT,
    "bass" to SoundEvent.BLOCK_NOTE_BLOCK_BASS,
    "flute" to SoundEvent.BLOCK_NOTE_BLOCK_FLUTE,
    "bell" to SoundEvent.BLOCK_NOTE_BLOCK_BELL,
    "guitar" to SoundEvent.BLOCK_NOTE_BLOCK_GUITAR,
    "chime" to SoundEvent.BLOCK_NOTE_BLOCK_CHIME,
    "xylophone" to SoundEvent.BLOCK_NOTE_BLOCK_XYLOPHONE,
    "iron_xylophone" to SoundEvent.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE,
    "cow_bell" to SoundEvent.BLOCK_NOTE_BLOCK_COW_BELL,
    "didgeridoo" to SoundEvent.BLOCK_NOTE_BLOCK_DIDGERIDOO,
    "bit" to SoundEvent.BLOCK_NOTE_BLOCK_BIT,
    "banjo" to SoundEvent.BLOCK_NOTE_BLOCK_BANJO,
    "pling" to SoundEvent.BLOCK_NOTE_BLOCK_PLING,
)

class NoteBlockModule : OrbitModule("noteblock") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:note_block") return@addListener
            if (BlockStateAllocator.isAllocated(event.block)) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val block = event.block

            val currentNote = block.getProperty("note")?.toIntOrNull() ?: 0
            val newNote = (currentNote + 1) % 25
            val newBlock = block.withProperty("note", newNote.toString())
            instance.setBlock(pos, newBlock)

            val instrument = newBlock.getProperty("instrument") ?: "harp"
            val sound = INSTRUMENT_SOUNDS[instrument] ?: SoundEvent.BLOCK_NOTE_BLOCK_HARP
            val pitch = Math.pow(2.0, (newNote - 12).toDouble() / 12.0).toFloat()

            instance.playSound(
                Sound.sound(sound.key(), Sound.Source.RECORD, 3f, pitch),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }
}
