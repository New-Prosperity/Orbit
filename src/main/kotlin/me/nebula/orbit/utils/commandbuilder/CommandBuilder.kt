package me.nebula.orbit.utils.commandbuilder

import me.nebula.orbit.utils.permissions.PermissionManager
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.arguments.Argument
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.command.builder.suggestion.SuggestionEntry
import net.minestom.server.entity.Player

class CommandBuilderDsl @PublishedApi internal constructor(
    @PublishedApi internal val name: String,
) {
    @PublishedApi internal val aliases = mutableListOf<String>()
    @PublishedApi internal var permission: String? = null
    @PublishedApi internal var playerOnly: Boolean = false
    @PublishedApi internal val arguments = mutableListOf<Argument<*>>()
    @PublishedApi internal val subCommands = mutableListOf<Command>()
    @PublishedApi internal var executeHandler: ((CommandSender, CommandContext) -> Unit)? = null
    @PublishedApi internal var tabCompleteHandler: ((Player, String) -> List<String>)? = null

    fun aliases(vararg names: String) { aliases.addAll(names) }
    fun permission(perm: String) { permission = perm }
    fun playerOnly() { playerOnly = true }

    fun <T> argument(arg: Argument<T>) { arguments += arg }

    fun argument(name: String, type: Argument<*>) { arguments += type }

    fun stringArgument(name: String) { arguments += ArgumentType.String(name) }
    fun intArgument(name: String) { arguments += ArgumentType.Integer(name) }
    fun doubleArgument(name: String) { arguments += ArgumentType.Double(name) }
    fun floatArgument(name: String) { arguments += ArgumentType.Float(name) }
    fun booleanArgument(name: String) { arguments += ArgumentType.Boolean(name) }
    fun wordArgument(name: String) { arguments += ArgumentType.Word(name) }
    fun stringArrayArgument(name: String) { arguments += ArgumentType.StringArray(name) }

    fun subCommand(name: String, block: CommandBuilderDsl.() -> Unit) {
        subCommands += buildCommand(name, block)
    }

    fun onExecute(handler: (CommandSender, CommandContext) -> Unit) { executeHandler = handler }

    fun onPlayerExecute(handler: (Player, CommandContext) -> Unit) {
        playerOnly = true
        executeHandler = { sender, context -> handler(sender as Player, context) }
    }

    fun tabComplete(handler: (Player, String) -> List<String>) { tabCompleteHandler = handler }

    @PublishedApi internal fun build(): Command {
        val cmd = object : Command(name, *aliases.toTypedArray()) {}

        permission?.let { perm ->
            cmd.setCondition { sender, _ ->
                sender is Player && PermissionManager.hasPermission(sender.uuid, perm)
            }
        }

        if (permission == null && playerOnly) {
            cmd.setCondition { sender, _ -> sender is Player }
        }

        subCommands.forEach { cmd.addSubcommand(it) }

        if (arguments.isNotEmpty()) {
            val lastArg = arguments.last()
            tabCompleteHandler?.let { handler ->
                lastArg.setSuggestionCallback { sender, _, suggestion ->
                    if (sender !is Player) return@setSuggestionCallback
                    val input = suggestion.input
                    handler(sender, input).forEach { suggestion.addEntry(SuggestionEntry(it)) }
                }
            }

            executeHandler?.let { handler ->
                cmd.addSyntax({ sender, context ->
                    if (playerOnly && sender !is Player) return@addSyntax
                    handler(sender, context)
                }, *arguments.toTypedArray())
            }
        }

        executeHandler?.let { handler ->
            cmd.setDefaultExecutor { sender, _ ->
                if (playerOnly && sender !is Player) return@setDefaultExecutor
                handler(sender, CommandContext(""))
            }
        }

        return cmd
    }
}

@PublishedApi internal fun buildCommand(name: String, block: CommandBuilderDsl.() -> Unit): Command =
    CommandBuilderDsl(name).apply(block).build()

inline fun command(name: String, block: CommandBuilderDsl.() -> Unit): Command =
    CommandBuilderDsl(name).apply(block).build()
