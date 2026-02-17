package me.nebula.orbit.command

import me.nebula.gravity.rank.RankManager
import me.nebula.gravity.session.SessionStore
import me.nebula.orbit.Orbit
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.command.builder.suggestion.SuggestionEntry
import net.minestom.server.entity.Player

data class OrbitCommandContext(
    val player: Player,
    val args: Array<String>,
    val locale: String
)

class OrbitCommandBuilder {
    var permission: String? = null
    var playerOnly: Boolean = true
    internal var executeBlock: OrbitCommandContext.() -> Unit = {}
    internal var suggestBlock: (CommandSender, Array<String>) -> List<String> = { _, _ -> emptyList() }

    fun execute(block: OrbitCommandContext.() -> Unit) {
        executeBlock = block
    }

    fun suggest(block: (CommandSender, Array<String>) -> List<String>) {
        suggestBlock = block
    }
}

fun minestomCommand(name: String, vararg aliases: String, block: OrbitCommandBuilder.() -> Unit): Command {
    val builder = OrbitCommandBuilder().apply(block)
    return object : Command(name, *aliases) {
        init {
            builder.permission?.let { perm ->
                setCondition { sender, _ ->
                    sender is Player && RankManager.hasPermission(sender.uuid, perm)
                }
            }

            val argsArgument = ArgumentType.StringArray("args")
            argsArgument.setDefaultValue(emptyArray())
            argsArgument.setSuggestionCallback { sender, _, suggestion ->
                val input = suggestion.input.split(" ").drop(1).toTypedArray()
                builder.suggestBlock(sender, input).forEach { suggestion.addEntry(SuggestionEntry(it)) }
            }

            setDefaultExecutor { sender, _ ->
                if (builder.playerOnly && sender !is Player) return@setDefaultExecutor
                val player = sender as Player
                Thread.startVirtualThread {
                    val ctx = OrbitCommandContext(
                        player = player,
                        args = emptyArray(),
                        locale = Orbit.localeOf(player.uuid)
                    )
                    builder.executeBlock(ctx)
                }
            }

            addSyntax({ sender, context ->
                if (builder.playerOnly && sender !is Player) return@addSyntax
                val player = sender as Player
                Thread.startVirtualThread {
                    val ctx = OrbitCommandContext(
                        player = player,
                        args = context.get(argsArgument),
                        locale = Orbit.localeOf(player.uuid)
                    )
                    builder.executeBlock(ctx)
                }
            }, argsArgument)
        }
    }
}

object OnlinePlayerCache {
    @Volatile
    var names: List<String> = emptyList()

    fun refresh() {
        names = SessionStore.all().map { it.playerName }
    }
}

fun suggestPlayers(prefix: String): List<String> =
    OnlinePlayerCache.names.filter { it.startsWith(prefix, ignoreCase = true) }
