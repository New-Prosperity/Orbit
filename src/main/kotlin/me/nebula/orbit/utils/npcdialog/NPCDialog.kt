package me.nebula.orbit.utils.npcdialog

import me.nebula.orbit.utils.chat.mm
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerChatEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val miniMessage = MiniMessage.miniMessage()
private val commandCounter = AtomicInteger(0)

class DialogOption(
    val label: String,
    val targetPage: String?,
    val onSelect: ((Player) -> Unit)?,
    val subPages: Map<String, DialogPage>,
)

data class DialogPage(
    val id: String,
    val text: String,
    val options: List<DialogOption>,
)

data class DialogTree(
    val npcName: String,
    val pages: Map<String, DialogPage>,
    val rootPage: String,
)

data class ActiveConversation(
    val npcName: String,
    val dialogTree: DialogTree,
    @Volatile var currentPage: String,
)

object DialogManager {

    private val dialogs = ConcurrentHashMap<String, DialogTree>()
    private val conversations = ConcurrentHashMap<UUID, ActiveConversation>()
    private val entityBindings = ConcurrentHashMap<Int, String>()
    private val commandHandlers = ConcurrentHashMap<String, (Player) -> Unit>()
    @Volatile private var installed = false

    fun register(dialog: DialogTree) {
        dialogs[dialog.npcName] = dialog
    }

    fun unregister(npcName: String) {
        dialogs.remove(npcName)
    }

    operator fun get(npcName: String): DialogTree? = dialogs[npcName]

    fun bindEntity(entity: LivingEntity, npcName: String) {
        require(dialogs.containsKey(npcName)) { "Dialog '$npcName' not registered" }
        entityBindings[entity.entityId] = npcName
    }

    fun unbindEntity(entity: LivingEntity) {
        entityBindings.remove(entity.entityId)
    }

    fun dialogForEntity(entityId: Int): String? = entityBindings[entityId]

    fun startConversation(player: Player, npcName: String) {
        val tree = requireNotNull(dialogs[npcName]) { "Dialog '$npcName' not registered" }
        val conversation = ActiveConversation(npcName, tree, tree.rootPage)
        conversations[player.uuid] = conversation
        installIfNeeded()
        displayPage(player, conversation)
    }

    fun endConversation(player: Player) {
        conversations.remove(player.uuid)
    }

    fun isInConversation(player: Player): Boolean = conversations.containsKey(player.uuid)

    fun activeConversation(player: Player): ActiveConversation? = conversations[player.uuid]

    fun navigateTo(player: Player, pageId: String) {
        val conversation = conversations[player.uuid] ?: return
        val page = conversation.dialogTree.pages[pageId] ?: return
        conversation.currentPage = pageId
        displayPage(player, conversation)
    }

    fun registerCommandHandler(commandId: String, handler: (Player) -> Unit) {
        commandHandlers[commandId] = handler
    }

    fun handleCommand(player: Player, commandId: String) {
        commandHandlers.remove(commandId)?.invoke(player)
    }

    fun clear() {
        conversations.clear()
        commandHandlers.clear()
        dialogs.clear()
        entityBindings.clear()
    }

    private fun displayPage(player: Player, conversation: ActiveConversation) {
        val page = conversation.dialogTree.pages[conversation.currentPage] ?: return

        player.sendMessage(Component.empty())
        player.sendMessage(mm("<gold><bold>${conversation.npcName}"))
        player.sendMessage(mm("<gray>${page.text}"))
        player.sendMessage(Component.empty())

        page.options.forEach { option ->
            val commandId = "dialog_${commandCounter.incrementAndGet()}"

            val handler: (Player) -> Unit = handler@{ p ->
                option.onSelect?.invoke(p)
                when {
                    option.targetPage != null -> navigateTo(p, option.targetPage)
                    option.subPages.isNotEmpty() -> {
                        val conv = conversations[p.uuid] ?: return@handler
                        val allPages = conv.dialogTree.pages.toMutableMap()
                        option.subPages.forEach { (id, subPage) ->
                            allPages[id] = subPage
                        }
                        val updatedTree = conv.dialogTree.copy(pages = allPages)
                        conversations[p.uuid] = conv.copy(dialogTree = updatedTree)
                        val firstSubPage = option.subPages.keys.firstOrNull()
                        if (firstSubPage != null) navigateTo(p, firstSubPage)
                    }
                    else -> endConversation(p)
                }
            }
            commandHandlers[commandId] = handler

            val optionComponent = miniMessage.deserialize("<yellow> > ${option.label}")
                .clickEvent(ClickEvent.runCommand("/dialogresponse $commandId"))
                .hoverEvent(HoverEvent.showText(mm("<gray>Click to select")))
            player.sendMessage(optionComponent)
        }
    }

