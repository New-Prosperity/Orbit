package me.nebula.orbit.utils.customcontent.furniture

import net.minestom.server.entity.Player
import java.util.concurrent.ConcurrentHashMap

fun interface FurnitureCustomHandler {
    fun handle(player: Player, furniture: FurnitureInstance)
}

object FurnitureInteractionRegistry {

    private val handlers = ConcurrentHashMap<String, FurnitureCustomHandler>()

    fun register(handlerId: String, handler: FurnitureCustomHandler) {
        require(handlerId.isNotBlank()) { "Handler id must not be blank" }
        require(!handlers.containsKey(handlerId)) { "Handler already registered: $handlerId" }
        handlers[handlerId] = handler
    }

    fun unregister(handlerId: String) {
        handlers.remove(handlerId)
    }

    operator fun get(handlerId: String): FurnitureCustomHandler? = handlers[handlerId]

    fun ids(): Set<String> = handlers.keys.toSet()

    fun clear() { handlers.clear() }
}
