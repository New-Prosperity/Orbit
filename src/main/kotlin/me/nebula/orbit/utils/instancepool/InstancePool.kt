package me.nebula.orbit.utils.instancepool

import me.nebula.orbit.translation.translateDefault
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class InstancePool(
    val name: String,
    private val poolSize: Int,
    private val factory: () -> InstanceContainer,
) {
    private val available = ConcurrentLinkedQueue<InstanceContainer>()
    private val inUse = ConcurrentLinkedQueue<InstanceContainer>()
    private val created = AtomicInteger(0)

    fun warmup() {
        repeat(poolSize) {
            val instance = factory()
            available.offer(instance)
            created.incrementAndGet()
        }
    }

    fun acquire(): InstanceContainer {
        val instance = available.poll() ?: factory().also { created.incrementAndGet() }
        inUse.offer(instance)
        return instance
    }

    fun release(instance: InstanceContainer) {
        inUse.remove(instance)
        instance.players.forEach { it.kick(translateDefault("orbit.util.instance_pool.released")) }
        if (available.size < poolSize) {
            available.offer(instance)
        } else {
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
            created.decrementAndGet()
        }
    }

    val availableCount: Int get() = available.size
    val inUseCount: Int get() = inUse.size
    val totalCount: Int get() = created.get()
}

fun instancePool(name: String, poolSize: Int = 3, factory: () -> InstanceContainer): InstancePool =
    InstancePool(name, poolSize, factory)
