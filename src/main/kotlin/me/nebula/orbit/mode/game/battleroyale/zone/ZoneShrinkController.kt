package me.nebula.orbit.mode.game.battleroyale.zone

interface ZoneShrinkController {
    fun planZoneShrink(targetDiameter: Double, durationSeconds: Double, announceLeadSeconds: Double)
}
