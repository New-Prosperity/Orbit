package me.nebula.orbit.mode

import net.minestom.server.coordinate.Pos
import net.minestom.server.event.GlobalEventHandler
import net.minestom.server.instance.InstanceContainer

interface ServerMode {

    val defaultInstance: InstanceContainer

    val spawnPoint: Pos

    fun install(handler: GlobalEventHandler)

    fun shutdown()
}
