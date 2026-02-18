package me.nebula.orbit.utils.commandalias

import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class CommandAlias(
    aliasName: String,
    private val targetCommand: String,
) : Command(aliasName) {

    init {
        val greedy = ArgumentType.StringArray("args")

        setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                MinecraftServer.getCommandManager().execute(sender, targetCommand)
            }
        }

        addSyntax({ sender, context ->
            if (sender is Player) {
                val args = context.get(greedy).joinToString(" ")
                MinecraftServer.getCommandManager().execute(sender, "$targetCommand $args")
            }
        }, greedy)
    }
}

fun registerAlias(alias: String, target: String) {
    MinecraftServer.getCommandManager().register(CommandAlias(alias, target))
}

fun registerAliases(vararg pairs: Pair<String, String>) {
    pairs.forEach { (alias, target) -> registerAlias(alias, target) }
}
