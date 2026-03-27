package me.nebula.orbit.utils.gamechat

import me.nebula.orbit.rank
import me.nebula.orbit.rankColor
import me.nebula.orbit.rankPrefix
import me.nebula.orbit.mode.game.PlayerTracker
import me.nebula.orbit.utils.chat.miniMessage
import me.nebula.orbit.utils.cooldown.Cooldown
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerChatEvent
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class ChatContext(
    val sender: Player,
    val rawMessage: String,
    val recipients: MutableSet<Player>,
) {
    var cancelled = false
    val prefixes = mutableListOf<String>()
    var nameColor: String = "<white>"
    var messageColor: String = "<white>"
    var separator: String = "<gray>: "
}

fun interface ChatProcessor {
    fun process(context: ChatContext)
}

class GameChatPipeline {

    private val processors = CopyOnWriteArrayList<Pair<String, ChatProcessor>>()
    private var eventNode: EventNode<*>? = null
    fun addProcessor(name: String, processor: ChatProcessor) {
        processors.add(name to processor)
    }

    fun removeProcessor(name: String) {
        processors.removeAll { it.first == name }
    }

    fun install() {
        val node = EventNode.all("game-chat-pipeline")
        node.addListener(PlayerChatEvent::class.java) { event ->
            event.isCancelled = true
            val context = ChatContext(
                sender = event.player,
                rawMessage = event.rawMessage,
                recipients = event.recipients.toMutableSet(),
            )

            for ((_, processor) in processors) {
                processor.process(context)
                if (context.cancelled) return@addListener
            }

            if (context.recipients.isEmpty()) return@addListener

            val formatted = buildMessage(context)
            for (recipient in context.recipients) {
                recipient.sendMessage(formatted)
            }
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun uninstall() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
    }

    private fun buildMessage(context: ChatContext): Component {
        val builder = StringBuilder()
        for (prefix in context.prefixes) {
            builder.append(prefix)
        }
        builder.append(context.nameColor).append(context.sender.username)
        builder.append(context.separator)
        builder.append(context.messageColor)
        builder.append(miniMessage.escapeTags(context.rawMessage))
        return miniMessage.deserialize(builder.toString())
    }
}

class RankPrefixProcessor : ChatProcessor {
    override fun process(context: ChatContext) {
        val prefix = context.sender.rankPrefix
        if (prefix.isNotEmpty()) {
            context.prefixes.add("$prefix ")
        }
        context.nameColor = "<${context.sender.rankColor}>"
    }
}

class TeamPrefixProcessor(private val tracker: PlayerTracker) : ChatProcessor {
    override fun process(context: ChatContext) {
        val team = tracker.teamOf(context.sender.uuid) ?: return
        context.prefixes.add("<gray>[<white>$team<gray>] ")
    }
}

class DeadPlayerDimProcessor(private val tracker: PlayerTracker) : ChatProcessor {
    override fun process(context: ChatContext) {
        if (!tracker.contains(context.sender.uuid)) return
        if (tracker.isAlive(context.sender.uuid)) return
        context.prefixes.add(0, "<dark_gray>\u2620 ")
        context.nameColor = "<dark_gray>"
        context.messageColor = "<dark_gray>"
    }
}

class SpectatorIsolationProcessor(private val tracker: PlayerTracker) : ChatProcessor {
    override fun process(context: ChatContext) {
        if (!tracker.contains(context.sender.uuid)) return
        val senderAlive = tracker.isAlive(context.sender.uuid)
        context.recipients.removeIf { recipient ->
            if (!tracker.contains(recipient.uuid)) return@removeIf false
            val recipientAlive = tracker.isAlive(recipient.uuid)
            (!senderAlive && recipientAlive) || (senderAlive && !recipientAlive)
        }
    }
}

class MuteCheckProcessor(private val isMuted: (UUID) -> Boolean) : ChatProcessor {
    override fun process(context: ChatContext) {
        if (isMuted(context.sender.uuid)) {
            context.cancelled = true
        }
    }
}

class CooldownProcessor(cooldownMillis: Long) : ChatProcessor {
    private val cooldown = Cooldown<UUID>(Duration.ofMillis(cooldownMillis))

    override fun process(context: ChatContext) {
        if (!cooldown.tryUse(context.sender.uuid)) {
            context.cancelled = true
        }
    }

    fun clear() = cooldown.resetAll()
}

class RadiusChatProcessor(private val radius: Double) : ChatProcessor {
    override fun process(context: ChatContext) {
        val senderPos = context.sender.position
        context.recipients.removeIf { it.position.distance(senderPos) > radius }
    }
}

class GameChatPipelineBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val processors = mutableListOf<Pair<String, ChatProcessor>>()

    fun processor(name: String, processor: ChatProcessor) {
        processors.add(name to processor)
    }

    @PublishedApi internal fun build(): GameChatPipeline {
        val pipeline = GameChatPipeline()
        processors.forEach { (name, processor) -> pipeline.addProcessor(name, processor) }
        return pipeline
    }
}

inline fun gameChatPipeline(block: GameChatPipelineBuilder.() -> Unit): GameChatPipeline =
    GameChatPipelineBuilder().apply(block).build()
