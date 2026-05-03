package me.nebula.orbit.utils.modelengine.mount

import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.entity.Player
import java.util.concurrent.ConcurrentHashMap

object PassiveMountController : MountController {
    override fun tick(modeledEntity: ModeledEntity, driver: Player, input: MountInput) {}
}

object MountControllers {
    private val factories = ConcurrentHashMap<String, () -> MountController>()

    init {
        register("walking") { WalkingController() }
        register("passive") { PassiveMountController }
    }

    fun register(name: String, factory: () -> MountController) {
        factories[name.lowercase()] = factory
    }

    fun resolve(name: String): MountController =
        factories[name.lowercase()]?.invoke() ?: PassiveMountController

    fun resolveFactory(name: String): () -> MountController =
        factories[name.lowercase()] ?: { PassiveMountController }
}
