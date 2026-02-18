package me.nebula.orbit.utils.eventbus

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

typealias EventHandler<T> = (T) -> Unit

class EventBus {

    private val handlers = ConcurrentHashMap<KClass<*>, CopyOnWriteArrayList<EventHandler<*>>>()

    inline fun <reified T : Any> on(noinline handler: EventHandler<T>): Subscription {
        return subscribe(T::class, handler)
    }

    fun <T : Any> subscribe(type: KClass<T>, handler: EventHandler<T>): Subscription {
        handlers.getOrPut(type) { CopyOnWriteArrayList() }.add(handler)
        return Subscription(this, type, handler)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> emit(event: T) {
        val list = handlers[event::class] ?: return
        list.forEach { (it as EventHandler<T>)(event) }
    }

    fun <T : Any> unsubscribe(type: KClass<T>, handler: EventHandler<T>) {
        handlers[type]?.remove(handler)
    }

    fun clear() = handlers.clear()

    fun clear(type: KClass<*>) = handlers.remove(type)

    fun listenerCount(type: KClass<*>): Int = handlers[type]?.size ?: 0
}

class Subscription(
    private val bus: EventBus,
    private val type: KClass<*>,
    private val handler: EventHandler<*>,
) {

    @Suppress("UNCHECKED_CAST")
    fun cancel() {
        bus.unsubscribe(type as KClass<Any>, handler as EventHandler<Any>)
    }
}

val globalEventBus = EventBus()
