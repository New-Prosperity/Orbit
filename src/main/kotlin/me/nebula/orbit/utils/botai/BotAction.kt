package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.item.Material

internal const val REACH_DISTANCE_SQ = 2.25
internal const val BLOCK_REACH_SQ = 16.0
internal const val ARM_SWING_INTERVAL = 5

internal fun blockCenter(pos: Point): Pos = Pos(pos.blockX() + 0.5, pos.blockY().toDouble(), pos.blockZ() + 0.5)

internal fun blockMidpoint(pos: Point): Pos = Pos(pos.blockX() + 0.5, pos.blockY() + 0.5, pos.blockZ() + 0.5)

internal fun fireInteractEvent(player: Player, pos: Point, block: Block): Boolean {
    val instance = player.instance ?: return false
    val blockVec = BlockVec(pos.blockX(), pos.blockY(), pos.blockZ())
    val event = PlayerBlockInteractEvent(
        player, PlayerHand.MAIN, instance, block,
        blockVec, blockVec, BlockFace.TOP,
    )
    EventDispatcher.call(event)
    if (!event.isCancelled) {
        val handler = block.handler()
        if (handler != null) {
            handler.onInteract(
                BlockHandler.Interaction(
                    block, instance, BlockFace.TOP, blockVec, blockVec, player, PlayerHand.MAIN,
                )
            )
        }
    }
    return !event.isCancelled
}

internal fun blockToMaterial(block: Block): Material? = when {
    block.compare(Block.COBBLESTONE) -> Material.COBBLESTONE
    block.compare(Block.DIRT) -> Material.DIRT
    block.compare(Block.OAK_PLANKS) -> Material.OAK_PLANKS
    block.compare(Block.BIRCH_PLANKS) -> Material.BIRCH_PLANKS
    block.compare(Block.SPRUCE_PLANKS) -> Material.SPRUCE_PLANKS
    block.compare(Block.STONE) -> Material.STONE
    block.compare(Block.SAND) -> Material.SAND
    block.compare(Block.SANDSTONE) -> Material.SANDSTONE
    block.compare(Block.NETHERRACK) -> Material.NETHERRACK
    block.compare(Block.END_STONE) -> Material.END_STONE
    block.compare(Block.DEEPSLATE) -> Material.DEEPSLATE
    else -> null
}

internal val FOOD_MAP = setOf(
    Material.APPLE, Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE,
    Material.BREAD, Material.COOKED_BEEF, Material.COOKED_PORKCHOP,
    Material.COOKED_CHICKEN, Material.COOKED_MUTTON, Material.COOKED_SALMON,
    Material.COOKED_COD, Material.COOKED_RABBIT, Material.BAKED_POTATO,
    Material.GOLDEN_CARROT, Material.MUSHROOM_STEW, Material.BEETROOT_SOUP,
    Material.RABBIT_STEW, Material.SUSPICIOUS_STEW, Material.COOKIE,
    Material.MELON_SLICE, Material.DRIED_KELP, Material.SWEET_BERRIES,
    Material.CARROT, Material.POTATO, Material.BEETROOT, Material.BEEF,
    Material.PORKCHOP, Material.CHICKEN, Material.RABBIT, Material.COD,
    Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH,
    Material.ROTTEN_FLESH, Material.SPIDER_EYE, Material.POISONOUS_POTATO,
)

interface BotAction {
    val isComplete: Boolean
    fun start(player: Player) {}
    fun tick(player: Player)
    fun cancel(player: Player) {}
}
