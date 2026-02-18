package me.nebula.orbit.utils.world

import me.nebula.orbit.translation.translateDefault
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.InstanceManager
import net.minestom.server.instance.block.Block
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object WorldManager {

    private val worlds = ConcurrentHashMap<String, InstanceContainer>()
    private val instanceManager: InstanceManager get() = MinecraftServer.getInstanceManager()

    fun create(name: String, block: WorldBuilder.() -> Unit = {}): InstanceContainer {
        require(!worlds.containsKey(name)) { "World '$name' already exists" }
        val builder = WorldBuilder().apply(block)
        val instance = instanceManager.createInstanceContainer()
        builder.generator?.let { instance.setGenerator(it) }
        builder.spawnPoint?.let { /* stored for reference */ }
        worlds[name] = instance
        return instance
    }

    fun get(name: String): InstanceContainer? = worlds[name]

    fun require(name: String): InstanceContainer =
        requireNotNull(worlds[name]) { "World '$name' not found" }

    fun delete(name: String) {
        val instance = worlds.remove(name) ?: return
        instance.players.forEach { it.kick(translateDefault("orbit.util.world.deleted")) }
        instanceManager.unregisterInstance(instance)
    }

    fun all(): Map<String, InstanceContainer> = worlds.toMap()

    fun names(): Set<String> = worlds.keys.toSet()

    fun createVoid(name: String): InstanceContainer = create(name)

    fun createFlat(name: String, height: Int = 40, material: Block = Block.GRASS_BLOCK): InstanceContainer =
        create(name) {
            generator { unit -> unit.modifier().fillHeight(0, height, material) }
        }
}

class WorldBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var generator: ((net.minestom.server.instance.generator.GenerationUnit) -> Unit)? = null
    @PublishedApi internal var spawnPoint: Pos? = null

    fun generator(gen: (net.minestom.server.instance.generator.GenerationUnit) -> Unit) {
        generator = gen
    }

    fun spawn(pos: Pos) { spawnPoint = pos }

    fun void() { generator = null }

    fun flat(height: Int = 40, material: Block = Block.GRASS_BLOCK) {
        generator = { unit -> unit.modifier().fillHeight(0, height, material) }
    }
}