    private fun installIfNeeded() {
        if (installed) return
        installed = true

        MinecraftServer.getCommandManager().register(object : net.minestom.server.command.builder.Command("dialogresponse") {
            init {
                addSyntax({ sender, context ->
                    val player = sender as? Player ?: return@addSyntax
                    val commandId = context.getRaw("id") ?: return@addSyntax
                    handleCommand(player, commandId)
                }, net.minestom.server.command.builder.arguments.ArgumentType.Word("id"))
            }
        })
    }
}

class DialogOptionBuilder @PublishedApi internal constructor(private val label: String) {

    @PublishedApi internal var targetPage: String? = null
    @PublishedApi internal var onSelectHandler: ((Player) -> Unit)? = null
    @PublishedApi internal val subPages = mutableMapOf<String, DialogPage>()

    fun text(pageText: String) {
        val pageId = "inline_${commandCounter.incrementAndGet()}"
        subPages[pageId] = DialogPage(pageId, pageText, emptyList())
        targetPage = pageId
    }

    fun onSelect(handler: (Player) -> Unit) { onSelectHandler = handler }

    inline fun option(label: String, block: DialogOptionBuilder.() -> Unit = {}) {
        val optionBuilder = DialogOptionBuilder(label).apply(block)
        val builtOption = optionBuilder.buildOption()
        val currentPages = subPages.toMutableMap()
        currentPages.putAll(optionBuilder.subPages)

        val existingTarget = targetPage
        if (existingTarget != null && currentPages.containsKey(existingTarget)) {
            val existingPage = currentPages[existingTarget]!!
            val updatedOptions = existingPage.options + builtOption
            currentPages[existingTarget] = existingPage.copy(options = updatedOptions)
        }

        subPages.clear()
        subPages.putAll(currentPages)
    }

    @PublishedApi internal fun buildOption(): DialogOption = DialogOption(
        label = label,
        targetPage = targetPage,
        onSelect = onSelectHandler,
        subPages = subPages.toMap(),
    )
}

class DialogPageBuilder @PublishedApi internal constructor(private val pageId: String) {

    @PublishedApi internal var pageText: String = ""
    @PublishedApi internal val options = mutableListOf<DialogOption>()
    @PublishedApi internal val subPages = mutableMapOf<String, DialogPage>()

    fun text(text: String) { pageText = text }

    inline fun option(label: String, block: DialogOptionBuilder.() -> Unit = {}) {
        val builder = DialogOptionBuilder(label).apply(block)
        val option = builder.buildOption()
        options.add(option)
        subPages.putAll(builder.subPages)
    }

    @PublishedApi internal fun build(): Pair<DialogPage, Map<String, DialogPage>> {
        val page = DialogPage(pageId, pageText, options.toList())
        return page to subPages.toMap()
    }
}

class NPCDialogBuilder @PublishedApi internal constructor(private val npcName: String) {

    @PublishedApi internal val pages = mutableMapOf<String, DialogPage>()
    @PublishedApi internal var rootPageId: String? = null

    inline fun page(id: String, block: DialogPageBuilder.() -> Unit) {
        val builder = DialogPageBuilder(id).apply(block)
        val (page, subPages) = builder.build()
        pages[id] = page
        pages.putAll(subPages)
        if (rootPageId == null) rootPageId = id
    }

    @PublishedApi internal fun build(): DialogTree {
        require(pages.isNotEmpty()) { "Dialog '$npcName' must have at least one page" }
        return DialogTree(
            npcName = npcName,
            pages = pages.toMap(),
            rootPage = requireNotNull(rootPageId) { "Dialog '$npcName' has no pages" },
        )
    }
}

inline fun npcDialog(npcName: String, block: NPCDialogBuilder.() -> Unit): DialogTree {
    val tree = NPCDialogBuilder(npcName).apply(block).build()
    DialogManager.register(tree)
    return tree
}

fun Player.startDialog(npcName: String) = DialogManager.startConversation(this, npcName)
fun Player.endDialog() = DialogManager.endConversation(this)
val Player.isInDialog: Boolean get() = DialogManager.isInConversation(this)
