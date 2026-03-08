package me.nebula.orbit.mode

import me.nebula.orbit.mode.config.CosmeticConfig
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.instance.InstanceContainer

interface ServerMode {

    val defaultInstance: InstanceContainer

    val spawnPoint: Pos

    val activeInstance: InstanceContainer get() = defaultInstance

    val activeSpawnPoint: Pos get() = spawnPoint

    val maxPlayers: Int get() = 0

    val cosmeticConfig: CosmeticConfig get() = CosmeticConfig()

    fun install(handler: GlobalEventHandler)

    fun shutdown()
}
