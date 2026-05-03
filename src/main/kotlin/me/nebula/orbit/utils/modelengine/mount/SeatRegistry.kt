package me.nebula.orbit.utils.modelengine.mount

import me.nebula.orbit.utils.modelengine.behavior.MountBehavior
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import java.util.concurrent.ConcurrentHashMap

object SeatRegistry {

    data class Binding(
        val mountBehavior: MountBehavior,
        val modeledEntity: ModeledEntity,
        val controllerFactory: () -> MountController,
    )

    private val byEntityId = ConcurrentHashMap<Int, Binding>()

    fun register(seatEntityId: Int, binding: Binding) {
        byEntityId[seatEntityId] = binding
    }

    fun unregister(seatEntityId: Int) {
        byEntityId.remove(seatEntityId)
    }

    fun get(seatEntityId: Int): Binding? = byEntityId[seatEntityId]

    fun unregisterAllOf(modeledEntity: ModeledEntity) {
        val iter = byEntityId.entries.iterator()
        while (iter.hasNext()) {
            val (_, binding) = iter.next()
            if (binding.modeledEntity === modeledEntity) iter.remove()
        }
    }
}
