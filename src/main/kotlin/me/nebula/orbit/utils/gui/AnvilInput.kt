package me.nebula.orbit.utils.gui

import me.nebula.ether.utils.logging.logger
import me.nebula.ether.utils.translation.TranslationKey
import me.nebula.orbit.utils.itembuilder.itemStack
import me.nebula.orbit.utils.scheduler.delay
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryCloseEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.player.PlayerAnvilInputEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.type.AnvilInventory
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

sealed interface AnvilResult {
    data class Submitted(val text: String) : AnvilResult
    data object Cancelled : AnvilResult
}

sealed interface AnvilValidation {
    data object Valid : AnvilValidation
    data class Invalid(val messageKey: TranslationKey?, val literalMessage: String? = null) : AnvilValidation
}

object AnvilInput {

    private val logger = logger("AnvilInput")
    private val activeInputs = ConcurrentHashMap<UUID, AnvilSession>()

    private class AnvilSession(
        val inventory: AnvilInventory,
        val eventNode: EventNode<Event>,
        val decided: AtomicBoolean,
        var latestInput: String,
    )

    fun open(
        player: Player,
        title: String,
        default: String = "",
        maxLength: Int = 40,
        iconMaterial: Material = Material.PAPER,
        validator: (String) -> AnvilValidation = { AnvilValidation.Valid },
        callback: (AnvilResult) -> Unit,
    ) {
        close(player)

        val inv = AnvilInventory(Component.text(title))
        val placeholder = itemStack(iconMaterial) {
            name(default.ifEmpty { " " })
            clean()
        }
        inv.setItemStack(0, placeholder)

        val decided = AtomicBoolean(false)
        val node = EventNode.all("anvil-input-${System.identityHashCode(inv)}")
        val session = AnvilSession(inv, node, decided, default)
        activeInputs[player.uuid] = session

        node.addListener(PlayerAnvilInputEvent::class.java) { event ->
            if (event.inventory !== inv) return@addListener
            val input = event.input.orEmpty()
            session.latestInput = input.take(maxLength)
        }

        node.addListener(InventoryPreClickEvent::class.java) { event ->
            if (event.inventory !== inv) return@addListener
            val click = event.click
            val clickType = GuiClickType.fromMinestom(click)
            if (clickType.isDrag || clickType.isHotbarShortcut || clickType == GuiClickType.UNKNOWN) {
                event.isCancelled = true
                return@addListener
            }
            if (event.slot != 2) {
                event.isCancelled = true
                return@addListener
            }
            event.isCancelled = true
            val text = session.latestInput.take(maxLength)
            when (val v = validator(text)) {
                AnvilValidation.Valid -> {
                    if (decided.compareAndSet(false, true)) {
                        cleanup(player, session, inv)
                        event.player.closeInventory()
                        callback(AnvilResult.Submitted(text))
                    }
                }
                is AnvilValidation.Invalid -> {
                    val msg = v.messageKey?.let { Component.text(it.value) }
                        ?: v.literalMessage?.let { Component.text(it) }
                    msg?.let { event.player.sendMessage(it) }
                }
            }
        }
        node.addListener(InventoryCloseEvent::class.java) { event ->
            if (event.inventory !== inv) return@addListener
            if (decided.compareAndSet(false, true)) {
                cleanup(event.player, session, inv)
                callback(AnvilResult.Cancelled)
            }
        }
        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            if (event.player.uuid != player.uuid) return@addListener
            if (decided.compareAndSet(false, true)) {
                cleanup(event.player, session, inv)
                runCatching { callback(AnvilResult.Cancelled) }
                    .onFailure { logger.warn(it) { "AnvilInput cancellation callback threw" } }
            }
        }

        MinecraftServer.getGlobalEventHandler().addChild(node)
        player.openInventory(inv)

        delay(1) {
            if (!decided.get() && player.openInventory === inv) {
                inv.setItemStack(2, placeholder.withCustomName(Component.text(default.ifEmpty { " " })))
            }
        }
    }

    fun close(player: Player) {
        val session = activeInputs.remove(player.uuid) ?: return
        if (session.decided.compareAndSet(false, true)) {
            cleanup(player, session, session.inventory)
        }
    }

    fun isOpen(player: Player): Boolean = activeInputs.containsKey(player.uuid)

    internal fun onPlayerDisconnect(uuid: UUID) {
        val session = activeInputs.remove(uuid) ?: return
        session.decided.compareAndSet(false, true)
        MinecraftServer.getGlobalEventHandler().removeChild(session.eventNode)
    }

    private fun cleanup(player: Player, session: AnvilSession, inv: Inventory) {
        activeInputs.remove(player.uuid, session)
        MinecraftServer.getGlobalEventHandler().removeChild(session.eventNode)
    }

    internal fun activeCountForTest(): Int = activeInputs.size
}

private fun ItemStack.withCustomName(component: Component): ItemStack =
    with(DataComponents.CUSTOM_NAME, component)
