package me.nebula.orbit.utils.sound

import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.sound.SoundEvent

class SoundEffect @PublishedApi internal constructor(
    private val sound: Sound,
) {

    fun play(player: Player) = player.playSound(sound)

    fun play(player: Player, position: Pos) =
        player.playSound(sound, position.x(), position.y(), position.z())

    fun play(instance: Instance, position: Pos) =
        instance.playSound(sound, position.x(), position.y(), position.z())
}

class SoundBuilder @PublishedApi internal constructor(private val event: SoundEvent) {

    var volume: Float = 1f
    var pitch: Float = 1f
    var source: Sound.Source = Sound.Source.MASTER

    @PublishedApi internal fun build(): SoundEffect =
        SoundEffect(Sound.sound(event.key(), source, volume, pitch))
}

inline fun soundEffect(event: SoundEvent, block: SoundBuilder.() -> Unit = {}): SoundEffect =
    SoundBuilder(event).apply(block).build()

fun Player.playSound(event: SoundEvent, volume: Float = 1f, pitch: Float = 1f) =
    playSound(Sound.sound(event.key(), Sound.Source.MASTER, volume, pitch))

fun Player.playSound(event: SoundEvent, position: Pos, volume: Float = 1f, pitch: Float = 1f) =
    playSound(Sound.sound(event.key(), Sound.Source.MASTER, volume, pitch), position.x(), position.y(), position.z())
