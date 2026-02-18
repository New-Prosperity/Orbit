package me.nebula.orbit.utils.entitycleanup

import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val miniMessage = MiniMessage.miniMessage()

data class EntityCleanupConfig @PublishedApi internal constructor(
    var maxAge: Duration = Duration.ofMinutes(5),
    var maxPerInstance: Int = 200,
    val excludedTypes: MutableSet<EntityType> = mutableSetOf(EntityType.PLAYER),
    var warningAtSeconds: Int = 180,
    var warningTemplate: String = "<yellow>Cleaning entities in {time}s",
    var cleanupInterval: Duration = Duration.ofSeconds(30),
)

class EntityCleanupConfigBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val config = EntityCleanupConfig()

    fun maxAge(duration: Duration) { config.maxAge = duration }
    fun maxPerInstance(count: Int) { config.maxPerInstance = count }
    fun excludeTypes(vararg types: EntityType) { config.excludedTypes.addAll(types) }
    fun warningAt(seconds: Int) { config.warningAtSeconds = seconds }
    fun warningMessage(template: String) { config.warningTemplate = template }
    fun cleanupInterval(duration: Duration) { config.cleanupInterval = duration }

    @PublishedApi internal fun build(): EntityCleanupConfig = config.copy(
        excludedTypes = config.excludedTypes.toMutableSet()
    )
}

object EntityCleanupManager {

    private val installedCleaners = ConcurrentHashMap<Instance, Task>()
    private val instanceConfigs = ConcurrentHashMap<Instance, EntityCleanupConfig>()

    fun install(instance: Instance, config: EntityCleanupConfig) {
        uninstall(instance)
        instanceConfigs[instance] = config

        val task = MinecraftServer.getSchedulerManager()
            .buildTask { cleanupInstance(instance, config) }
            .repeat(TaskSchedule.duration(config.cleanupInterval))
            .schedule()
        installedCleaners[instance] = task
    }

    fun uninstall(instance: Instance) {
        installedCleaners.remove(instance)?.cancel()
        instanceConfigs.remove(instance)
    }

    fun forceCleanup(instance: Instance) {
        val config = instanceConfigs[instance] ?: return
        cleanupInstance(instance, config)
    }

    fun uninstallAll() {
        installedCleaners.values.forEach { it.cancel() }
        installedCleaners.clear()
        instanceConfigs.clear()
    }

    private fun cleanupInstance(instance: Instance, config: EntityCleanupConfig) {
        val entities = instance.entities
            .filter { it.entityType !in config.excludedTypes && it !is Player }

        val now = System.currentTimeMillis()
        val maxAgeMs = config.maxAge.toMillis()

        val aged = entities
            .filter { now - it.aliveTicks * 50L > maxAgeMs }
            .sortedWith(entityCleanupComparator())

        aged.forEach { it.remove() }

        val remaining = instance.entities
            .filter { it.entityType !in config.excludedTypes && it !is Player }

        if (remaining.size > config.maxPerInstance) {
            val excess = remaining
                .sortedWith(entityCleanupComparator())
                .take(remaining.size - config.maxPerInstance)

            if (config.warningAtSeconds > 0 && excess.isNotEmpty()) {
                broadcastWarning(instance, config, excess.size)
            }

            excess.forEach { it.remove() }
        }
    }

    private fun entityCleanupComparator(): Comparator<Entity> =
        compareBy<Entity> { if (it.entityType == EntityType.ITEM) 0 else 1 }
            .thenBy { it.aliveTicks }

    private fun broadcastWarning(instance: Instance, config: EntityCleanupConfig, count: Int) {
        val message = miniMessage.deserialize(
            config.warningTemplate
                .replace("{time}", config.warningAtSeconds.toString())
                .replace("{count}", count.toString())
        )
        instance.players.forEach { it.sendMessage(message) }
    }
}

inline fun entityCleanup(instance: Instance, block: EntityCleanupConfigBuilder.() -> Unit) {
    EntityCleanupManager.install(instance, EntityCleanupConfigBuilder().apply(block).build())
}
