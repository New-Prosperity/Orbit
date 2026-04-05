package me.nebula.orbit.utils.worldedit

import net.minestom.server.instance.block.Block

fun interface Mask {
    fun test(stateId: Int): Boolean
}

object Masks {

    fun block(block: Block): Mask = Mask { it == block.stateId() }

    fun blocks(vararg blocks: Block): Mask {
        val ids = blocks.map { it.stateId() }.toIntArray()
        return Mask { stateId -> ids.any { it == stateId } }
    }

    fun not(mask: Mask): Mask = Mask { !mask.test(it) }

    fun existing(): Mask = Mask {
        it != Block.AIR.stateId() &&
            it != Block.VOID_AIR.stateId() &&
            it != Block.CAVE_AIR.stateId()
    }

    fun air(): Mask = not(existing())

    fun solid(): Mask = Mask {
        val block = Block.fromStateId(it.toInt())
        block != null && block.isSolid
    }

    fun liquid(): Mask = Mask {
        val block = Block.fromStateId(it.toInt())
        block != null && block.isLiquid
    }

    fun any(vararg masks: Mask): Mask = Mask { stateId -> masks.any { it.test(stateId) } }

    fun all(vararg masks: Mask): Mask = Mask { stateId -> masks.all { it.test(stateId) } }

    fun parse(input: String): Mask = when {
        input.startsWith("!") -> not(parse(input.removePrefix("!")))
        input == "#existing" -> existing()
        input == "#air" -> air()
        input == "#solid" -> solid()
        input == "#liquid" -> liquid()
        input.contains(",") -> any(*input.split(",").map { parse(it.trim()) }.toTypedArray())
        else -> {
            val block = Block.fromKey("minecraft:$input") ?: Block.fromKey(input)
            if (block != null) block(block) else block(Block.AIR)
        }
    }
}
