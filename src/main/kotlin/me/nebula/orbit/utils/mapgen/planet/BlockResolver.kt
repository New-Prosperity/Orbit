package me.nebula.orbit.utils.mapgen.planet

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import net.minestom.server.instance.block.Block
import java.util.concurrent.ConcurrentHashMap

object BlockResolver {

    private val logger = logger("BlockResolver")
    private val warned = ConcurrentHashMap.newKeySet<String>()

    fun resolve(name: String): Block = resolveOrNull(name) ?: warnAndFallback(name)

    fun resolveOrNull(name: String): Block? {
        CustomBlockRegistry[name]?.let { return it.allocatedState }
        val key = if (':' in name) name else "minecraft:${name.lowercase()}"
        return Block.fromKey(key)
    }

    private fun warnAndFallback(name: String): Block {
        if (warned.add(name)) {
            logger.warn { "Unknown block '$name' — falling back to AIR (warning suppressed for subsequent occurrences)" }
        }
        return Block.AIR
    }
}
