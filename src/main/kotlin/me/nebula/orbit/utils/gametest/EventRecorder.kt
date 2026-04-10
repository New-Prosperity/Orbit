package me.nebula.orbit.utils.gametest

import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.util.concurrent.CopyOnWriteArrayList

class EventCapture<E : Event>(
    private val eventType: Class<E>,
) {

    private val _events = CopyOnWriteArrayList<E>()

    val events: List<E> get() = _events.toList()
    val count: Int get() = _events.size

    fun last(): E = _events.last()

    fun first(): E = _events.first()

    fun any(predicate: (E) -> Boolean): Boolean = _events.any(predicate)

    fun none(predicate: (E) -> Boolean): Boolean = _events.none(predicate)

    fun filter(predicate: (E) -> Boolean): List<E> = _events.filter(predicate)

    fun clear() = _events.clear()

    internal fun add(event: E) {
        _events.add(event)
    }
}

class EventRecorder {

    private val node = EventNode.all("gametest-event-recorder")
    private val captures = mutableMapOf<Class<out Event>, EventCapture<*>>()

    inline fun <reified E : Event> record(): EventCapture<E> = record(E::class.java)

    @Suppress("UNCHECKED_CAST")
    fun <E : Event> record(eventType: Class<E>): EventCapture<E> {
        val existing = captures[eventType]
        if (existing != null) return existing as EventCapture<E>

        val capture = EventCapture(eventType)
        captures[eventType] = capture
        node.addListener(eventType) { event -> capture.add(event) }
        return capture
    }

    @Suppress("UNCHECKED_CAST")
    fun <E : Event> captureOf(eventType: Class<E>): EventCapture<E>? =
        captures[eventType] as? EventCapture<E>

    fun totalEventCount(): Int = captures.values.sumOf { it.count }

    fun install() {
        MinecraftServer.getGlobalEventHandler().addChild(node)
    }

    fun uninstall() {
        runCatching { MinecraftServer.getGlobalEventHandler().removeChild(node) } // noqa: dangling runCatching
    }
}
