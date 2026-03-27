package me.nebula.orbit.utils.hotbar

import me.nebula.orbit.utils.condition.Condition
import me.nebula.orbit.utils.gui.CUSTOM_GUI_TAG
import me.nebula.orbit.utils.scheduler.repeat
import me.nebula.orbit.utils.sound.playSound
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.event.player.PlayerSwapItemEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

val HOTBAR_ITEM_TAG: Tag<String> = Tag.String("nebula:hotbar_item")
val ACTIVE_HOTBAR_TAG: Tag<String> = Tag.String("nebula:active_hotbar")

enum class ClickType { RIGHT, LEFT, SHIFT_RIGHT, SHIFT_LEFT }

class HotbarSlot(
    val id: String,
    val slot: Int,
    val item: ItemStack,
    val dynamicItem: ((Player) -> ItemStack)?,
    val visibleWhen: Condition<Player>?,
    val cooldownTicks: Int,
    val sound: SoundEvent?,
    val soundPitch: Float,
    val onClick: ((Player, ClickType) -> Unit)?,
)

class Hotbar(
    val name: String,
    val slots: Map<Int, HotbarSlot>,
    val clearOtherSlots: Boolean,
    val lockInventory: Boolean,
    val preventDrop: Boolean,
    val preventSwap: Boolean,
    val preventPlace: Boolean,
    val refreshIntervalTicks: Int,
) {
    private val slotsById: Map<String, HotbarSlot> = slots.values.associateBy { it.id }
    private val cooldowns = ConcurrentHashMap<Long, Long>()
    private val playerOverrides = ConcurrentHashMap<UUID, ConcurrentHashMap<Int, ItemStack>>()
    val eventNode: EventNode<net.minestom.server.event.Event> = EventNode.all("hotbar:$name")
    private var refreshTask: Task? = null

    private fun isOurs(player: Player): Boolean =
        player.getTag(ACTIVE_HOTBAR_TAG) == name

    private fun resolveSlot(player: Player): HotbarSlot? {
        val id = player.itemInMainHand.getTag(HOTBAR_ITEM_TAG) ?: return null
        return slotsById[id]
    }

    init {
        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (!isOurs(event.player)) return@addListener
            val hotbarSlot = resolveSlot(event.player) ?: return@addListener
            handleClick(event.player, hotbarSlot, ClickType.RIGHT)
        }

        eventNode.addListener(PlayerHandAnimationEvent::class.java) { event ->
            if (!isOurs(event.player)) return@addListener
            val hotbarSlot = resolveSlot(event.player) ?: return@addListener
            if (hotbarSlot.onClick != null) handleClick(event.player, hotbarSlot, ClickType.LEFT)
        }

        if (lockInventory) {
            eventNode.addListener(InventoryPreClickEvent::class.java) { event ->
                if (!isOurs(event.player)) return@addListener
                val openInv = event.player.openInventory
                if (openInv == null) {
                    event.isCancelled = true
                } else if (openInv.getTag(CUSTOM_GUI_TAG)) {
                    if (event.slot >= openInv.size) event.isCancelled = true
                } else {
                    event.isCancelled = true
                }
            }
        }

        if (preventDrop) {
            eventNode.addListener(net.minestom.server.event.item.ItemDropEvent::class.java) { event ->
                if (!isOurs(event.player)) return@addListener
                event.isCancelled = true
            }
        }

        if (preventSwap) {
            eventNode.addListener(PlayerSwapItemEvent::class.java) { event ->
                if (!isOurs(event.player)) return@addListener
                event.isCancelled = true
            }
        }

        if (preventPlace) {
            eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
                if (!isOurs(event.player)) return@addListener
                event.isCancelled = true
            }
            eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
                if (!isOurs(event.player)) return@addListener
                val heldItem = event.player.itemInMainHand
                if (heldItem.getTag(HOTBAR_ITEM_TAG) != null) event.isCancelled = true
            }
        }

        eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
            playerOverrides.remove(event.player.uuid)
        }
    }

    fun apply(player: Player) {
        if (clearOtherSlots) {
            for (i in 0..8) player.inventory.setItemStack(i, ItemStack.AIR)
        }
        for ((slot, entry) in slots) {
            if (entry.visibleWhen != null && !entry.visibleWhen.test(player)) continue
            val override = playerOverrides[player.uuid]?.get(slot)
            val item = override ?: entry.dynamicItem?.invoke(player)?.withTag(HOTBAR_ITEM_TAG, entry.id) ?: entry.item
            player.inventory.setItemStack(slot, item)
        }
        player.setTag(ACTIVE_HOTBAR_TAG, name)
    }

    fun remove(player: Player) {
        player.removeTag(ACTIVE_HOTBAR_TAG)
        playerOverrides.remove(player.uuid)
        if (clearOtherSlots) {
            for (i in 0..8) player.inventory.setItemStack(i, ItemStack.AIR)
        } else {
            for (slot in slots.keys) player.inventory.setItemStack(slot, ItemStack.AIR)
        }
    }

    fun refresh(player: Player) {
        if (!isOurs(player)) return
        for ((slot, entry) in slots) {
            val visible = entry.visibleWhen?.test(player) != false
            val override = playerOverrides[player.uuid]?.get(slot)
            val item = if (visible) {
                override ?: entry.dynamicItem?.invoke(player)?.withTag(HOTBAR_ITEM_TAG, entry.id) ?: entry.item
            } else ItemStack.AIR
            player.inventory.setItemStack(slot, item)
        }
    }

    fun refreshAll() {
        MinecraftServer.getConnectionManager().onlinePlayers
            .filter { isOurs(it) }
            .forEach { refresh(it) }
    }

    fun refreshSlot(player: Player, slot: Int) {
        if (!isOurs(player)) return
        val entry = slots[slot] ?: return
        val visible = entry.visibleWhen?.test(player) != false
        val override = playerOverrides[player.uuid]?.get(slot)
        val item = if (visible) {
            override ?: entry.dynamicItem?.invoke(player)?.withTag(HOTBAR_ITEM_TAG, entry.id) ?: entry.item
        } else ItemStack.AIR
        player.inventory.setItemStack(slot, item)
    }

    fun overrideSlot(player: Player, slot: Int, item: ItemStack) {
        val entry = slots[slot] ?: return
        val tagged = item.withTag(HOTBAR_ITEM_TAG, entry.id)
        playerOverrides.getOrPut(player.uuid) { ConcurrentHashMap() }[slot] = tagged
        if (isOurs(player)) player.inventory.setItemStack(slot, tagged)
    }

    fun clearOverride(player: Player, slot: Int) {
        playerOverrides[player.uuid]?.remove(slot)
        refreshSlot(player, slot)
    }

    fun clearOverrides(player: Player) {
        playerOverrides.remove(player.uuid)
        refresh(player)
    }

    fun isActive(player: Player): Boolean = isOurs(player)

    fun install() {
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        if (refreshIntervalTicks > 0) {
            refreshTask = repeat(refreshIntervalTicks) { refreshAll() }
        }
    }

    fun uninstall() {
        refreshTask?.cancel()
        refreshTask = null
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
        MinecraftServer.getConnectionManager().onlinePlayers
            .filter { isOurs(it) }
            .forEach { remove(it) }
        cooldowns.clear()
        playerOverrides.clear()
    }

    private fun handleClick(player: Player, slot: HotbarSlot, type: ClickType) {
        val handler = slot.onClick ?: return
        if (slot.visibleWhen != null && !slot.visibleWhen.test(player)) return

        if (slot.cooldownTicks > 0) {
            val key = player.uuid.mostSignificantBits xor slot.id.hashCode().toLong()
            val now = System.currentTimeMillis()
            val cooldownMs = slot.cooldownTicks * 50L
            val lastUse = cooldowns[key]
            if (lastUse != null && now - lastUse < cooldownMs) return
            cooldowns[key] = now
        }

        slot.sound?.let { player.playSound(it, 1f, slot.soundPitch) }
        handler(player, type)
    }
}

