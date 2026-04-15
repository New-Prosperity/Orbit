package me.nebula.orbit.mode.game.battleroyale.script

interface BorderController {
    fun shrinkBorderTo(diameter: Double, durationSeconds: Double)
    fun setBorderDamage(damagePerSecond: Double)
}

interface DeathmatchController {
    fun startDeathmatch()
}
