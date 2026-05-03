package me.nebula.orbit.utils.mapgen.planet

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.mapgen.GeneratedMap
import me.nebula.orbit.utils.mapgen.planet.rhexor.RhexorGenerator
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadLocalRandom

object PlanetMapGenerator {

    private val logger = logger("PlanetMapGenerator")

    fun build(planetId: String, seed: Long? = null): PlanetGenerator {
        val effectiveSeed = seed ?: ThreadLocalRandom.current().nextLong()
        return when (planetId.lowercase()) {
            "rhexor" -> RhexorGenerator(seed = effectiveSeed)
            else -> error("Unknown planet '$planetId'")
        }
    }

    fun generate(planet: PlanetGenerator): GeneratedMap {
        val island = planet.spec.island
        val mapRadius = if (island.enabled) island.radius.toInt() else DEFAULT_RADIUS
        logger.info { "Generating planet '${planet.spec.id}' (seed=${planet.spec.seed}, radius=$mapRadius, island=${island.enabled})" }

        val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        instance.setGenerator(planet)

        val radiusChunks = (mapRadius / 16) + 1
        val totalChunks = (radiusChunks * 2 + 1).let { it * it }
        logger.info { "Preloading $totalChunks chunks..." }

        val futures = mutableListOf<CompletableFuture<*>>()
        for (cx in -radiusChunks..radiusChunks) {
            for (cz in -radiusChunks..radiusChunks) {
                futures.add(instance.loadChunk(cx, cz))
            }
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()

        val centerY = planet.groundLevelAt(island.centerX, island.centerZ).toInt()
        val center = Pos(island.centerX + 0.5, centerY + 2.0, island.centerZ + 0.5)

        logger.info { "Planet generation complete (center=${center.blockX()},${center.blockY()},${center.blockZ()})" }
        return GeneratedMap(instance, center, mapRadius)
    }

    private const val DEFAULT_RADIUS = 256
}
