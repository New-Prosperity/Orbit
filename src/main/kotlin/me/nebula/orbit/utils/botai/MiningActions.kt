package me.nebula.orbit.utils.botai

import me.nebula.orbit.utils.vanilla.packBlockPos
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.item.Material

class BreakBlock(private val pos: Point) : BotAction {
    override var isComplete = false
        private set
    private var breakTicks = 0
    private var ticksElapsed = 0

    override fun start(player: Player) {
        val instance = player.instance ?: run { isComplete = true; return }
        val block = instance.getBlock(pos)
        breakTicks = MiningKnowledge.breakTime(block, null)
    }

    override fun tick(player: Player) {
        val instance = player.instance ?: run { isComplete = true; return }
        val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
        if (block.isAir) {
            isComplete = true
            return
        }
        val distSq = player.position.distanceSquared(blockCenter(pos))
        if (distSq > BLOCK_REACH_SQ) {
            BotMovement.moveToward(player, blockCenter(pos), false)
            return
        }
        BotMovement.lookAt(player, blockMidpoint(pos))
        if (ticksElapsed % ARM_SWING_INTERVAL == 0) {
            player.swingMainHand()
        }
        ticksElapsed++
        if (ticksElapsed >= breakTicks) {
            instance.breakBlock(player, pos, BlockFace.TOP)
            isComplete = true
        }
    }
}

class PlaceBlock(private val pos: Point, private val block: Block) : BotAction {
    override var isComplete = false
        private set

    override fun tick(player: Player) {
        val instance = player.instance ?: run { isComplete = true; return }
        val distSq = player.position.distanceSquared(blockCenter(pos))
        if (distSq > BLOCK_REACH_SQ) {
            BotMovement.moveToward(player, blockCenter(pos), false)
            return
        }
        BotMovement.lookAt(player, blockMidpoint(pos))
        val blockVec = BlockVec(pos.blockX(), pos.blockY(), pos.blockZ())
        val event = PlayerBlockPlaceEvent(
            player, instance, block,
            BlockFace.TOP, blockVec, blockVec, PlayerHand.MAIN,
        )
        EventDispatcher.call(event)
        if (!event.isCancelled) {
            instance.setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), event.block)
        }
        isComplete = true
    }
}

class MineTunnel(private val direction: Vec, private val length: Int) : BotAction {
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
        val ahead = Vec(
            basePos.x() + direction.x() * (stepsDone + 1),
            basePos.y(),
            basePos.z() + direction.z() * (stepsDone + 1),
        )
        val headBlock = instance.getBlock(ahead.blockX(), ahead.blockY() + 1, ahead.blockZ())
        val feetBlock = instance.getBlock(ahead.blockX(), ahead.blockY(), ahead.blockZ())
        if (headBlock.compare(Block.BEDROCK) || feetBlock.compare(Block.BEDROCK)) {
            isComplete = true
            return
        }
        if (headBlock.compare(Block.LAVA) || feetBlock.compare(Block.LAVA)) {
            isComplete = true
            return
        }
        if (headBlock.isAir && feetBlock.isAir) {
            isComplete = true
            return
        }
        val sequence = mutableListOf<BotAction>()
        if (!headBlock.isAir) sequence.add(BreakBlock(Vec(ahead.x(), ahead.y() + 1, ahead.z())))
        if (!feetBlock.isAir) sequence.add(BreakBlock(ahead))
        if (stepsDone > 0 && stepsDone % 8 == 0) {
            sequence.add(PlaceTorch(Vec(ahead.x(), ahead.y(), ahead.z())))
        }
        sequence.add(WalkTo(Pos(ahead.x(), ahead.y(), ahead.z())))
        sequence.add(PickupNearbyItems(4.0))
        val seq = Sequence(sequence)
        seq.start(player)
        subAction = seq
        stepsDone++
    }

    override fun cancel(player: Player) {
        subAction?.cancel(player)
    }
}

class MineStaircase(private val direction: Vec, private val depth: Int) : BotAction {
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
        if (stepsDone >= depth) {
            isComplete = true
            return
        }
        val basePos = player.position
        val targetX = basePos.x() + direction.x() * (stepsDone + 1)
        val targetY = basePos.y() - (stepsDone + 1)
        val targetZ = basePos.z() + direction.z() * (stepsDone + 1)
        val feetPos = Vec(targetX, targetY, targetZ)
        val headPos = Vec(targetX, targetY + 1, targetZ)
        val aboveHead = Vec(targetX, targetY + 2, targetZ)
        val feetBlock = instance.getBlock(feetPos.blockX(), feetPos.blockY(), feetPos.blockZ())
        val headBlock = instance.getBlock(headPos.blockX(), headPos.blockY(), headPos.blockZ())
        val aboveBlock = instance.getBlock(aboveHead.blockX(), aboveHead.blockY(), aboveHead.blockZ())
        if (feetBlock.compare(Block.BEDROCK) || headBlock.compare(Block.BEDROCK)) {
            isComplete = true
            return
        }
        if (feetBlock.compare(Block.LAVA) || headBlock.compare(Block.LAVA)) {
            isComplete = true
            return
        }
        val sequence = mutableListOf<BotAction>()
        if (!aboveBlock.isAir) sequence.add(BreakBlock(aboveHead))
        if (!headBlock.isAir) sequence.add(BreakBlock(headPos))
        if (!feetBlock.isAir) sequence.add(BreakBlock(feetPos))
        sequence.add(WalkTo(Pos(feetPos.x(), feetPos.y(), feetPos.z())))
        sequence.add(PickupNearbyItems(4.0))
        val seq = Sequence(sequence)
        seq.start(player)
        subAction = seq
        stepsDone++
    }

    override fun cancel(player: Player) {
        subAction?.cancel(player)
    }
}

