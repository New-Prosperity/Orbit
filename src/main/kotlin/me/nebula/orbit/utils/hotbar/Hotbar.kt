package me.nebula.orbit.utils.hotbar

import me.nebula.orbit.utils.condition.Condition
import me.nebula.orbit.utils.sound.playSound
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.event.player.PlayerSwapItemEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ClickType { RIGHT, LEFT, SHIFT_RIGHT, SHIFT_LEFT }

class HotbarSlot(
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
    private val activePlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val cooldowns = ConcurrentHashMap<Long, Long>()
    private val playerOverrides = ConcurrentHashMap<UUID, ConcurrentHashMap<Int, ItemStack>>()
    val eventNode: EventNode<net.minestom.server.event.Event> = EventNode.all("hotbar:$name")
    private var refreshTask: net.minestom.server.timer.Task? = null

    init {
        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (!activePlayers.contains(event.player.uuid)) return@addListener
            val held = event.player.heldSlot.toInt()
            val hotbarSlot = slots[held] ?: return@addListener
            handleClick(event.player, hotbarSlot, ClickType.RIGHT)
        }

        eventNode.addListener(PlayerHandAnimationEvent::class.java) { event ->
            if (!activePlayers.contains(event.player.uuid)) return@addListener
            val held = event.player.heldSlot.toInt()
            val hotbarSlot = slots[held] ?: return@addListener
            if (hotbarSlot.onClick != null) handleClick(event.player, hotbarSlot, ClickType.LEFT)
        }

        if (lockInventory) {
            eventNode.addListener(InventoryPreClickEvent::class.java) { event ->
                if (!activePlayers.contains(event.player.uuid)) return@addListener
                val openInv = event.player.openInventory
                if (openInv == null) {
                    event.isCancelled = true
                } else if (event.slot >= openInv.size) {
                    event.isCancelled = true
                }
            }
        }

        if (preventDrop) {
            eventNode.addListener(net.minestom.server.event.item.ItemDropEvent::class.java) { event ->
                if (!activePlayers.contains(event.player.uuid)) return@addListener
                event.isCancelled = true
            }
        }

        if (preventSwap) {
            eventNode.addListener(PlayerSwapItemEvent::class.java) { event ->
                if (!activePlayers.contains(event.player.uuid)) return@addListener
                event.isCancelled = true
            }
        }

        if (preventPlace) {
            eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
                if (!activePlayers.contains(event.player.uuid)) return@addListener
                event.isCancelled = true
            }
            eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
                if (!activePlayers.contains(event.player.uuid)) return@addListener
                val held = event.player.heldSlot.toInt()
                if (slots.containsKey(held)) event.isCancelled = true
            }
        }
    }

    fun apply(player: Player) {
        if (clearOtherSlots) {
            for (i in 0..8) player.inventory.setItemStack(i, ItemStack.AIR)
        }
        for ((slot, entry) in slots) {
            if (entry.visibleWhen != null && !entry.visibleWhen.test(player)) continue
            val override = playerOverrides[player.uuid]?.get(slot)
            val item = override ?: entry.dynamicItem?.invoke(player) ?: entry.item
            player.inventory.setItemStack(slot, item)
        }
        activePlayers.add(player.uuid)
    }

    fun remove(player: Player) {
        activePlayers.remove(player.uuid)
        playerOverrides.remove(player.uuid)
        if (clearOtherSlots) {
            for (i in 0..8) player.inventory.setItemStack(i, ItemStack.AIR)
        } else {
            for (slot in slots.keys) player.inventory.setItemStack(slot, ItemStack.AIR)
        }
    }

    fun refresh(player: Player) {
        if (!activePlayers.contains(player.uuid)) return
        for ((slot, entry) in slots) {
            val visible = entry.visibleWhen?.test(player) != false
            val override = playerOverrides[player.uuid]?.get(slot)
            val item = if (visible) (override ?: entry.dynamicItem?.invoke(player) ?: entry.item) else ItemStack.AIR
            player.inventory.setItemStack(slot, item)
        }
    }

    fun refreshAll() {
        for (uuid in activePlayers) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: continue
            refresh(player)
        }
    }

    fun refreshSlot(player: Player, slot: Int) {
        if (!activePlayers.contains(player.uuid)) return
        val entry = slots[slot] ?: return
        val visible = entry.visibleWhen?.test(player) != false
        val override = playerOverrides[player.uuid]?.get(slot)
        val item = if (visible) (override ?: entry.dynamicItem?.invoke(player) ?: entry.item) else ItemStack.AIR
        player.inventory.setItemStack(slot, item)
    }

    fun overrideSlot(player: Player, slot: Int, item: ItemStack) {
        playerOverrides.getOrPut(player.uuid) { ConcurrentHashMap() }[slot] = item
        if (activePlayers.contains(player.uuid)) player.inventory.setItemStack(slot, item)
    }

    fun clearOverride(player: Player, slot: Int) {
        playerOverrides[player.uuid]?.remove(slot)
        refreshSlot(player, slot)
    }

    fun clearOverrides(player: Player) {
        playerOverrides.remove(player.uuid)
        refresh(player)
    }

    fun isActive(player: Player): Boolean = activePlayers.contains(player.uuid)

    fun install() {
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        if (refreshIntervalTicks > 0) {
            refreshTask = MinecraftServer.getSchedulerManager()
                .buildTask(::refreshAll)
                .repeat(TaskSchedule.tick(refreshIntervalTicks))
                .schedule()
        }
    }

    fun uninstall() {
        refreshTask?.cancel()
        refreshTask = null
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
        activePlayers.clear()
        cooldowns.clear()
        playerOverrides.clear()
    }

    private fun handleClick(player: Player, slot: HotbarSlot, type: ClickType) {
        val handler = slot.onClick ?: return
        if (slot.visibleWhen != null && !slot.visibleWhen.test(player)) return

        if (slot.cooldownTicks > 0) {
            val key = player.uuid.mostSignificantBits xor slot.slot.toLong()
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
    @PublishedApi internal var dynamicItem: ((Player) -> ItemStack)? = null
    @PublishedApi internal var visibleWhen: Condition<Player>? = null
    @PublishedApi internal var cooldownTicks: Int = 4
    @PublishedApi internal var sound: SoundEvent? = null
    @PublishedApi internal var soundPitch: Float = 1f
    @PublishedApi internal var onClick: ((Player, ClickType) -> Unit)? = null

    fun dynamicItem(provider: (Player) -> ItemStack) { dynamicItem = provider }
    fun visibleWhen(condition: Condition<Player>) { visibleWhen = condition }
    fun visibleWhen(predicate: (Player) -> Boolean) { visibleWhen = Condition { predicate(it) } }
    fun cooldown(ticks: Int) { cooldownTicks = ticks }
    fun sound(event: SoundEvent, pitch: Float = 1f) { sound = event; soundPitch = pitch }
    fun onClick(handler: (Player) -> Unit) { onClick = { player, _ -> handler(player) } }
    fun onClick(handler: (Player, ClickType) -> Unit) { onClick = handler }

    @PublishedApi internal fun build(): HotbarSlot =
        HotbarSlot(slot, item, dynamicItem, visibleWhen, cooldownTicks, sound, soundPitch, onClick)
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
        slots[slot] = HotbarSlot(slot, item, null, null, 4, null, 1f) { player, _ -> onClick(player) }
    }

    fun slot(slot: Int, item: ItemStack, visibleWhen: Condition<Player>, onClick: (Player) -> Unit) {
        require(slot in 0..8) { "Hotbar slot must be 0-8" }
        slots[slot] = HotbarSlot(slot, item, null, visibleWhen, 4, null, 1f) { player, _ -> onClick(player) }
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
