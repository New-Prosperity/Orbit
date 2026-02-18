package me.nebula.orbit.module

import me.nebula.ether.utils.module.Module
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

abstract class OrbitModule(name: String) : Module(name, canReload = true) {

    val eventNode: EventNode<Event> = EventNode.all(name)

    private val disconnectCallbacks = mutableListOf<(Player) -> Unit>()
    private val instanceKeyedMaps = mutableListOf<Pair<ConcurrentHashMap<*, *>, (Any?) -> Int>>()
    private val instanceKeyedSets = mutableListOf<Pair<MutableSet<*>, (Any?) -> Int>>()
    private var sweepTask: Task? = null

    protected open fun commands(): List<Command> = emptyList()

    fun onPlayerDisconnect(callback: (Player) -> Unit) {
        disconnectCallbacks += callback
    }

    @Suppress("UNCHECKED_CAST")
    fun <K> ConcurrentHashMap<K, *>.cleanOnInstanceRemove(extractHash: (K) -> Int) {
        instanceKeyedMaps += (this as ConcurrentHashMap<*, *>) to (extractHash as (Any?) -> Int)
    }

    @Suppress("UNCHECKED_CAST")
    fun <K> MutableSet<K>.cleanOnInstanceRemove(extractHash: (K) -> Int) {
        instanceKeyedSets += (this as MutableSet<*>) to (extractHash as (Any?) -> Int)
    }

    override fun onEnable() {
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        commands().forEach { MinecraftServer.getCommandManager().register(it) }

        if (disconnectCallbacks.isNotEmpty()) {
            eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
                disconnectCallbacks.forEach { it(event.player) }
            }
        }

        if (instanceKeyedMaps.isNotEmpty() || instanceKeyedSets.isNotEmpty()) {
            sweepTask = MinecraftServer.getSchedulerManager().buildTask {
                val liveHashes = MinecraftServer.getInstanceManager().instances
                    .mapTo(HashSet()) { System.identityHashCode(it) }

                instanceKeyedMaps.forEach { (map, extractor) ->
                    map.keys.removeIf { key -> extractor(key) !in liveHashes }
                }
                instanceKeyedSets.forEach { (set, extractor) ->
                    set.removeIf { key -> extractor(key) !in liveHashes }
                }
            }.repeat(TaskSchedule.seconds(30)).schedule()
        }
    }

    override fun onDisable() {
        sweepTask?.cancel()
        sweepTask = null
        commands().forEach { MinecraftServer.getCommandManager().unregister(it) }
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
    }

    override fun onReload() {
        onDisable()
        onEnable()
    }
}
