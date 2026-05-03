package me.nebula.orbit.utils.mapgen.planet

import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import net.minestom.server.instance.block.Block

object BlockResolver {

    fun resolve(name: String): Block {
        CustomBlockRegistry[name]?.let { return it.allocatedState }
        val key = if (':' in name) name else "minecraft:${name.lowercase()}"
        return Block.fromKey(key)
            ?: error("Unknown block '$name' (no vanilla key, no custom block registered)")
    }

    fun resolveOrNull(name: String): Block? {
        CustomBlockRegistry[name]?.let { return it.allocatedState }
        val key = if (':' in name) name else "minecraft:${name.lowercase()}"
        return Block.fromKey(key)
    }
}
