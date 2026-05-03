package me.nebula.orbit.utils.customcontent.event

import me.nebula.orbit.utils.customcontent.block.CustomBlock
import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerCancelDiggingEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerStartDiggingEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.network.packet.server.play.BlockBreakAnimationPacket
import net.minestom.server.registry.RegistryKey
import net.minestom.server.network.packet.server.play.BlockChangePacket
import net.minestom.server.potion.PotionEffect
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CustomBlockMiningGate {

    private const val TICK_INTERVAL = 1

    private val EFFICIENCY: RegistryKey<Enchantment> = RegistryKey.unsafeOf("efficiency")

    private data class DigState(
        val startedAt: Long,
        val expectedMs: Long,
        val pos: BlockVec,
        val carrierStateId: Int,
        val customId: String,
        val playerEntityId: Int,
    )

    private val active = ConcurrentHashMap<UUID, DigState>()
    @Volatile private var task: Task? = null

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerStartDiggingEvent::class.java) { event ->
            val player = event.player
            if (player.gameMode == GameMode.CREATIVE) return@addListener
            val customBlock = CustomBlockRegistry.fromVanillaBlock(event.block) ?: return@addListener
            val existing = active[player.uuid]
            if (existing != null && existing.pos == event.blockPosition) return@addListener
            val expectedMs = computeExpectedMs(player, customBlock)
            active[player.uuid] = DigState(
                startedAt = System.currentTimeMillis(),
                expectedMs = expectedMs,
                pos = event.blockPosition,
                carrierStateId = event.block.stateId(),
                customId = customBlock.id,
                playerEntityId = player.entityId,
            )
            player.sendPacket(BlockChangePacket(event.blockPosition, event.block.stateId()))
        }
        eventNode.addListener(PlayerCancelDiggingEvent::class.java) { event ->
            clearAndHideAnimation(event.player.uuid)
        }
        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val dig = active[event.player.uuid] ?: return@addListener
            if (event.blockPosition == dig.pos) {
                event.isCancelled = true
                event.player.sendPacket(BlockChangePacket(dig.pos, dig.carrierStateId))
            }
        }
        eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
            active.remove(event.player.uuid)
        }
        if (task == null) {
            task = repeat(TICK_INTERVAL) { tick() }
        }
    }

    fun uninstall() {
        task?.cancel()
        task = null
        active.clear()
    }

    fun isMining(player: Player): Boolean = active.containsKey(player.uuid)

    private fun tick() {
        if (active.isEmpty()) return
        val now = System.currentTimeMillis()
        val iter = active.entries.iterator()
        while (iter.hasNext()) {
            val (uuid, dig) = iter.next()
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            if (player == null || player.isRemoved) {
                iter.remove()
                continue
            }
            val instance = player.instance
            if (instance == null) {
                iter.remove()
                continue
            }
            val elapsed = now - dig.startedAt

            player.sendPacket(BlockChangePacket(dig.pos, dig.carrierStateId))

            val stage = ((elapsed.toFloat() / dig.expectedMs) * 10f).toInt().coerceIn(0, 9)
            instance.sendGroupedPacket(
                BlockBreakAnimationPacket(dig.playerEntityId, dig.pos, stage.toByte())
            )

            if (elapsed >= dig.expectedMs) {
                iter.remove()
                completeBreak(player, instance, dig)
            }
        }
    }

    private fun completeBreak(player: Player, instance: Instance, dig: DigState) {
        instance.sendGroupedPacket(
            BlockBreakAnimationPacket(dig.playerEntityId, dig.pos, 10)
        )
        val customBlock = CustomBlockRegistry[dig.customId] ?: return
        val currentBlock = instance.getBlock(dig.pos)
        if (currentBlock.stateId() != dig.carrierStateId) return
        CustomBlockBreaker.execute(instance, dig.pos, customBlock)
    }

    private fun clearAndHideAnimation(uuid: UUID) {
        val dig = active.remove(uuid) ?: return
        val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: return
        val instance = player.instance ?: return
        instance.sendGroupedPacket(
            BlockBreakAnimationPacket(dig.playerEntityId, dig.pos, 10)
        )
    }

    private fun computeExpectedMs(player: Player, customBlock: CustomBlock): Long {
        val stack = player.itemInMainHand
        val tool = stack.get(DataComponents.TOOL)
        val miningBlock: Block = customBlock.miningBlock ?: customBlock.allocatedState

        val toolSpeed = tool?.getSpeed(miningBlock) ?: 1f
        val correct = tool?.isCorrectForDrops(miningBlock) ?: false

        var speed = toolSpeed
        if (correct) {
            val efficiency = stack.get(DataComponents.ENCHANTMENTS)?.level(EFFICIENCY) ?: 0
            if (efficiency > 0) speed += efficiency * efficiency + 1f
        }

        val hasteLevel = if (player.hasEffect(PotionEffect.HASTE)) player.getEffectLevel(PotionEffect.HASTE) + 1 else 0
        val conduitLevel = if (player.hasEffect(PotionEffect.CONDUIT_POWER)) player.getEffectLevel(PotionEffect.CONDUIT_POWER) + 1 else 0
        val effectiveHaste = maxOf(hasteLevel, conduitLevel)
        if (effectiveHaste > 0) speed *= 1f + 0.2f * effectiveHaste

        if (player.hasEffect(PotionEffect.MINING_FATIGUE)) {
            val factor = when (player.getEffectLevel(PotionEffect.MINING_FATIGUE)) {
                0 -> 0.3f
                1 -> 0.09f
                2 -> 0.0027f
                else -> 0.00081f
            }
            speed *= factor
        }

        val baseFactor = if (correct) 1.5f else 5.0f
        val seconds = customBlock.hardness * baseFactor / speed
        return (seconds * 1000f).toLong().coerceAtLeast(50L)
    }
}
