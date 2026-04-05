package me.nebula.orbit.utils.metrics

import com.hazelcast.replicatedmap.ReplicatedMap
import me.nebula.ether.utils.hazelcast.HazelcastStructureProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.metrics.ServerMetrics
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.tpsmonitor.TPSMonitor
import net.minestom.server.MinecraftServer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object MetricsPublisher {

    private val logger = logger("MetricsPublisher")
    private val metricsMap: ReplicatedMap<String, Any> by lazy {
        HazelcastStructureProvider.replicatedMap("metrics")
    }
    private val startTime = System.currentTimeMillis()
    @Volatile private var executor: ScheduledExecutorService? = null

    fun initialize() {
        executor = Executors.newSingleThreadScheduledExecutor {
            Thread(it, "metrics-publisher").apply { isDaemon = true }
        }
        executor?.scheduleAtFixedRate(::publish, 10, 10, TimeUnit.SECONDS)
        logger.info { "MetricsPublisher initialized" }
    }

    fun shutdown() {
        executor?.shutdown()
        executor?.awaitTermination(5, TimeUnit.SECONDS)
        runCatching { metricsMap.remove(Orbit.serverName) }
        logger.info { "MetricsPublisher shut down" }
    }

    private fun publish() {
        try {
            val runtime = Runtime.getRuntime()
            val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
            val entityCount = MinecraftServer.getInstanceManager().instances.sumOf { it.entities.size }

            val metrics = ServerMetrics(
                name = Orbit.serverName,
                type = Orbit.gameMode ?: "hub",
                tps = TPSMonitor.averageTPS,
                playerCount = onlinePlayers.size,
                entityCount = entityCount,
                memoryUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576,
                memoryMaxMb = runtime.maxMemory() / 1_048_576,
                uptime = System.currentTimeMillis() - startTime,
                timestamp = System.currentTimeMillis(),
            )

            metricsMap[Orbit.serverName] = metrics
        } catch (e: Exception) {
            logger.warn { "Failed to publish metrics: ${e.message}" }
        }
    }
}