class MineVein(private val startPos: Point, private val targetBlock: Block) : BotAction {
    override var isComplete = false
        private set
    private var positions: List<Point> = emptyList()
    private var currentIdx = 0
    private var subAction: BotAction? = null
    private var initialized = false

    override fun tick(player: Player) {
        val instance = player.instance ?: run { isComplete = true; return }
        if (!initialized) {
            positions = floodFillOre(instance, startPos, targetBlock, 32)
            initialized = true
            if (positions.isEmpty()) {
                isComplete = true
                return
            }
        }
        val sub = subAction
        if (sub != null && !sub.isComplete) {
            sub.tick(player)
            return
        }
        if (currentIdx >= positions.size) {
            isComplete = true
            return
        }
        val pos = positions[currentIdx]
        val seq = Sequence(listOf(BreakBlock(pos), PickupNearbyItems(5.0)))
        seq.start(player)
        subAction = seq
        currentIdx++
    }

    override fun cancel(player: Player) {
        subAction?.cancel(player)
    }

    private fun floodFillOre(
        instance: Instance,
        start: Point,
        target: Block,
        maxBlocks: Int,
    ): List<Point> {
        val result = mutableListOf<Point>()
        val visited = mutableSetOf(packBlockPos(start.blockX(), start.blockY(), start.blockZ()))
        val queue = ArrayDeque<Point>()
        queue.add(start)
        while (queue.isNotEmpty() && result.size < maxBlocks) {
            val current = queue.removeFirst()
            if (instance.getBlock(current.blockX(), current.blockY(), current.blockZ()).compare(target)) {
                result.add(current)
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        for (dz in -1..1) {
                            if (dx == 0 && dy == 0 && dz == 0) continue
                            val nx = current.blockX() + dx
                            val ny = current.blockY() + dy
                            val nz = current.blockZ() + dz
                            val key = packBlockPos(nx, ny, nz)
                            if (visited.add(key)) {
                                queue.add(Vec(nx + 0.5, ny.toDouble(), nz + 0.5))
                            }
                        }
                    }
                }
            }
        }
        return result
    }
}

class PlaceTorch(private val pos: Point) : BotAction {
    override var isComplete = false
        private set

    override fun tick(player: Player) {
        val instance = player.instance ?: run { isComplete = true; return }
        var hasTorch = false
        for (i in 0 until player.inventory.size) {
            val stack = player.inventory.getItemStack(i)
            if (stack.material() == Material.TORCH) {
                player.inventory.setItemStack(i, stack.withAmount(stack.amount() - 1))
                hasTorch = true
                break
            }
        }
        if (hasTorch) {
            val blockVec = BlockVec(pos.blockX(), pos.blockY(), pos.blockZ())
            val event = PlayerBlockPlaceEvent(
                player, instance, Block.TORCH,
                BlockFace.TOP, blockVec, blockVec, PlayerHand.MAIN,
            )
            EventDispatcher.call(event)
            if (!event.isCancelled) {
                instance.setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), Block.TORCH)
            }
        }
        isComplete = true
    }
}

class DigDown(private val depth: Int) : BotAction {
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
        if (stepsDone >= depth) {
            isComplete = true
            return
        }
        val basePos = player.position
        val targetY = basePos.blockY() - 1
        val directions = listOf(Vec(1.0, 0.0, 0.0), Vec(-1.0, 0.0, 0.0), Vec(0.0, 0.0, 1.0), Vec(0.0, 0.0, -1.0))
        val dir = directions[stepsDone % directions.size]
        val sideX = basePos.blockX() + dir.blockX()
        val sideZ = basePos.blockZ() + dir.blockZ()
        val belowBlock = instance.getBlock(basePos.blockX(), targetY, basePos.blockZ())
        val sideBlock = instance.getBlock(sideX, targetY, sideZ)
        if (belowBlock.compare(Block.BEDROCK) || belowBlock.compare(Block.LAVA)) {
            isComplete = true
            return
        }
        if (sideBlock.compare(Block.LAVA)) {
            isComplete = true
            return
        }
        val sequence = mutableListOf<BotAction>()
        if (!sideBlock.isAir) {
            sequence.add(BreakBlock(Vec(sideX + 0.5, targetY.toDouble(), sideZ + 0.5)))
        }
        sequence.add(WalkTo(Pos(sideX + 0.5, targetY.toDouble(), sideZ + 0.5)))
        if (!belowBlock.isAir) {
            sequence.add(BreakBlock(Vec(basePos.blockX() + 0.5, targetY.toDouble(), basePos.blockZ() + 0.5)))
        }
        sequence.add(WalkTo(Pos(basePos.blockX() + 0.5, targetY.toDouble(), basePos.blockZ() + 0.5)))
        sequence.add(PickupNearbyItems(4.0))
        val seq = Sequence(sequence)
        seq.start(player)
        subAction = seq
        stepsDone++
    }

    override fun cancel(player: Player) {
        subAction?.cancel(player)
    }
}
