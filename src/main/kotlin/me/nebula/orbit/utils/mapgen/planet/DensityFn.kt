package me.nebula.orbit.utils.mapgen.planet

import me.nebula.orbit.utils.mapgen.OctaveNoise
import me.nebula.orbit.utils.mapgen.PerlinNoise

fun interface DensityFn {
    fun sample(x: Int, y: Int, z: Int): Double
}

object Density {

    fun const(value: Double): DensityFn = DensityFn { _, _, _ -> value }

    fun heightDelta(groundLevelFn: (Int, Int) -> Double): DensityFn =
        DensityFn { x, y, z -> groundLevelFn(x, z) - y }

    fun noise2D(noise: OctaveNoise, scaleXZ: Double, amplitude: Double): DensityFn =
        DensityFn { x, _, z -> noise.sample2D(x * scaleXZ, z * scaleXZ) * amplitude }

    fun noise3D(noise: OctaveNoise, scaleXZ: Double, scaleY: Double, amplitude: Double): DensityFn =
        DensityFn { x, y, z -> noise.sample3D(x * scaleXZ, y * scaleY, z * scaleXZ) * amplitude }

    fun add(vararg fns: DensityFn): DensityFn = DensityFn { x, y, z ->
        var sum = 0.0
        for (fn in fns) sum += fn.sample(x, y, z)
        sum
    }

    fun mul(vararg fns: DensityFn): DensityFn = DensityFn { x, y, z ->
        var prod = 1.0
        for (fn in fns) prod *= fn.sample(x, y, z)
        prod
    }

    fun clamp(fn: DensityFn, min: Double, max: Double): DensityFn =
        DensityFn { x, y, z -> fn.sample(x, y, z).coerceIn(min, max) }

    fun heightFalloff(centerY: Double, falloff: Double): DensityFn =
        DensityFn { _, y, _ ->
            val d = (centerY - y) / falloff
            d.coerceIn(-1.0, 1.0)
        }

    fun perlinOctaves(seed: Long, octaves: Int = 4, lacunarity: Double = 2.0, persistence: Double = 0.5): OctaveNoise =
        OctaveNoise(PerlinNoise(seed), octaves = octaves, lacunarity = lacunarity, persistence = persistence)
}