class HotbarSlotBuilder @PublishedApi internal constructor(
    @PublishedApi internal val slot: Int,
    @PublishedApi internal var item: ItemStack,
) {
    @PublishedApi internal var id: String = "slot_$slot"
    @PublishedApi internal var dynamicItem: ((Player) -> ItemStack)? = null
    @PublishedApi internal var visibleWhen: Condition<Player>? = null
    @PublishedApi internal var cooldownTicks: Int = 4
    @PublishedApi internal var sound: SoundEvent? = null
    @PublishedApi internal var soundPitch: Float = 1f
    @PublishedApi internal var onClick: ((Player, ClickType) -> Unit)? = null

    fun id(value: String) { id = value }
    fun dynamicItem(provider: (Player) -> ItemStack) { dynamicItem = provider }
    fun visibleWhen(condition: Condition<Player>) { visibleWhen = condition }
    fun visibleWhen(predicate: (Player) -> Boolean) { visibleWhen = Condition { predicate(it) } }
    fun cooldown(ticks: Int) { cooldownTicks = ticks }
    fun sound(event: SoundEvent, pitch: Float = 1f) { sound = event; soundPitch = pitch }
    fun onClick(handler: (Player) -> Unit) { onClick = { player, _ -> handler(player) } }
    fun onClick(handler: (Player, ClickType) -> Unit) { onClick = handler }

    @PublishedApi internal fun build(): HotbarSlot {
        val taggedItem = item.withTag(HOTBAR_ITEM_TAG, id)
        return HotbarSlot(id, slot, taggedItem, dynamicItem, visibleWhen, cooldownTicks, sound, soundPitch, onClick)
    }
}

