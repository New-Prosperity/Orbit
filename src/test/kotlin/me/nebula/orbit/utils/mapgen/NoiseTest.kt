package me.nebula.orbit.utils.mapgen

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NoiseTest {

    @Test
    fun `same seed produces same noise2D values`() {
        val a = PerlinNoise(seed = 12345L)
        val b = PerlinNoise(seed = 12345L)
        for (i in 1..10) {
            val x = i * 0.123
            val z = i * 0.456
            assertEquals(a.noise2D(x, z), b.noise2D(x, z))
        }
    }

    @Test
    fun `different seeds produce different noise values`() {
        val a = PerlinNoise(seed = 1L)
        val b = PerlinNoise(seed = 99L)
        var differences = 0
        for (i in 1..50) {
            val x = i * 0.37
            val z = i * 0.91
            if (a.noise2D(x, z) != b.noise2D(x, z)) differences++
        }
        assertTrue(differences > 25, "expected most samples to differ")
    }

    @Test
    fun `noise2D output stays within reasonable bounds`() {
        val noise = PerlinNoise(seed = 42L)
        for (i in 0..1000) {
            val x = i * 0.05
            val z = i * 0.07
            val v = noise.noise2D(x, z)
            assertTrue(abs(v) <= 1.5, "noise out of bounds: $v")
        }
    }

    @Test
    fun `noise3D output stays within reasonable bounds`() {
        val noise = PerlinNoise(seed = 42L)
        for (i in 0..200) {
            val v = noise.noise3D(i * 0.05, i * 0.07, i * 0.11)
            assertTrue(abs(v) <= 1.5, "noise out of bounds: $v")
        }
    }

    @Test
    fun `noise2D varies smoothly between adjacent samples`() {
        val noise = PerlinNoise(seed = 7L)
        val a = noise.noise2D(10.0, 10.0)
        val b = noise.noise2D(10.001, 10.0)
        assertTrue(abs(a - b) < 0.05, "tiny step caused jump from $a to $b")
    }

    @Test
    fun `octave noise is deterministic for same seed`() {
        val a = OctaveNoise(PerlinNoise(seed = 1L), octaves = 4)
        val b = OctaveNoise(PerlinNoise(seed = 1L), octaves = 4)
        assertEquals(a.sample2D(5.5, 7.5), b.sample2D(5.5, 7.5))
    }

    @Test
    fun `octave noise output normalized to roughly -1 to 1`() {
        val noise = OctaveNoise(PerlinNoise(seed = 99L), octaves = 4)
        for (i in 0..500) {
            val v = noise.sample2D(i * 0.13, i * 0.17)
            assertTrue(v in -1.5..1.5, "octave noise out of bounds: $v")
        }
    }

    @Test
    fun `octave noise gives variation across many sample points`() {
        val noise = OctaveNoise(PerlinNoise(seed = 1L), octaves = 4)
        val samples = (0..100).map { noise.sample2D(it * 0.7, it * 0.9) }
        val unique = samples.toSet()
        assertTrue(unique.size > 50, "expected variation, got ${unique.size}/101 unique")
    }

    @Test
    fun `ridged noise is deterministic`() {
        val a = RidgedNoise(PerlinNoise(seed = 5L), octaves = 3)
        val b = RidgedNoise(PerlinNoise(seed = 5L), octaves = 3)
        assertEquals(a.sample2D(11.0, 13.0), b.sample2D(11.0, 13.0))
    }

    @Test
    fun `scaled noise multiplies coordinates`() {
        val source = OctaveNoise(PerlinNoise(seed = 3L), octaves = 2).asSource()
        val scaled = ScaledNoise(source, scaleX = 2.0)
        assertEquals(source.sample(2.0, 4.0), scaled.sample(1.0, 4.0))
    }
}
