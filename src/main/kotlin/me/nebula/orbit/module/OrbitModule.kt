package me.nebula.orbit.module

import me.nebula.ether.utils.module.Module
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

abstract class OrbitModule(name: String) : Module(name, canReload = true) {

    val eventNode: EventNode<Event> = EventNode.all(name)

    protected open fun commands(): List<Command> = emptyList()

    override fun onEnable() {
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        commands().forEach { MinecraftServer.getCommandManager().register(it) }
    }

    override fun onDisable() {
        commands().forEach { MinecraftServer.getCommandManager().unregister(it) }
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
    }

    override fun onReload() {
        onDisable()
        onEnable()
    }
}
