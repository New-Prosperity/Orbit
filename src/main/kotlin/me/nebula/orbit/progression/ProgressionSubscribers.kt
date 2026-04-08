package me.nebula.orbit.progression

import me.nebula.orbit.progression.mission.MissionTracker

object ProgressionSubscribers {

    @Volatile private var installed = false
    private val missionDispatcher: (ProgressionEvent) -> Unit = ::dispatchToMissions

    fun install() {
        if (installed) return
        installed = true
        ProgressionEventBus.subscribe(missionDispatcher)
    }

    fun uninstall() {
        if (!installed) return
        installed = false
        ProgressionEventBus.unsubscribe(missionDispatcher)
    }

    private fun dispatchToMissions(event: ProgressionEvent) {
        when (event) {
            is ProgressionEvent.BlockMined -> MissionTracker.onMineBlock(event.player)
            is ProgressionEvent.BlockPlaced -> MissionTracker.onPlaceBlock(event.player)
            is ProgressionEvent.GameStarted -> {
                MissionTracker.onGamePlayed(event.player)
                MissionTracker.onPlayGamemode(event.player, event.gameMode)
            }
            is ProgressionEvent.GameEnded -> {
                if (event.won) {
                    MissionTracker.onWin(event.player)
                }
            }
            is ProgressionEvent.SurvivalTick -> MissionTracker.onSurvivalMinute(event.player)
            is ProgressionEvent.DistanceWalked -> MissionTracker.onWalkDistance(event.player, event.blocks)
            is ProgressionEvent.DamageDealt -> MissionTracker.onDamageDealt(event.player, event.amount)
            is ProgressionEvent.Kill -> MissionTracker.onKill(event.player)
            is ProgressionEvent.Assist -> MissionTracker.onAssist(event.player)
            is ProgressionEvent.KillStreak -> MissionTracker.onKillStreak(event.player)
            is ProgressionEvent.TopPlacement -> MissionTracker.onTopPlacement(event.player)
        }
    }
}
