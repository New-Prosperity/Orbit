package me.nebula.orbit.utils.metrics

import com.hazelcast.replicatedmap.ReplicatedMap
import me.nebula.ether.utils.hazelcast.HazelcastStructureProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.observability.ErrorAggregator
import me.nebula.ether.utils.observability.errorAggregator
import me.nebula.ether.utils.scheduling.ScheduledTask
import me.nebula.ether.utils.scheduling.TaskScheduler
import me.nebula.gravity.metrics.ServerMetrics
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.tpsmonitor.TPSMonitor
import net.minestom.server.MinecraftServer
import kotlin.time.Duration.Companion.seconds

object MetricsPublisher {

    private val logger = logger("MetricsPublisher")
    private val metricsMap: ReplicatedMap<String, Any> by lazy {
        HazelcastStructureProvider.replicatedMap("metrics")
    }
    private val startTime = System.currentTimeMillis()
    @Volatile private var publisherTask: ScheduledTask? = null

    private fun errorsKey(): String = "errors:${Orbit.serverName}"

    fun initialize() {
        errorAggregator()
        publisherTask = TaskScheduler.scheduleAtFixedRate("metrics-publisher", 10.seconds, ::publish)
        logger.info { "MetricsPublisher initialized" }
    }

    fun shutdown() {
        publisherTask?.cancel()
        publisherTask = null
        runCatching { metricsMap.remove(Orbit.serverName) }
        runCatching { metricsMap.remove(errorsKey()) }
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
            metricsMap[errorsKey()] = ErrorAggregator.snapshot(Orbit.serverName)
        } catch (e: Exception) {
            logger.warn { "Failed to publish metrics: ${e.message}" }
        }
    }
}
