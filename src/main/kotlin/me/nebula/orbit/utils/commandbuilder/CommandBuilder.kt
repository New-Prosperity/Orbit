package me.nebula.orbit.utils.commandbuilder

import me.nebula.gravity.rank.RankManager
import me.nebula.orbit.Orbit
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.arguments.Argument
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.command.builder.suggestion.SuggestionEntry
import net.minestom.server.entity.Player
import java.util.UUID

data class CommandExecutionContext(
    val player: Player,
    val args: CommandContext,
    val locale: String,
)

class CommandBuilderDsl @PublishedApi internal constructor(
    @PublishedApi internal val name: String,
) {
    @PublishedApi internal val aliases = mutableListOf<String>()
    @PublishedApi internal var permission: String? = null
    @PublishedApi internal var playerOnly: Boolean = false
    @PublishedApi internal val arguments = mutableListOf<Argument<*>>()
    @PublishedApi internal val subCommands = mutableListOf<Command>()
    @PublishedApi internal var executeHandler: ((CommandSender, CommandContext) -> Unit)? = null
    @PublishedApi internal var playerExecuteHandler: ((CommandExecutionContext) -> Unit)? = null
    @PublishedApi internal var tabCompleteHandler: ((Player, String) -> List<String>)? = null

    fun aliases(vararg names: String) { aliases += names }
    fun permission(perm: String) { permission = perm }
    fun playerOnly() { playerOnly = true }

    fun <T> argument(arg: Argument<T>) { arguments += arg }
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

    fun onPlayerExecute(handler: CommandExecutionContext.() -> Unit) {
        playerOnly = true
        playerExecuteHandler = handler
    }

    fun tabComplete(handler: (Player, String) -> List<String>) { tabCompleteHandler = handler }

    @PublishedApi internal fun build(): Command {
        val cmd = object : Command(name, *aliases.toTypedArray()) {}

        val perm = permission
        if (perm != null) {
            cmd.setCondition { sender, _ ->
                sender is Player && RankManager.hasPermission(sender.uuid, perm)
            }
        } else if (playerOnly) {
            cmd.setCondition { sender, _ -> sender is Player }
        }

        subCommands.forEach { cmd.addSubcommand(it) }

        val resolvedHandler = resolveHandler()

        if (arguments.isNotEmpty()) {
            tabCompleteHandler?.let { handler ->
                arguments.last().setSuggestionCallback { sender, _, suggestion ->
                    if (sender !is Player) return@setSuggestionCallback
                    handler(sender, suggestion.input).forEach { suggestion.addEntry(SuggestionEntry(it)) }
                }
            }

            resolvedHandler?.let { handler ->
                cmd.addSyntax({ sender, context ->
                    if (playerOnly && sender !is Player) return@addSyntax
                    handler(sender, context)
                }, *arguments.toTypedArray())
            }
        }

        resolvedHandler?.let { handler ->
            cmd.setDefaultExecutor { sender, _ ->
                if (playerOnly && sender !is Player) return@setDefaultExecutor
                handler(sender, CommandContext(""))
            }
        }

        return cmd
    }

    private fun resolveHandler(): ((CommandSender, CommandContext) -> Unit)? {
        val playerHandler = playerExecuteHandler
        if (playerHandler != null) return { sender, context ->
            val player = sender as Player
            Thread.startVirtualThread {
                playerHandler(CommandExecutionContext(player, context, Orbit.localeOf(player.uuid)))
            }
        }
        val rawHandler = executeHandler
        if (rawHandler != null) return { sender, context ->
            Thread.startVirtualThread { rawHandler(sender, context) }
        }
        return null
    }
}

@PublishedApi internal fun buildCommand(name: String, block: CommandBuilderDsl.() -> Unit): Command =
    CommandBuilderDsl(name).apply(block).build()

inline fun command(name: String, block: CommandBuilderDsl.() -> Unit): Command =
    CommandBuilderDsl(name).apply(block).build()
