package me.nebula.orbit.utils.areaeffect

import me.nebula.orbit.utils.region.Region
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class AreaEffectZone(
    val name: String,
    val region: Region,
    val instance: Instance,
    val effects: List<Potion>,
    val intervalTicks: Int,
    val onEnterHandler: ((Player) -> Unit)?,
    val onExitHandler: ((Player) -> Unit)?,
) {

    @PublishedApi internal val playersInside = ConcurrentHashMap.newKeySet<UUID>()
    @Volatile internal var task: Task? = null

    fun start() {
        task?.cancel()
        task = MinecraftServer.getSchedulerManager()
            .buildTask { tick() }
            .repeat(TaskSchedule.tick(intervalTicks))
            .schedule()
    }

    fun stop() {
        task?.cancel()
        task = null
        playersInside.forEach { uuid ->
            instance.players.firstOrNull { it.uuid == uuid }?.let { player ->
                effects.forEach { player.removeEffect(it.effect()) }
            }
        }
        playersInside.clear()
    }

    fun isInside(player: Player): Boolean = playersInside.contains(player.uuid)

    private fun tick() {
        val current = instance.players.filter { region.contains(it.position) }
        val currentUuids = current.map { it.uuid }.toSet()

        current.forEach { player ->
            if (playersInside.add(player.uuid)) {
                onEnterHandler?.invoke(player)
            }
            effects.forEach { player.addEffect(it) }
        }

        val exited = playersInside.filter { it !in currentUuids }
        exited.forEach { uuid ->
            playersInside.remove(uuid)
            instance.players.firstOrNull { it.uuid == uuid }?.let { player ->
                effects.forEach { player.removeEffect(it.effect()) }
                onExitHandler?.invoke(player)
            }
        }
    }
}

class AreaEffectBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var region: Region? = null
    @PublishedApi internal var instance: Instance? = null
    @PublishedApi internal val effects = mutableListOf<Potion>()
    @PublishedApi internal var intervalTicks: Int = 20
    @PublishedApi internal var onEnterHandler: ((Player) -> Unit)? = null
    @PublishedApi internal var onExitHandler: ((Player) -> Unit)? = null

    fun region(region: Region) { this.region = region }
    fun instance(inst: Instance) { this.instance = inst }

    fun effect(effect: PotionEffect, amplifier: Int, durationTicks: Int = 100) {
        effects.add(Potion(effect, amplifier, durationTicks))
    }

    fun interval(ticks: Int) { intervalTicks = ticks }
    fun onEnter(handler: (Player) -> Unit) { onEnterHandler = handler }
    fun onExit(handler: (Player) -> Unit) { onExitHandler = handler }

    @PublishedApi internal fun build(): AreaEffectZone = AreaEffectZone(
        name = name,
        region = requireNotNull(region) { "AreaEffect '$name' requires a region" },
        instance = requireNotNull(instance) { "AreaEffect '$name' requires an instance" },
        effects = effects.toList(),
        intervalTicks = intervalTicks,
        onEnterHandler = onEnterHandler,
        onExitHandler = onExitHandler,
    )
}

inline fun areaEffect(name: String, block: AreaEffectBuilder.() -> Unit): AreaEffectZone =
    AreaEffectBuilder(name).apply(block).build()

object AreaEffectManager {

    private val zones = ConcurrentHashMap<String, AreaEffectZone>()

    fun register(zone: AreaEffectZone) {
        require(!zones.containsKey(zone.name)) { "AreaEffect '${zone.name}' already registered" }
        zones[zone.name] = zone
        zone.start()
    }

    fun unregister(name: String) {
        zones.remove(name)?.stop()
    }

    operator fun get(name: String): AreaEffectZone? = zones[name]
    fun require(name: String): AreaEffectZone = requireNotNull(zones[name]) { "AreaEffect '$name' not found" }
    fun all(): Map<String, AreaEffectZone> = zones.toMap()

    fun zonesAt(player: Player): List<AreaEffectZone> =
        zones.values.filter { it.instance == player.instance && it.region.contains(player.position) }

    fun stopAll() {
        zones.values.forEach { it.stop() }
        zones.clear()
    }
}
