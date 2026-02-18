package me.nebula.orbit.utils.teambalance

import net.minestom.server.entity.Player
import kotlin.math.abs

object TeamBalance {

    fun balance(players: Collection<Player>, teamCount: Int): Map<Int, List<Player>> {
        require(teamCount > 0) { "Team count must be positive" }
        return balance(players, teamCount) { 1.0 }
    }

    fun balance(
        players: Collection<Player>,
        teamCount: Int,
        scorer: (Player) -> Double,
    ): Map<Int, List<Player>> {
        require(teamCount > 0) { "Team count must be positive" }
        val sorted = players.sortedByDescending(scorer)
        val teams = (0 until teamCount).associateWith { mutableListOf<Player>() }
        val teamScores = DoubleArray(teamCount)

        var forward = true
        var teamIndex = 0
        for (player in sorted) {
            teams.getValue(teamIndex).add(player)
            teamScores[teamIndex] += scorer(player)
            if (forward) {
                if (teamIndex == teamCount - 1) { forward = false } else { teamIndex++ }
            } else {
                if (teamIndex == 0) { forward = true } else { teamIndex-- }
            }
        }

        return teams.mapValues { it.value.toList() }
    }

    fun suggestSwap(
        teams: Map<Int, List<Player>>,
        scorer: (Player) -> Double,
    ): Pair<Player, Player>? {
        if (teams.size < 2) return null
        val teamScores = teams.mapValues { (_, players) -> players.sumOf(scorer) }
        val currentVariance = variance(teamScores.values)
        var bestSwap: Pair<Player, Player>? = null
        var bestVariance = currentVariance

        val teamEntries = teams.entries.toList()
        for (i in teamEntries.indices) {
            for (j in i + 1 until teamEntries.size) {
                val (teamA, playersA) = teamEntries[i]
                val (teamB, playersB) = teamEntries[j]
                val scoreA = teamScores.getValue(teamA)
                val scoreB = teamScores.getValue(teamB)
                for (pa in playersA) {
                    for (pb in playersB) {
                        val sa = scorer(pa)
                        val sb = scorer(pb)
                        val newScoreA = scoreA - sa + sb
                        val newScoreB = scoreB - sb + sa
                        val newScores = teamScores.toMutableMap()
                        newScores[teamA] = newScoreA
                        newScores[teamB] = newScoreB
                        val v = variance(newScores.values)
                        if (v < bestVariance) {
                            bestVariance = v
                            bestSwap = pa to pb
                        }
                    }
                }
            }
        }

        return bestSwap
    }

    fun autoBalance(teams: MutableMap<Int, MutableList<Player>>, newPlayer: Player): Int {
        require(teams.isNotEmpty()) { "Teams must not be empty" }
        val targetTeam = teams.minByOrNull { it.value.size }!!.key
        teams.getValue(targetTeam).add(newPlayer)
        return targetTeam
    }

    private fun variance(values: Collection<Double>): Double {
        if (values.size <= 1) return 0.0
        val mean = values.sum() / values.size
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }
}
