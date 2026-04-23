package me.nebula.orbit.utils.commandbuilder

import me.nebula.ether.utils.ratelimit.Cooldown
import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.rank.RankManager
import me.nebula.gravity.translation.Keys
import me.nebula.orbit.localeCode
import me.nebula.orbit.translation.translate
import me.nebula.orbit.user.OrbitOnlineUser
import me.nebula.orbit.user.asNebulaUser
import me.nebula.orbit.utils.chat.miniMessage
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.arguments.Argument
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.command.builder.suggestion.SuggestionEntry
import net.minestom.server.entity.Player
import java.time.Duration
import java.util.UUID

data class SuggestionScope(
    val player: Player,
    val context: CommandContext,
    val partial: String,
) {
    fun priorArg(name: String): String? = if (context.has(name)) context.getRaw(name) else null
}

data class CommandExecutionContext(
    val player: Player,
    val args: CommandContext,
    val locale: String,
) {
    val user: OrbitOnlineUser = player.asNebulaUser()

    fun arg(name: String): String = args.get(ArgumentType.String(name))
    fun argOrNull(name: String): String? =
        if (args.has(name)) args.get(ArgumentType.String(name)) else null
    fun intArg(name: String): Int = args.get(ArgumentType.Integer(name))
    fun intArgOrNull(name: String): Int? =
        if (args.has(name)) args.get(ArgumentType.Integer(name)) else null
    fun doubleArg(name: String): Double = args.get(ArgumentType.Double(name))
    fun floatArg(name: String): Float = args.get(ArgumentType.Float(name))
    fun boolArg(name: String): Boolean = args.get(ArgumentType.Boolean(name))

    fun reply(key: TranslationKey, vararg placeholders: Pair<String, String>) {
        user.sendMessage(user.translate(key, *placeholders))
    }

    fun reply(key: String, vararg placeholders: Pair<String, String>) {
        reply(key.asTranslationKey(), *placeholders)
    }

    fun replyRaw(text: String) {
        user.sendMessage(Component.text(text))
    }

    fun replyMM(text: String) {
        user.sendMessage(miniMessage.deserialize(text))
    }

    fun targetPlayer(argName: String = "player"): Player? {
        val name = argOrNull(argName) ?: return null
        return MinecraftServer.getConnectionManager().findOnlinePlayer(name)
    }

    fun targetPlayerOrSelf(argName: String = "player"): Player {
        val name = argOrNull(argName)
        if (name != null) {
            return MinecraftServer.getConnectionManager().findOnlinePlayer(name) ?: run {
                reply(Keys.Orbit.Command.PlayerNotFound, "name" to name)
                return player
            }
        }
        return player
    }

    fun targetUser(argName: String = "player"): OrbitOnlineUser? =
        targetPlayer(argName)?.asNebulaUser()

    fun targetUserOrSelf(argName: String = "player"): OrbitOnlineUser =
        targetPlayerOrSelf(argName).asNebulaUser()

    fun requireArg(name: String, errorKey: String = "orbit.command.missing_arg"): String? {
        val value = argOrNull(name)
        if (value == null) reply(errorKey, "arg" to name)
        return value
    }
}

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
    @PublishedApi internal var cooldownMs: Long = 0
    @PublishedApi internal var usageKey: TranslationKey? = null

    fun aliases(vararg names: String) { aliases += names }
    fun permission(perm: String) { permission = perm }
    fun playerOnly() { playerOnly = true }
    fun cooldown(ms: Long) { cooldownMs = ms }
    fun usage(translationKey: String) { usageKey = translationKey.asTranslationKey() }

    fun <T> argument(arg: Argument<T>) { arguments += arg }
    fun stringArgument(name: String) { arguments += ArgumentType.String(name) }
    fun intArgument(name: String) { arguments += ArgumentType.Integer(name) }
    fun doubleArgument(name: String) { arguments += ArgumentType.Double(name) }
    fun floatArgument(name: String) { arguments += ArgumentType.Float(name) }
    fun booleanArgument(name: String) { arguments += ArgumentType.Boolean(name) }
    fun wordArgument(name: String) { arguments += ArgumentType.Word(name) }
    fun stringArrayArgument(name: String) { arguments += ArgumentType.StringArray(name) }

    fun suggestArgument(name: String, provider: SuggestionScope.() -> Collection<String>) {
        val arg = arguments.firstOrNull { it.id == name }
            ?: error("No argument named '$name' — add it before calling suggestArgument")
        arg.setSuggestionCallback { sender, context, suggestion ->
            if (sender !is Player) return@setSuggestionCallback
            val partial = suggestion.input.substringAfterLast(" ")
            val scope = SuggestionScope(sender, context, partial)
            provider(scope).forEach { suggestion.addEntry(SuggestionEntry(it)) }
        }
    }

    fun wordArgument(name: String, provider: SuggestionScope.() -> Collection<String>) {
        wordArgument(name)
        suggestArgument(name, provider)
    }

    fun stringArgument(name: String, provider: SuggestionScope.() -> Collection<String>) {
        stringArgument(name)
        suggestArgument(name, provider)
    }

    fun playerArgument(name: String = "player") {
        wordArgument(name) { suggestPlayers(partial, player) }
    }

    fun enumArgument(name: String, values: Collection<String>) {
        arguments += ArgumentType.Word(name).from(*values.toTypedArray())
    }

    fun enumArgument(name: String, vararg values: String) {
        arguments += ArgumentType.Word(name).from(*values)
    }

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
                    val partial = suggestion.input.substringAfterLast(" ")
                    handler(sender, partial).forEach { suggestion.addEntry(SuggestionEntry(it)) }
                }
            }

            resolvedHandler?.let { handler ->
                cmd.addSyntax({ sender, context ->
                    if (playerOnly && sender !is Player) return@addSyntax
                    handler(sender, context)
                }, *arguments.toTypedArray())
            }
        }

        val usageKeyValue = usageKey
        resolvedHandler?.let { handler ->
            cmd.setDefaultExecutor { sender, _ ->
                if (playerOnly && sender !is Player) return@setDefaultExecutor
                if (usageKeyValue != null && sender is Player && arguments.isNotEmpty()) {
                    sender.sendMessage(sender.translate(usageKeyValue))
                    return@setDefaultExecutor
                }
                handler(sender, CommandContext(""))
            }
        } ?: run {
            if (usageKeyValue != null) {
                cmd.setDefaultExecutor { sender, _ ->
                    if (sender is Player) sender.sendMessage(sender.translate(usageKeyValue))
                }
            }
        }

        return cmd
    }

    private val cooldown: Cooldown<UUID>? by lazy {
        if (cooldownMs > 0) Cooldown(Duration.ofMillis(cooldownMs)) else null
    }

    private fun resolveHandler(): ((CommandSender, CommandContext) -> Unit)? {
        val playerHandler = playerExecuteHandler
        if (playerHandler != null) {
            val handler: (CommandSender, CommandContext) -> Unit = handler@{ sender, context ->
                val player = sender as Player
                val cd = cooldown
                if (cd != null && !cd.tryUse(player.uuid)) {
                    val remaining = cd.remainingMs(player.uuid) / 1000.0
                    player.sendMessage(player.translate(Keys.Orbit.Command.Cooldown, "seconds" to "%.1f".format(remaining)))
                    return@handler
                }
                Thread.startVirtualThread {
                    playerHandler(CommandExecutionContext(player, context, player.localeCode))
                }
            }
            return handler
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
