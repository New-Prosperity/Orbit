package me.nebula.orbit.utils.customcontent.event

import net.kyori.adventure.sound.Sound
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.StopSoundPacket

object CarrierSoundSuppressor {

    private const val FLAGS_SOURCE_AND_SOUND: Byte = 0x03

    private val PLACE_SOUNDS = listOf(
        "block.wood.place",
        "block.bamboo_wood.place",
        "block.stone.place",
        "block.tripwire.attach",
    )

    private val BREAK_SOUNDS = listOf(
        "block.wood.break",
        "block.bamboo_wood.break",
        "block.stone.break",
        "block.tripwire.detach",
    )

    fun suppressPlace(instance: Instance) = suppress(instance, PLACE_SOUNDS)

    fun suppressBreak(instance: Instance) = suppress(instance, BREAK_SOUNDS)

    private fun suppress(instance: Instance, sounds: List<String>) {
        for (name in sounds) {
            instance.sendGroupedPacket(StopSoundPacket(FLAGS_SOURCE_AND_SOUND, Sound.Source.BLOCK, name))
        }
    }
}