class HotbarBuilder @PublishedApi internal constructor(val name: String) {
    @PublishedApi internal val slots = mutableMapOf<Int, HotbarSlot>()
    @PublishedApi internal var clearOtherSlots: Boolean = true
    @PublishedApi internal var lockInventory: Boolean = true
    @PublishedApi internal var preventDrop: Boolean = true
    @PublishedApi internal var preventSwap: Boolean = true
    @PublishedApi internal var preventPlace: Boolean = true
    @PublishedApi internal var refreshIntervalTicks: Int = 0

    fun clearOtherSlots(value: Boolean) { clearOtherSlots = value }
    fun lockInventory(value: Boolean) { lockInventory = value }
    fun preventDrop(value: Boolean) { preventDrop = value }
    fun preventSwap(value: Boolean) { preventSwap = value }
    fun preventPlace(value: Boolean) { preventPlace = value }
    fun refreshEvery(ticks: Int) { refreshIntervalTicks = ticks }

    fun slot(slot: Int, item: ItemStack, onClick: (Player) -> Unit) {
        require(slot in 0..8) { "Hotbar slot must be 0-8" }
        val id = "slot_$slot"
        val tagged = item.withTag(HOTBAR_ITEM_TAG, id)
        slots[slot] = HotbarSlot(id, slot, tagged, null, null, 4, null, 1f) { player, _ -> onClick(player) }
    }

    fun slot(slot: Int, item: ItemStack, visibleWhen: Condition<Player>, onClick: (Player) -> Unit) {
        require(slot in 0..8) { "Hotbar slot must be 0-8" }
        val id = "slot_$slot"
        val tagged = item.withTag(HOTBAR_ITEM_TAG, id)
        slots[slot] = HotbarSlot(id, slot, tagged, null, visibleWhen, 4, null, 1f) { player, _ -> onClick(player) }
    }

    fun configuredSlot(slot: Int, item: ItemStack, block: HotbarSlotBuilder.() -> Unit) {
        require(slot in 0..8) { "Hotbar slot must be 0-8" }
        slots[slot] = HotbarSlotBuilder(slot, item).apply(block).build()
    }

    @PublishedApi internal fun build(): Hotbar = Hotbar(
        name, slots.toMap(), clearOtherSlots, lockInventory,
        preventDrop, preventSwap, preventPlace, refreshIntervalTicks,
    )
}

inline fun hotbar(name: String, block: HotbarBuilder.() -> Unit): Hotbar =
    HotbarBuilder(name).apply(block).build()
