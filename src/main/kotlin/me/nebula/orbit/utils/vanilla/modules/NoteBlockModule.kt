package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.SoundEffectPacket
import net.minestom.server.sound.SoundEvent
import kotlin.math.pow

private val INSTRUMENT_MAP = mapOf(
    "minecraft:oak_planks" to SoundEvent.BLOCK_NOTE_BLOCK_BASS,
    "minecraft:spruce_planks" to SoundEvent.BLOCK_NOTE_BLOCK_BASS,
    "minecraft:birch_planks" to SoundEvent.BLOCK_NOTE_BLOCK_BASS,
    "minecraft:jungle_planks" to SoundEvent.BLOCK_NOTE_BLOCK_BASS,
    "minecraft:acacia_planks" to SoundEvent.BLOCK_NOTE_BLOCK_BASS,
    "minecraft:dark_oak_planks" to SoundEvent.BLOCK_NOTE_BLOCK_BASS,
    "minecraft:sand" to SoundEvent.BLOCK_NOTE_BLOCK_SNARE,
    "minecraft:red_sand" to SoundEvent.BLOCK_NOTE_BLOCK_SNARE,
    "minecraft:gravel" to SoundEvent.BLOCK_NOTE_BLOCK_SNARE,
    "minecraft:glass" to SoundEvent.BLOCK_NOTE_BLOCK_HAT,
    "minecraft:stone" to SoundEvent.BLOCK_NOTE_BLOCK_BASEDRUM,
    "minecraft:cobblestone" to SoundEvent.BLOCK_NOTE_BLOCK_BASEDRUM,
    "minecraft:deepslate" to SoundEvent.BLOCK_NOTE_BLOCK_BASEDRUM,
    "minecraft:gold_block" to SoundEvent.BLOCK_NOTE_BLOCK_BELL,
    "minecraft:clay" to SoundEvent.BLOCK_NOTE_BLOCK_FLUTE,
    "minecraft:packed_ice" to SoundEvent.BLOCK_NOTE_BLOCK_CHIME,
    "minecraft:white_wool" to SoundEvent.BLOCK_NOTE_BLOCK_GUITAR,
    "minecraft:bone_block" to SoundEvent.BLOCK_NOTE_BLOCK_XYLOPHONE,
    "minecraft:iron_block" to SoundEvent.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE,
    "minecraft:soul_sand" to SoundEvent.BLOCK_NOTE_BLOCK_COW_BELL,
    "minecraft:pumpkin" to SoundEvent.BLOCK_NOTE_BLOCK_DIDGERIDOO,
    "minecraft:emerald_block" to SoundEvent.BLOCK_NOTE_BLOCK_BIT,
    "minecraft:hay_block" to SoundEvent.BLOCK_NOTE_BLOCK_BANJO,
    "minecraft:glowstone" to SoundEvent.BLOCK_NOTE_BLOCK_PLING,
)

private val DEFAULT_INSTRUMENT = SoundEvent.BLOCK_NOTE_BLOCK_HARP

object NoteBlockModule : VanillaModule {

    override val id = "note-block"
    override val description = "Note blocks play pitched sounds based on block below. Right-click to change pitch."

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-note-block")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:note_block") return@addListener

            val above = event.instance.getBlock(
                event.blockPosition.blockX(),
                event.blockPosition.blockY() + 1,
                event.blockPosition.blockZ(),
            )
            if (!above.isAir) return@addListener

            val currentNote = event.block.getProperty("note")?.toIntOrNull() ?: 0
            val newNote = (currentNote + 1) % 25
            val newBlock = event.block.withProperty("note", newNote.toString())
            event.instance.setBlock(event.blockPosition, newBlock)

            val belowBlock = event.instance.getBlock(
                event.blockPosition.blockX(),
                event.blockPosition.blockY() - 1,
                event.blockPosition.blockZ(),
            )
            val instrument = resolveInstrument(belowBlock)
            val pitch = 2.0.pow((newNote - 12).toDouble() / 12.0).toFloat()

            event.instance.sendGroupedPacket(SoundEffectPacket(
                instrument,
                Sound.Source.RECORD,
                Vec(event.blockPosition.blockX() + 0.5, event.blockPosition.blockY() + 0.5, event.blockPosition.blockZ() + 0.5),
                3.0f,
                pitch,
                0L,
            ))
        }

        return node
    }

    private fun resolveInstrument(belowBlock: Block): SoundEvent {
        val name = belowBlock.name()
        INSTRUMENT_MAP[name]?.let { return it }

        if (name.endsWith("_planks") || name.endsWith("_log") || name.endsWith("_wood") ||
            name.endsWith("_slab") || name.endsWith("_fence")) {
            return SoundEvent.BLOCK_NOTE_BLOCK_BASS
        }
        if (name.endsWith("_wool") || name.endsWith("_carpet")) {
            return SoundEvent.BLOCK_NOTE_BLOCK_GUITAR
        }
        if (name.endsWith("glass") || name.endsWith("glass_pane")) {
            return SoundEvent.BLOCK_NOTE_BLOCK_HAT
        }
        if (belowBlock.isSolid && !belowBlock.isAir) {
            return SoundEvent.BLOCK_NOTE_BLOCK_BASEDRUM
        }

        return DEFAULT_INSTRUMENT
    }
}
