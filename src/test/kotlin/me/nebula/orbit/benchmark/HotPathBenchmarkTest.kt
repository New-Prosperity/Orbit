package me.nebula.orbit.benchmark

import me.nebula.orbit.utils.mapvote.MapVoteManager
import me.nebula.orbit.utils.mapvote.VoteCategory
import me.nebula.orbit.utils.mapvote.VoteOption
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertTrue

class HotPathBenchmarkTest {

    private fun benchmark(name: String, iterations: Int, maxNanosPerOp: Long, block: () -> Unit) {
        repeat(iterations / 10) { block() }
        val start = System.nanoTime()
        repeat(iterations) { block() }
        val elapsed = System.nanoTime() - start
        val nanosPerOp = elapsed / iterations
        println("[bench] $name: ${iterations} ops, ${elapsed / 1_000_000}ms total, ${nanosPerOp}ns/op")
        assertTrue(nanosPerOp < maxNanosPerOp,
            "$name regression: ${nanosPerOp}ns/op exceeds budget ${maxNanosPerOp}ns/op")
    }

    @Test
    fun `MapVoteManager resolve scales linearly with vote count`() {
        val categories = listOf(
            VoteCategory(
                id = "test",
                nameKey = "test",
                material = "PAPER",
                defaultIndex = 0,
                options = (0..4).map { VoteOption("opt-$it", "PAPER", it) },
            )
        )
        val manager = MapVoteManager(categoriesProvider = { categories })
        repeat(500) { manager.vote(UUID.randomUUID(), "test", it % 5) }

        benchmark(
            name = "MapVoteManager.resolve(500 votes)",
            iterations = 10_000,
            maxNanosPerOp = 200_000,
        ) {
            manager.resolve("test")
        }
    }

    @Test
    fun `MapVoteManager recordSelection is allocation-light`() {
        val categories = listOf(
            VoteCategory("test", "test", "PAPER", 0, listOf(VoteOption("a", "PAPER", 0))),
        )
        val manager = MapVoteManager(categoriesProvider = { categories })

        benchmark(
            name = "MapVoteManager.recordSelection",
            iterations = 100_000,
            maxNanosPerOp = 50_000,
        ) {
            manager.recordSelection("test", 0)
        }
    }

    @Test
    fun `Particle circle math (40 points)`() {
        val center = doubleArrayOf(100.0, 64.0, 100.0)
        val out = DoubleArray(120)
        val points = 40
        val radius = 5.0

        benchmark(
            name = "Particle.spawnParticleCircle math (40 points)",
            iterations = 100_000,
            maxNanosPerOp = 5_000,
        ) {
            for (i in 0 until points) {
                val angle = 2.0 * PI * i / points
                out[i * 3] = center[0] + radius * cos(angle)
                out[i * 3 + 1] = center[1]
                out[i * 3 + 2] = center[2] + radius * sin(angle)
            }
        }
    }

    @Test
    fun `Particle line math (20 steps)`() {
        val from = doubleArrayOf(0.0, 64.0, 0.0)
        val to = doubleArrayOf(20.0, 64.0, 20.0)
        val out = DoubleArray(63)

        benchmark(
            name = "Particle.spawnParticleLine math (20 steps)",
            iterations = 100_000,
            maxNanosPerOp = 5_000,
        ) {
            val dx = to[0] - from[0]
            val dy = to[1] - from[1]
            val dz = to[2] - from[2]
            val steps = 20
            for (i in 0..steps) {
                val t = i.toDouble() / steps
                out[i * 3] = from[0] + dx * t
                out[i * 3 + 1] = from[1] + dy * t
                out[i * 3 + 2] = from[2] + dz * t
            }
        }
    }
}
