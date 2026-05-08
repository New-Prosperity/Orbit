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

        val issues = planet.validateAgainstRegistries()
        if (issues.isNotEmpty()) {
            issues.forEach { logger.warn { "Planet validation: $it" } }
            error("Planet '${planet.spec.id}' has unresolved registry references — refusing to generate. Fix the missing biomes/structures/ore-clusters first.")
        }

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

        val safe = planet.findSafeSpawn(island.centerX, island.centerZ)
        val center = Pos(safe.x + 0.5, safe.y.toDouble(), safe.z + 0.5)
        if (safe.fallback) {
            logger.warn { "findSafeSpawn fell back after ${safe.columnsTested} columns; spawning at planet origin (may be in water/cave)" }
        } else if (safe.columnsTested > 1) {
            logger.info { "Spawn relocated to (${safe.x}, ${safe.y}, ${safe.z}) after testing ${safe.columnsTested} columns" }
        }

        logger.info { "Planet generation complete (center=${center.blockX()},${center.blockY()},${center.blockZ()})" }
        return GeneratedMap(instance, center, mapRadius)
    }

    private const val DEFAULT_RADIUS = 256
}
