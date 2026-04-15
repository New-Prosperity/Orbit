package me.nebula.orbit.event

import me.nebula.ether.utils.logging.logger
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

class GameEventBus {

    private val logger = logger("GameEventBus")
    private val handlers = CopyOnWriteArrayList<Handler<*>>()

    @PublishedApi
    internal data class Handler<E : GameEvent>(
        val type: KClass<E>,
        val action: (E) -> Unit,
    )

    inner class Subscription internal constructor(private val handler: Handler<*>) {
        fun cancel() { handlers.remove(handler) }
    }

    inline fun <reified E : GameEvent> subscribe(noinline handler: (E) -> Unit): Subscription =
        subscribeInternal(E::class, handler)

    @PublishedApi
    internal fun <E : GameEvent> subscribeInternal(type: KClass<E>, action: (E) -> Unit): Subscription {
        val entry = Handler(type, action)
        handlers += entry
        return Subscription(entry)
    }

    fun publish(event: GameEvent) {
        for (handler in handlers) {
            if (handler.type.isInstance(event)) {
                @Suppress("UNCHECKED_CAST")
                runCatching { (handler as Handler<GameEvent>).action(event) }
                    .onFailure { logger.warn(it) { "Handler for ${handler.type.simpleName} failed" } }
            }
        }
    }

    fun clear() {
        handlers.clear()
    }

    fun size(): Int = handlers.size
}
