package me.nebula.orbit.mode.game

import me.nebula.orbit.utils.scheduler.delay
import net.minestom.server.timer.Task

class OvertimeController {

    @Volatile private var task: Task? = null
    @Volatile private var inOvertime = false
    @Volatile private var inSuddenDeath = false

    val isOvertime: Boolean get() = inOvertime
    val isSuddenDeath: Boolean get() = inSuddenDeath

    fun start(durationSeconds: Int, suddenDeath: Boolean, onExpire: () -> Unit) {
        inOvertime = true
        if (suddenDeath) inSuddenDeath = true
        task = delay(durationSeconds * 20) {
            inOvertime = false
            inSuddenDeath = false
            onExpire()
        }
    }

    fun cleanup() {
        task?.cancel()
        task = null
        inOvertime = false
        inSuddenDeath = false
    }
}
