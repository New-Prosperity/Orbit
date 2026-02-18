package me.nebula.orbit.utils.lobby

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerSwapItemEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack

class Lobby(
    val instance: Instance,
    val spawnPoint: Pos,
    val gameMode: GameMode = GameMode.ADVENTURE,
    val protectBlocks: Boolean = true,
    val disableDamage: Boolean = true,
    val disableHunger: Boolean = true,
    val lockInventory: Boolean = true,
    val voidTeleportY: Double = -64.0,
    val hotbarItems: Map<Int, HotbarEntry> = emptyMap(),
) {
    val eventNode = EventNode.all("lobby")

    fun install() {
        if (protectBlocks) {
            eventNode.addListener(PlayerBlockBreakEvent::class.java) { it.isCancelled = true }
            eventNode.addListener(PlayerBlockPlaceEvent::class.java) { it.isCancelled = true }
        }

        if (disableDamage) {
            eventNode.addListener(EntityDamageEvent::class.java) { event ->
                if (event.entity is Player) event.isCancelled = true
            }
        }

        if (lockInventory) {
            eventNode.addListener(InventoryPreClickEvent::class.java) { it.isCancelled = true }
            eventNode.addListener(ItemDropEvent::class.java) { it.isCancelled = true }
            eventNode.addListener(PlayerSwapItemEvent::class.java) { it.isCancelled = true }
        }

        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            val player = event.player
            player.gameMode = gameMode
            player.teleport(spawnPoint)
            player.health = 20f
            player.food = 20
            if (disableHunger) player.food = 20

            hotbarItems.forEach { (slot, entry) ->
                player.inventory.setItemStack(slot, entry.item)
            }
        }

        if (voidTeleportY > Double.NEGATIVE_INFINITY) {
            eventNode.addListener(PlayerMoveEvent::class.java) { event ->
                if (event.newPosition.y() < voidTeleportY) {
                    event.player.teleport(spawnPoint)
                }
            }
        }

        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
    }

    fun uninstall() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
    }

    fun teleportPlayer(player: Player) {
        player.setInstance(instance, spawnPoint)
        player.gameMode = gameMode
        hotbarItems.forEach { (slot, entry) ->
            player.inventory.setItemStack(slot, entry.item)
        }
    }
}

data class HotbarEntry(
    val item: ItemStack,
    val onClick: ((Player) -> Unit)? = null,
)

class LobbyBuilder {
    lateinit var instance: Instance
    var spawnPoint: Pos = Pos(0.0, 64.0, 0.0)
    var gameMode: GameMode = GameMode.ADVENTURE
    var protectBlocks: Boolean = true
    var disableDamage: Boolean = true
    var disableHunger: Boolean = true
    var lockInventory: Boolean = true
    var voidTeleportY: Double = -64.0
    private val hotbarItems = mutableMapOf<Int, HotbarEntry>()

    fun hotbarItem(slot: Int, item: ItemStack, onClick: ((Player) -> Unit)? = null) {
        hotbarItems[slot] = HotbarEntry(item, onClick)
    }

    fun build(): Lobby = Lobby(
        instance = instance,
        spawnPoint = spawnPoint,
        gameMode = gameMode,
        protectBlocks = protectBlocks,
        disableDamage = disableDamage,
        disableHunger = disableHunger,
        lockInventory = lockInventory,
        voidTeleportY = voidTeleportY,
        hotbarItems = hotbarItems.toMap(),
    )
}

inline fun lobby(block: LobbyBuilder.() -> Unit): Lobby =
    LobbyBuilder().apply(block).build()
