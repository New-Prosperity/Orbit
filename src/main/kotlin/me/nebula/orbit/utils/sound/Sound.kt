package me.nebula.orbit.utils.sound

import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.sound.SoundEvent

fun Player.playSound(event: SoundEvent, volume: Float = 1f, pitch: Float = 1f) {
    playSound(Sound.sound(event.key(), Sound.Source.MASTER, volume, pitch))
}

fun Player.playSound(event: SoundEvent, point: Point, volume: Float = 1f, pitch: Float = 1f) {
    playSound(Sound.sound(event.key(), Sound.Source.MASTER, volume, pitch), point)
}
