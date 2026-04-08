package me.nebula.orbit.utils.replay

import java.util.UUID

data class ReplayHighlight(
    val tick: Int,
    val type: HighlightType,
    val playerUuid: UUID,
    val description: String,
)

enum class HighlightType {
    MULTI_KILL,
    FINAL_KILL,
    CLUTCH,
    FIRST_BLOOD,
    LONG_RANGE_KILL,
}

object ReplayHighlights {

    private const val MULTI_KILL_WINDOW_TICKS = 200
    private const val LONG_RANGE_THRESHOLD = 20.0

    fun detect(frames: List<Pair<Int, ReplayFrame>>, metadata: ReplayMetadata): List<ReplayHighlight> {
        val highlights = mutableListOf<ReplayHighlight>()
        val deathFrames = frames
            .filter { it.second is ReplayFrame.Death }
            .map { it.first to (it.second as ReplayFrame.Death) }
            .sortedBy { it.first }

        if (deathFrames.isEmpty()) return highlights

        detectFirstBlood(deathFrames, highlights)
        detectFinalKill(deathFrames, highlights)
        detectMultiKills(deathFrames, highlights)
        detectLongRangeKills(deathFrames, frames, highlights)
        detectClutch(deathFrames, metadata, highlights)

        return highlights.sortedBy { it.tick }
    }

    private fun detectFirstBlood(
        deathFrames: List<Pair<Int, ReplayFrame.Death>>,
        highlights: MutableList<ReplayHighlight>,
    ) {
        val first = deathFrames.first()
        val killerUuid = first.second.killerUuid ?: return
        highlights += ReplayHighlight(
            tick = first.first,
            type = HighlightType.FIRST_BLOOD,
            playerUuid = killerUuid,
            description = "First kill of the game",
        )
    }

    private fun detectFinalKill(
        deathFrames: List<Pair<Int, ReplayFrame.Death>>,
        highlights: MutableList<ReplayHighlight>,
    ) {
        val last = deathFrames.last()
        val killerUuid = last.second.killerUuid ?: return
        highlights += ReplayHighlight(
            tick = last.first,
            type = HighlightType.FINAL_KILL,
            playerUuid = killerUuid,
            description = "Final kill of the game",
        )
    }

    private fun detectMultiKills(
        deathFrames: List<Pair<Int, ReplayFrame.Death>>,
        highlights: MutableList<ReplayHighlight>,
    ) {
        val killsByKiller = deathFrames
            .mapNotNull { entry -> entry.second.killerUuid?.let { it to entry } }
            .groupBy({ it.first }, { it.second })

        for ((killer, kills) in killsByKiller) {
            val sorted = kills.sortedBy { it.first }
            var windowStart = 0
            for (i in sorted.indices) {
                while (sorted[i].first - sorted[windowStart].first > MULTI_KILL_WINDOW_TICKS) {
                    windowStart++
                }
                val count = i - windowStart + 1
                if (count >= 3) {
                    highlights += ReplayHighlight(
                        tick = sorted[windowStart].first,
                        type = HighlightType.MULTI_KILL,
                        playerUuid = killer,
                        description = "$count kills within ${MULTI_KILL_WINDOW_TICKS / 20} seconds",
                    )
                    windowStart = i + 1
                }
            }
        }
    }

    private fun detectLongRangeKills(
        deathFrames: List<Pair<Int, ReplayFrame.Death>>,
        allFrames: List<Pair<Int, ReplayFrame>>,
        highlights: MutableList<ReplayHighlight>,
    ) {
        val positionsByTick = mutableMapOf<Int, MutableMap<UUID, ReplayFrame.Position>>()
        for ((tick, frame) in allFrames) {
            if (frame is ReplayFrame.Position) {
                positionsByTick.getOrPut(tick) { mutableMapOf() }[frame.uuid] = frame
            }
        }

        for ((tick, death) in deathFrames) {
            val killerUuid = death.killerUuid ?: continue
            val victimUuid = death.uuid

            val killerPos = findNearestPosition(positionsByTick, killerUuid, tick) ?: continue
            val victimPos = findNearestPosition(positionsByTick, victimUuid, tick) ?: continue

            val distance = killerPos.pos.distance(victimPos.pos)
            if (distance >= LONG_RANGE_THRESHOLD) {
                highlights += ReplayHighlight(
                    tick = tick,
                    type = HighlightType.LONG_RANGE_KILL,
                    playerUuid = killerUuid,
                    description = "Kill from ${"%.1f".format(distance)} blocks away",
                )
            }
        }
    }

    private fun detectClutch(
        deathFrames: List<Pair<Int, ReplayFrame.Death>>,
        metadata: ReplayMetadata,
        highlights: MutableList<ReplayHighlight>,
    ) {
        val allPlayers = deathFrames.flatMap { listOfNotNull(it.second.uuid, it.second.killerUuid) }.toSet()
        if (allPlayers.size < 3) return

        val alive = allPlayers.toMutableSet()
        var lastClutchPlayer: UUID? = null

        for ((tick, death) in deathFrames) {
            alive.remove(death.uuid)
            val killerUuid = death.killerUuid ?: continue

            if (alive.size == 1 && alive.contains(killerUuid) && lastClutchPlayer != killerUuid) {
                lastClutchPlayer = killerUuid
            }

            if (lastClutchPlayer == killerUuid && alive.size == 1) {
                val killsAfterClutch = deathFrames.count {
                    it.second.killerUuid == killerUuid && it.first >= tick
                }
                if (killsAfterClutch >= 2) {
                    highlights += ReplayHighlight(
                        tick = tick,
                        type = HighlightType.CLUTCH,
                        playerUuid = killerUuid,
                        description = "Won a 1v${killsAfterClutch + 1} clutch",
                    )
                }
                break
            }
        }
    }

    private fun findNearestPosition(
        positionsByTick: Map<Int, Map<UUID, ReplayFrame.Position>>,
        uuid: UUID,
        targetTick: Int,
    ): ReplayFrame.Position? {
        for (offset in 0..10) {
            positionsByTick[targetTick - offset]?.get(uuid)?.let { return it }
            if (offset > 0) {
                positionsByTick[targetTick + offset]?.get(uuid)?.let { return it }
            }
        }
        return null
    }
}
