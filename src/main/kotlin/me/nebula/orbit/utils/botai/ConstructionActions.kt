package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

class BridgeForward(
    private val direction: Vec,
    private val length: Int,
    private val block: Block = Block.COBBLESTONE,
) : BotAction {
    override var isComplete = false
        private set
    private var stepsDone = 0
    private var subAction: BotAction? = null

    override fun tick(player: Player) {
        val instance = player.instance ?: run { isComplete = true; return }
        val sub = subAction
        if (sub != null && !sub.isComplete) {
            sub.tick(player)
            return
        }
        if (stepsDone >= length) {
            isComplete = true
            return
        }
        val basePos = player.position
        val nextX = basePos.x() + direction.x() * (stepsDone + 1)
        val nextZ = basePos.z() + direction.z() * (stepsDone + 1)
        val floorY = basePos.blockY() - 1
        val floorBlock = instance.getBlock(nextX.toInt(), floorY, nextZ.toInt())
        val sequence = mutableListOf<BotAction>()
        if (floorBlock.isAir || floorBlock.isLiquid) {
            if (!consumeBlock(player)) {
                isComplete = true
                return
            }
            sequence.add(PlaceBlock(
                Pos(nextX.toInt() + 0.5, floorY.toDouble(), nextZ.toInt() + 0.5),
                block,
            ))
        }
        sequence.add(WalkTo(Pos(nextX, basePos.y(), nextZ)))
        val seq = Sequence(sequence)
        seq.start(player)
        subAction = seq
        stepsDone++
    }

    override fun cancel(player: Player) {
        subAction?.cancel(player)
    }

    private fun consumeBlock(player: Player): Boolean {
        val blockMaterial = blockToMaterial(block) ?: return false
        for (i in 0 until player.inventory.size) {
            val stack = player.inventory.getItemStack(i)
            if (stack.material() == blockMaterial) {
                player.inventory.setItemStack(i, stack.withAmount(stack.amount() - 1))
                return true
            }
        }
        return false
    }
}

class TowerUp(private val height: Int, private val block: Block = Block.COBBLESTONE) : BotAction {
    override var isComplete = false
        private set

    private enum class Phase { JUMP, WAIT_PEAK, PLACE }
    private var phase = Phase.JUMP
    private var stepsDone = 0
    private var waitTicks = 0

    override fun tick(player: Player) {
        if (stepsDone >= height) {
            isComplete = true
            return
        }
        when (phase) {
            Phase.JUMP -> {
                BotMovement.jump(player)
                waitTicks = 0
                phase = Phase.WAIT_PEAK
            }
            Phase.WAIT_PEAK -> {
                waitTicks++
                val vy = player.velocity.y()
                if (vy <= 0.5 || waitTicks >= 5) phase = Phase.PLACE
            }
            Phase.PLACE -> {
                val instance = player.instance ?: run { isComplete = true; return }
                val blockMaterial = blockToMaterial(block)
                if (blockMaterial == null) {
                    isComplete = true
                    return
                }
                var found = false
                for (i in 0 until player.inventory.size) {
                    val stack = player.inventory.getItemStack(i)
                    if (stack.material() == blockMaterial) {
                        player.inventory.setItemStack(i, stack.withAmount(stack.amount() - 1))
                        found = true
                        break
                    }
                }
                if (!found) {
                    isComplete = true
                    return
                }
                val placeY = player.position.blockY() - 1
                val blockVec = BlockVec(player.position.blockX(), placeY, player.position.blockZ())
                val event = PlayerBlockPlaceEvent(
                    player, instance, block,
                    BlockFace.TOP, blockVec, blockVec, PlayerHand.MAIN,
                )
                EventDispatcher.call(event)
                if (!event.isCancelled) {
                    instance.setBlock(blockVec.blockX(), blockVec.blockY(), blockVec.blockZ(), event.block)
                }
                stepsDone++
                phase = Phase.JUMP
            }
        }
    }
}

class PlaceWaterBucket(private val pos: Point) : BotAction {
    override var isComplete = false
        private set

    override fun tick(player: Player) {
        val instance = player.instance ?: run { isComplete = true; return }
        val distSq = player.position.distanceSquared(blockCenter(pos))
        if (distSq > BLOCK_REACH_SQ) {
            BotMovement.moveToward(player, blockCenter(pos), false)
            return
        }
        var bucketSlot = -1
        for (i in 0 until player.inventory.size) {
            if (player.inventory.getItemStack(i).material() == Material.WATER_BUCKET) {
                bucketSlot = i
                break
            }
        }
        if (bucketSlot < 0) {
            isComplete = true
            return
        }
        BotMovement.lookAt(player, blockMidpoint(pos))
        instance.setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), Block.WATER)
        player.inventory.setItemStack(bucketSlot, ItemStack.of(Material.BUCKET))
        isComplete = true
    }
}
