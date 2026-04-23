package me.nebula.orbit.mode.game.battleroyale.team

import me.nebula.gravity.translation.Keys
import me.nebula.orbit.displayUsername
import me.nebula.orbit.mode.game.GameMode
import me.nebula.orbit.mode.game.PlayerTracker
import me.nebula.orbit.mode.game.battleroyale.BattleRoyaleTeamConfig
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag

class RespawnBeacon(
    private val tracker: PlayerTracker,
    private val config: BattleRoyaleTeamConfig,
    private val resolveRespawnPos: (reviver: Player) -> net.minestom.server.coordinate.Pos,
    private val respawnCallback: (revived: Player, reviver: Player) -> Unit,
    private val broadcast: (Player.() -> net.kyori.adventure.text.Component) -> Unit,
) {

    private var eventNode: EventNode<*>? = null

    fun install() {
        if (!config.enabled || !config.respawnBeaconEnabled) return
        val node = EventNode.all("br-respawn-beacon")
        node.addListener(PlayerUseItemEvent::class.java) { event ->
            val stack = event.itemStack
            if (!isBeacon(stack)) return@addListener
            val player = event.player
            if (!attemptRespawn(player)) return@addListener
            consumeOne(player, stack)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun uninstall() {
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
    }

    fun decorate(stack: ItemStack): ItemStack {
        if (stack.material() != BEACON_MATERIAL) return stack
        if (isBeacon(stack)) return stack
        return createBeacon().withAmount(stack.amount().coerceAtLeast(1))
    }

    fun createBeacon(amount: Int = 1): ItemStack = itemStack(BEACON_MATERIAL) {
        amount(amount)
        name("<light_purple><bold>Respawn Beacon")
        lore("<gray>Activate to revive a fallen teammate.")
        glowing()
        clean()
    }.withTag(BEACON_TAG, true)

    private fun attemptRespawn(reviver: Player): Boolean {
        if (!config.enabled || !config.respawnBeaconEnabled) return false
        val team = tracker.teamOf(reviver.uuid) ?: return false
        val target = findRespawnTarget(team, reviver) ?: run {
            reviver.sendMessage(reviver.translate(Keys.Orbit.Game.Br.Beacon.NoTargets))
            return false
        }
        val respawnPos = resolveRespawnPos(reviver)
        target.teleport(respawnPos)
        respawnCallback(target, reviver)
        broadcast {
            translate(Keys.Orbit.Game.Br.Beacon.Respawned, "player" to target.displayUsername)
        }
        reviver.playSound(net.kyori.adventure.sound.Sound.sound(
            SoundEvent.BLOCK_BEACON_ACTIVATE.key(),
            net.kyori.adventure.sound.Sound.Source.PLAYER, 1f, 1f,
        ))
        return true
    }

    private fun findRespawnTarget(team: String, reviver: Player): Player? {
        val connection = MinecraftServer.getConnectionManager()
        val candidates = tracker.teamMembers(team)
            .filter { it != reviver.uuid }
            .filter { !tracker.isAlive(it) }
            .mapNotNull { connection.getOnlinePlayerByUuid(it) }
        return candidates.firstOrNull()
    }

    private fun consumeOne(player: Player, stack: ItemStack) {
        val heldSlot = player.heldSlot.toInt()
        val held = player.inventory.getItemStack(heldSlot)
        if (!isBeacon(held)) return
        val next = if (held.amount() > 1) held.withAmount(held.amount() - 1) else ItemStack.AIR
        player.inventory.setItemStack(heldSlot, next)
    }

    fun isBeacon(stack: ItemStack): Boolean = stack.getTag(BEACON_TAG) == true

    companion object {
        val BEACON_MATERIAL: Material = Material.LODESTONE
        val BEACON_TAG: Tag<Boolean> = Tag.Boolean("br_respawn_beacon")
    }
}
