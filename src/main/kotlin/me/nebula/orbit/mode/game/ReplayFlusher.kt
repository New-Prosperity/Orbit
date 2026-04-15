package me.nebula.orbit.mode.game

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.ReplayHighlightMessage
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.replay.HighlightType
import me.nebula.orbit.utils.replay.PendingReplayFlushes
import me.nebula.orbit.utils.replay.ReplayHighlights
import me.nebula.orbit.utils.replay.ReplayMetadata
import me.nebula.orbit.utils.replay.ReplayStorage

class ReplayFlusher(private val gameMode: GameMode) {

    private val logger = logger("ReplayFlusher")

    fun flush() {
        gameMode.activityWatchdog.cleanupPositionRecording()
        val semanticData = gameMode.semanticRecorderInternal.stop()
        val replayRecorder = gameMode.replayRecorderInternal

        if (replayRecorder.isRecording && ReplayStorage.isInitialized()) {
            val replayName = "${Orbit.serverName}-${gameMode.gameStartTime}"
            val gm = Orbit.gameMode ?: "unknown"
            val map = Orbit.mapName ?: ""
            PendingReplayFlushes.mark(replayName, replayName)
            Thread.startVirtualThread {
                runCatching {
                    val replayFile = replayRecorder.buildReplayFile(
                        gameMode.gameInstance,
                        replayName,
                        gm,
                        map,
                    )
                    ReplayStorage.saveBinary(replayName, replayFile)
                    PendingReplayFlushes.complete(replayName)
                    logger.info { "Replay saved: $replayName (${replayFile.rawPackets.size} packets)" }

                    val semanticFrames = semanticData.frames.map { it.tickOffset to it }
                    val metadata = ReplayMetadata(
                        gameMode = gm,
                        mapName = map,
                        recordedAt = System.currentTimeMillis(),
                        playerCount = gameMode.initialPlayerCount,
                        durationTicks = semanticData.durationTicks,
                    )
                    ReplayStorage.save("$replayName-semantic", semanticData, metadata)

                    val highlights = ReplayHighlights.detect(semanticFrames, metadata)
                    for (highlight in highlights) {
                        if (highlight.type == HighlightType.MULTI_KILL ||
                            highlight.type == HighlightType.CLUTCH) {
                            runCatching {
                                NetworkMessenger.publish(ReplayHighlightMessage(
                                    replayName = replayName,
                                    gameMode = gm,
                                    mapName = map,
                                    playerName = semanticData.playerNames[highlight.playerUuid] ?: highlight.playerUuid.toString(),
                                    highlightType = highlight.type.name,
                                    description = highlight.description,
                                ))
                            }
                        }
                    }
                }.onFailure { logger.warn { "Failed to save replay: ${it.message}" } }
            }
        } else if (replayRecorder.isRecording) {
            replayRecorder.stop(gameMode.gameInstance)
        }
    }
}
