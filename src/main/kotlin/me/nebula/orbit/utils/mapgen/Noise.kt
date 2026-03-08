package me.nebula.orbit.utils.mapgen

import kotlin.math.abs
import kotlin.math.floor

class PerlinNoise(seed: Long = 0L) {

    private val perm = IntArray(512)

    init {
        val source = IntArray(256) { it }
        val random = java.util.Random(seed)
        for (i in 255 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = source[i]
            source[i] = source[j]
            source[j] = tmp
        }
        for (i in 0 until 512) perm[i] = source[i and 255]
    }

    fun noise2D(x: Double, z: Double): Double {
        val xi = floor(x).toInt() and 255
        val zi = floor(z).toInt() and 255
        val xf = x - floor(x)
        val zf = z - floor(z)
        val u = fade(xf)
        val v = fade(zf)
        val aa = perm[perm[xi] + zi]
        val ab = perm[perm[xi] + zi + 1]
        val ba = perm[perm[xi + 1] + zi]
        val bb = perm[perm[xi + 1] + zi + 1]
        return lerp(v,
            lerp(u, grad2D(aa, xf, zf), grad2D(ba, xf - 1.0, zf)),
            lerp(u, grad2D(ab, xf, zf - 1.0), grad2D(bb, xf - 1.0, zf - 1.0)),
        )
    }

    fun noise3D(x: Double, y: Double, z: Double): Double {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255
        val zi = floor(z).toInt() and 255
        val xf = x - floor(x)
        val yf = y - floor(y)
        val zf = z - floor(z)
        val u = fade(xf)
        val v = fade(yf)
        val w = fade(zf)
        val a = perm[xi] + yi
        val aa = perm[a] + zi
        val ab = perm[a + 1] + zi
        val b = perm[xi + 1] + yi
        val ba = perm[b] + zi
        val bb = perm[b + 1] + zi
        return lerp(w,
            lerp(v,
                lerp(u, grad3D(perm[aa], xf, yf, zf), grad3D(perm[ba], xf - 1.0, yf, zf)),
                lerp(u, grad3D(perm[ab], xf, yf - 1.0, zf), grad3D(perm[bb], xf - 1.0, yf - 1.0, zf)),
            ),
            lerp(v,
                lerp(u, grad3D(perm[aa + 1], xf, yf, zf - 1.0), grad3D(perm[ba + 1], xf - 1.0, yf, zf - 1.0)),
                lerp(u, grad3D(perm[ab + 1], xf, yf - 1.0, zf - 1.0), grad3D(perm[bb + 1], xf - 1.0, yf - 1.0, zf - 1.0)),
            ),
        )
    }

    private fun fade(t: Double): Double = t * t * t * (t * (t * 6 - 15) + 10)

    private fun lerp(t: Double, a: Double, b: Double): Double = a + t * (b - a)

    private fun grad2D(hash: Int, x: Double, z: Double): Double = when (hash and 3) {
        0 -> x + z
        1 -> -x + z
        2 -> x - z
        else -> -x - z
    }

    private fun grad3D(hash: Int, x: Double, y: Double, z: Double): Double = when (hash and 15) {
        0 -> x + y
        1 -> -x + y
        2 -> x - y
        3 -> -x - y
        4 -> x + z
        5 -> -x + z
        6 -> x - z
        7 -> -x - z
        8 -> y + z
        9 -> -y + z
        10 -> y - z
        11 -> -y - z
        12 -> x + y
        13 -> -x + y
        14 -> y - z
        else -> -y - z
    }
}

class OctaveNoise(
    private val base: PerlinNoise,
    private val octaves: Int = 6,
    private val lacunarity: Double = 2.0,
    private val persistence: Double = 0.5,
) {

    private val normalization: Double = run {
        var sum = 0.0
        var amp = 1.0
        repeat(octaves) { sum += amp; amp *= persistence }
        sum
    }

    fun sample2D(x: Double, z: Double): Double {
        var total = 0.0
        var frequency = 1.0
        var amplitude = 1.0
        repeat(octaves) {
            total += base.noise2D(x * frequency, z * frequency) * amplitude
            frequency *= lacunarity
            amplitude *= persistence
        }
        return total / normalization
    }

    fun sample3D(x: Double, y: Double, z: Double): Double {
        var total = 0.0
        var frequency = 1.0
        var amplitude = 1.0
        repeat(octaves) {
            total += base.noise3D(x * frequency, y * frequency, z * frequency) * amplitude
            frequency *= lacunarity
            amplitude *= persistence
        }
        return total / normalization
    }
}

class RidgedNoise(
    private val base: PerlinNoise,
    private val octaves: Int = 6,
    private val lacunarity: Double = 2.0,
    private val gain: Double = 0.5,
    private val offset: Double = 1.0,
) {

    fun sample2D(x: Double, z: Double): Double {
        var total = 0.0
        var frequency = 1.0
        var amplitude = 1.0
        var weight = 1.0
        repeat(octaves) {
            var signal = base.noise2D(x * frequency, z * frequency)
            signal = offset - abs(signal)
            signal *= signal
            signal *= weight
            weight = (signal * gain).coerceIn(0.0, 1.0)
            total += signal * amplitude
            frequency *= lacunarity
            amplitude *= 0.5
        }
        return total * 1.25 - 1.0
    }

    fun sample3D(x: Double, y: Double, z: Double): Double {
        var total = 0.0
        var frequency = 1.0
        var amplitude = 1.0
        var weight = 1.0
        repeat(octaves) {
            var signal = base.noise3D(x * frequency, y * frequency, z * frequency)
            signal = offset - abs(signal)
            signal *= signal
            signal *= weight
            weight = (signal * gain).coerceIn(0.0, 1.0)
            total += signal * amplitude
            frequency *= lacunarity
            amplitude *= 0.5
        }
        return total * 1.25 - 1.0
    }
}

class WarpedNoise(
    private val primary: OctaveNoise,
    private val warpX: OctaveNoise,
    private val warpZ: OctaveNoise,
    private val warpStrength: Double = 30.0,
) {

    fun sample2D(x: Double, z: Double): Double {
        val wx = warpX.sample2D(x * 0.5, z * 0.5) * warpStrength
        val wz = warpZ.sample2D(x * 0.5, z * 0.5) * warpStrength
        return primary.sample2D(x + wx, z + wz)
    }
}

interface NoiseSource2D {
    fun sample(x: Double, z: Double): Double
}

fun OctaveNoise.asSource() = object : NoiseSource2D {
    override fun sample(x: Double, z: Double) = sample2D(x, z)
}

fun RidgedNoise.asSource() = object : NoiseSource2D {
    override fun sample(x: Double, z: Double) = sample2D(x, z)
}

fun WarpedNoise.asSource() = object : NoiseSource2D {
    override fun sample(x: Double, z: Double) = sample2D(x, z)
}

class ScaledNoise(
    private val source: NoiseSource2D,
    private val scaleX: Double = 1.0,
    private val scaleZ: Double = scaleX,
) : NoiseSource2D {
    override fun sample(x: Double, z: Double) = source.sample(x * scaleX, z * scaleZ)
}
