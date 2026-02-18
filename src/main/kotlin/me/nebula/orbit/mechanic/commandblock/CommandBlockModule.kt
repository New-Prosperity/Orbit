package me.nebula.orbit.mechanic.commandblock

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import java.util.concurrent.ConcurrentHashMap

private data class BlockKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val COMMAND_BLOCK_NAMES = setOf(
    "minecraft:command_block",
    "minecraft:chain_command_block",
    "minecraft:repeating_command_block",
)

class CommandBlockModule : OrbitModule("command-block") {

    private val storedCommands = ConcurrentHashMap<BlockKey, String>()

    override fun onEnable() {
        super.onEnable()
        storedCommands.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() !in COMMAND_BLOCK_NAMES) return@addListener

            val player = event.player
            if (player.permissionLevel < 2) return@addListener

            val instance = player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = BlockKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val currentCommand = storedCommands[key] ?: ""
            player.sendMessage(player.translate("orbit.mechanic.command_block.info", "command" to currentCommand))
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() !in COMMAND_BLOCK_NAMES) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = BlockKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())
            storedCommands.remove(key)
        }
    }

    fun setCommand(instanceHash: Int, x: Int, y: Int, z: Int, command: String) {
        storedCommands[BlockKey(instanceHash, x, y, z)] = command
    }

    fun executeCommand(instanceHash: Int, x: Int, y: Int, z: Int) {
        val key = BlockKey(instanceHash, x, y, z)
        val command = storedCommands[key] ?: return
        if (command.isBlank()) return
        MinecraftServer.getCommandManager().executeServerCommand(command)
    }

    override fun onDisable() {
        storedCommands.clear()
        super.onDisable()
    }
}
