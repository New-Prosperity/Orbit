package me.nebula.orbit.utils.worldedit

import net.minestom.server.instance.block.Block
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

fun interface Pattern {
    fun apply(x: Int, y: Int, z: Int): Int
}

enum class Axis { X, Y, Z }

object Patterns {

    fun single(block: Block): Pattern = Pattern { _, _, _ -> block.stateId() }

    fun random(vararg entries: Pair<Block, Double>): Pattern {
        require(entries.isNotEmpty()) { "Pattern.random requires at least one entry" }
        val totalWeight = entries.sumOf { it.second }
        val normalized = entries.map { it.first.stateId() to (it.second / totalWeight) }
        return Pattern { _, _, _ ->
            var roll = Random.nextDouble()
            for ((stateId, weight) in normalized) {
                roll -= weight
                if (roll <= 0) return@Pattern stateId
            }
            normalized.last().first
        }
    }

    fun gradient(from: Block, to: Block, axis: Axis, length: Int): Pattern {
        val fromId = from.stateId()
        val toId = to.stateId()
        return Pattern { x, y, z ->
            val coord = when (axis) {
                Axis.X -> x
                Axis.Y -> y
                Axis.Z -> z
            }
            val t = (coord.toDouble() / length.coerceAtLeast(1)).coerceIn(0.0, 1.0)
            if (t < 0.5) fromId else toId
        }
    }

    fun noise(palette: List<Block>, scale: Double, seed: Long): Pattern {
        val ids = palette.map { it.stateId() }
        val rng = Random(seed)
        val permutation = IntArray(512) { rng.nextInt(256) }
        return Pattern { x, y, z ->
            val n = simplexNoise(x * scale, y * scale, z * scale, permutation)
            val normalized = ((n + 1.0) / 2.0).coerceIn(0.0, 0.999)
            ids[(normalized * ids.size).toInt()]
        }
    }

    fun parse(input: String): Pattern? {
        if (input.contains(",") && input.contains("%")) {
            val entries = input.split(",").map { part ->
                val pct = part.substringBefore("%").trim().toDoubleOrNull() ?: return null
                val blockName = part.substringAfter("%").trim()
                val block = resolveBlock(blockName) ?: return null
                block to pct
            }
            return random(*entries.toTypedArray())
        }
        val block = resolveBlock(input) ?: return null
        return single(block)
    }

    fun resolveBlock(name: String): Block? {
        val key = if (":" in name) name else "minecraft:$name"
        return Block.fromKey(key)
    }
}

private fun simplexNoise(x: Double, y: Double, z: Double, perm: IntArray): Double {
    val F3 = 1.0 / 3.0
    val G3 = 1.0 / 6.0
    val s = (x + y + z) * F3
    val i = floor(x + s).toInt()
    val j = floor(y + s).toInt()
    val k = floor(z + s).toInt()
    val t = (i + j + k) * G3
    val x0 = x - (i - t)
    val y0 = y - (j - t)
    val z0 = z - (k - t)

    val hash = ((perm[(i and 255) + perm[(j and 255) + perm[k and 255]]]) and 15)
    return when (hash) {
        0 -> x0 + y0
        1 -> -x0 + y0
        2 -> x0 - y0
        3 -> -x0 - y0
        4 -> x0 + z0
        5 -> -x0 + z0
        6 -> x0 - z0
        7 -> -x0 - z0
        8 -> y0 + z0
        9 -> -y0 + z0
        10 -> y0 - z0
        11 -> -y0 - z0
        12 -> x0 + y0
        13 -> -x0 + y0
        14 -> -y0 + z0
        15 -> -y0 - z0
        else -> 0.0
    }
}
