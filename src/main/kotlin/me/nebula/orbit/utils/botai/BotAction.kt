package me.nebula.orbit.utils.botai

import me.nebula.orbit.utils.vanilla.modules.FURNACE_NAMES
import me.nebula.orbit.utils.vanilla.packBlockPos
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.metadata.LivingEntityMeta
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

private const val REACH_DISTANCE_SQ = 2.25
private const val BLOCK_REACH_SQ = 16.0
private const val ARM_SWING_INTERVAL = 5

private fun blockCenter(pos: Point): Pos = Pos(pos.blockX() + 0.5, pos.blockY().toDouble(), pos.blockZ() + 0.5)

private fun blockMidpoint(pos: Point): Pos = Pos(pos.blockX() + 0.5, pos.blockY() + 0.5, pos.blockZ() + 0.5)

private fun fireInteractEvent(player: Player, pos: Point, block: Block): Boolean {
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

sealed interface BotAction {
    val isComplete: Boolean
    fun start(player: Player) {}
    fun tick(player: Player)
    fun cancel(player: Player) {}

    class WalkTo(private val target: Point) : BotAction {
        override var isComplete = false
            private set

        override fun tick(player: Player) {
            val distSq = player.position.distanceSquared(target)
            if (distSq <= REACH_DISTANCE_SQ) {
                isComplete = true
                player.velocity = Vec(0.0, player.velocity.y(), 0.0)
                return
            }
            val jitter = BotAI.skillLevels[player.uuid]?.movementJitter ?: 0f
            BotMovement.moveToward(player, target, false, jitter)
        }

        override fun cancel(player: Player) {
            player.velocity = Vec(0.0, player.velocity.y(), 0.0)
        }
    }

    class SprintTo(private val target: Point) : BotAction {
        override var isComplete = false
            private set

        override fun tick(player: Player) {
            val distSq = player.position.distanceSquared(target)
            if (distSq <= REACH_DISTANCE_SQ) {
                isComplete = true
                player.isSprinting = false
                player.velocity = Vec(0.0, player.velocity.y(), 0.0)
                return
            }
            val jitter = BotAI.skillLevels[player.uuid]?.movementJitter ?: 0f
            BotMovement.moveToward(player, target, true, jitter)
        }

        override fun cancel(player: Player) {
            player.isSprinting = false
            player.velocity = Vec(0.0, player.velocity.y(), 0.0)
        }
    }

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

    class AttackEntity(private val target: LivingEntity, private val times: Int = 1) : BotAction {
        override var isComplete = false
            private set
        private var hits = 0
        private var cooldown = 0

        override fun tick(player: Player) {
            if (!target.isActive || target.isRemoved) {
                isComplete = true
                return
            }
            if (cooldown > 0) {
                cooldown--
                return
            }
            val skill = BotAI.skillLevels[player.uuid]
            val jitter = skill?.movementJitter ?: 0f
            val distSq = player.position.distanceSquared(target.position)
            if (distSq > 9.0) {
                BotMovement.moveToward(player, target.position, true, jitter)
                return
            }
            BotMovement.lookAt(player, target.position.add(0.0, target.eyeHeight, 0.0))
            player.swingMainHand()
            val aimAccuracy = skill?.aimAccuracy ?: 1f
            if (Random.nextFloat() < aimAccuracy) {
                target.damage(Damage.fromEntity(player, player.getAttributeValue(Attribute.ATTACK_DAMAGE).toFloat()))
            }
            hits++
            cooldown = 10
            if (hits >= times) isComplete = true
        }

        override fun cancel(player: Player) {
            player.isSprinting = false
        }
    }

    class UseItem : BotAction {
        override var isComplete = false
            private set

        override fun tick(player: Player) {
            player.swingMainHand()
            isComplete = true
        }
    }

    class HoldSlot(private val slot: Int) : BotAction {
        override var isComplete = false
            private set

        override fun tick(player: Player) {
            player.setHeldItemSlot(slot.toByte())
            isComplete = true
        }
    }

    class EquipItem(private val slot: EquipmentSlot, private val item: ItemStack) : BotAction {
        override var isComplete = false
            private set

        override fun tick(player: Player) {
            player.setEquipment(slot, item)
            isComplete = true
        }
    }

    class PickupNearbyItems(private val radius: Double = 4.0) : BotAction {
        override var isComplete = false
            private set
        private var targetPos: Point? = null
        private var scanCooldown = 0

        override fun tick(player: Player) {
            val instance = player.instance ?: run { isComplete = true; return }
            if (scanCooldown > 0) {
                scanCooldown--
                if (targetPos != null) BotMovement.moveToward(player, targetPos!!, false)
                return
            }
            scanCooldown = 10
            var nearest: ItemEntity? = null
            var nearestDistSq = radius * radius
            instance.entityTracker.nearbyEntities(player.position, radius, EntityTracker.Target.ITEMS) { item ->
                if (nearest == null || item.position.distanceSquared(player.position) < nearestDistSq) {
                    nearest = item as? ItemEntity
                    nearestDistSq = item.position.distanceSquared(player.position)
                }
            }
            val item = nearest
            if (item == null) {
                isComplete = true
                return
            }
            targetPos = item.position
            BotMovement.moveToward(player, item.position, false)
        }
    }

    class OpenContainer(private val pos: Point) : BotAction {
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
            val block = instance.getBlock(pos)
            fireInteractEvent(player, pos, block)
            isComplete = true
        }
    }

    class CraftItem(
        private val result: ItemStack,
        private val recipe: List<Pair<Material, Int>>,
        private val craftingTablePos: Point? = null,
    ) : BotAction {
        override var isComplete = false
            private set
        private var walkAction: WalkTo? = null
        private var interacted = false

        override fun tick(player: Player) {
            if (isComplete) return
            val instance = player.instance ?: run { isComplete = true; return }
            val tablePos = craftingTablePos

            if (tablePos != null) {
                if (walkAction == null) {
                    val walk = WalkTo(blockCenter(tablePos))
                    walk.start(player)
                    walkAction = walk
                }
                val walk = walkAction!!
                if (!walk.isComplete) {
                    walk.tick(player)
                    return
                }
                if (!interacted) {
                    val block = instance.getBlock(tablePos)
                    fireInteractEvent(player, tablePos, block)
                    player.swingMainHand()
                    interacted = true
                }
            }

            for ((material, count) in recipe) {
                var remaining = count
                for (i in 0 until player.inventory.size) {
                    val stack = player.inventory.getItemStack(i)
                    if (stack.material() == material) {
                        val take = minOf(remaining, stack.amount())
                        player.inventory.setItemStack(i, stack.withAmount(stack.amount() - take))
                        remaining -= take
                        if (remaining <= 0) break
                    }
                }
                if (remaining > 0) {
                    isComplete = true
                    return
                }
            }
            player.inventory.addItemStack(result)
            isComplete = true
        }
    }

    class EatFood : BotAction {
        override var isComplete = false
            private set
        private var foodSlot = -1

        override fun start(player: Player) {
            for (i in 0 until player.inventory.size) {
                val stack = player.inventory.getItemStack(i)
                if (stack.material() in FOOD_MAP) {
                    foodSlot = i
                    return
                }
            }
            isComplete = true
        }

        override fun tick(player: Player) {
            if (foodSlot < 0) {
                isComplete = true
                return
            }
            val stack = player.inventory.getItemStack(foodSlot)
            if (stack.isAir) {
                isComplete = true
                return
            }
            player.setHeldItemSlot(foodSlot.coerceIn(0, 8).toByte())
            val event = PlayerUseItemEvent(player, PlayerHand.MAIN, stack, 0L)
            EventDispatcher.call(event)
            isComplete = true
        }
    }

    class SmeltItems(
        private val furnacePos: Point,
        private val input: Material,
        private val fuel: Material = Material.COAL,
    ) : BotAction {
        override var isComplete = false
            private set

        private enum class Phase { WALKING, OPENING, PLACING, WAITING, COLLECTING, DONE }
        private var phase = Phase.WALKING
        private var subAction: BotAction? = null
        private var furnaceInv: Inventory? = null
        private var waitTicks = 0
        private var itemsToSmelt = 0

        override fun tick(player: Player) {
            if (isComplete) return
            when (phase) {
                Phase.WALKING -> {
                    val sub = subAction
                    if (sub == null) {
                        val walk = WalkTo(blockCenter(furnacePos))
                        walk.start(player)
                        subAction = walk
                        return
                    }
                    sub.tick(player)
                    if (sub.isComplete) phase = Phase.OPENING
                }
                Phase.OPENING -> {
                    val instance = player.instance
                    val block = instance?.getBlock(furnacePos)
                    if (block == null || !block.name().let { it in FURNACE_NAMES }) {
                        isComplete = true
                        return
                    }
                    fireInteractEvent(player, furnacePos, block)
                    val openInv = player.openInventory
                    if (openInv is Inventory && openInv.inventoryType == InventoryType.FURNACE) {
                        furnaceInv = openInv
                        phase = Phase.PLACING
                    } else {
                        isComplete = true
                    }
                }
                Phase.PLACING -> {
                    val inv = furnaceInv ?: run { isComplete = true; return }
                    var inputCount = 0
                    for (i in 0 until player.inventory.size) {
                        val stack = player.inventory.getItemStack(i)
                        if (stack.material() == input) inputCount += stack.amount()
                    }
                    var fuelCount = 0
                    for (i in 0 until player.inventory.size) {
                        val stack = player.inventory.getItemStack(i)
                        if (stack.material() == fuel) fuelCount += stack.amount()
                    }
                    val canSmelt = minOf(inputCount, fuelCount * 8)
                    if (canSmelt <= 0) {
                        player.closeInventory()
                        isComplete = true
                        return
                    }
                    itemsToSmelt = canSmelt
                    val fuelNeeded = (canSmelt + 7) / 8
                    consumeMaterial(player, input, canSmelt)
                    consumeMaterial(player, fuel, fuelNeeded)
                    inv.setItemStack(0, ItemStack.of(input, canSmelt))
                    inv.setItemStack(1, ItemStack.of(fuel, fuelNeeded))
                    waitTicks = 0
                    phase = Phase.WAITING
                }
                Phase.WAITING -> {
                    waitTicks++
                    if (waitTicks % 20 != 0 && waitTicks <= itemsToSmelt * 220) return
                    val inv = furnaceInv ?: run { isComplete = true; return }
                    val output = inv.getItemStack(2)
                    if (!output.isAir && output.amount() >= itemsToSmelt) {
                        phase = Phase.COLLECTING
                        return
                    }
                    if (waitTicks > itemsToSmelt * 220) {
                        phase = Phase.COLLECTING
                    }
                }
                Phase.COLLECTING -> {
                    val inv = furnaceInv ?: run { isComplete = true; return }
                    val output = inv.getItemStack(2)
                    if (!output.isAir) {
                        player.inventory.addItemStack(output)
                        inv.setItemStack(2, ItemStack.AIR)
                    }
                    val leftover0 = inv.getItemStack(0)
                    if (!leftover0.isAir) {
                        player.inventory.addItemStack(leftover0)
                        inv.setItemStack(0, ItemStack.AIR)
                    }
                    val leftover1 = inv.getItemStack(1)
                    if (!leftover1.isAir) {
                        player.inventory.addItemStack(leftover1)
                        inv.setItemStack(1, ItemStack.AIR)
                    }
                    player.closeInventory()
                    phase = Phase.DONE
                    isComplete = true
                }
                Phase.DONE -> isComplete = true
            }
        }

        private fun consumeMaterial(player: Player, material: Material, amount: Int) {
            var remaining = amount
            for (i in 0 until player.inventory.size) {
                if (remaining <= 0) break
                val stack = player.inventory.getItemStack(i)
                if (stack.material() == material) {
                    val take = minOf(remaining, stack.amount())
                    player.inventory.setItemStack(i, stack.withAmount(stack.amount() - take))
                    remaining -= take
                }
            }
        }
    }

    class Wait(private val ticks: Int) : BotAction {
        override var isComplete = false
            private set
        private var elapsed = 0

        override fun tick(player: Player) {
            elapsed++
            if (elapsed >= ticks) isComplete = true
        }
    }

    class LookAt(private val target: Point) : BotAction {
        override var isComplete = false
            private set

        override fun tick(player: Player) {
            BotMovement.lookAt(player, target)
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

    class CriticalHit(private val target: LivingEntity) : BotAction {
        override var isComplete = false
            private set

        private enum class Phase { APPROACH, JUMP, WAIT_PEAK, STRIKE }
        private var phase = Phase.APPROACH
        private var waitTicks = 0

        override fun tick(player: Player) {
            if (!target.isActive || target.isRemoved) {
                isComplete = true
                return
            }
            when (phase) {
                Phase.APPROACH -> {
                    val skill = BotAI.skillLevels[player.uuid]
                    val jitter = skill?.movementJitter ?: 0f
                    val distSq = player.position.distanceSquared(target.position)
                    if (distSq <= 9.0) {
                        phase = Phase.JUMP
                        return
                    }
                    BotMovement.moveToward(player, target.position, true, jitter)
                }
                Phase.JUMP -> {
                    BotMovement.lookAt(player, target.position.add(0.0, target.eyeHeight, 0.0))
                    BotMovement.jump(player)
                    waitTicks = 0
                    phase = Phase.WAIT_PEAK
                }
                Phase.WAIT_PEAK -> {
                    BotMovement.lookAt(player, target.position.add(0.0, target.eyeHeight, 0.0))
                    waitTicks++
                    val vy = player.velocity.y()
                    val atPeak = (vy <= 0.5 && !player.isOnGround) || waitTicks >= 5
                    if (atPeak) phase = Phase.STRIKE
                }
                Phase.STRIKE -> {
                    BotMovement.lookAt(player, target.position.add(0.0, target.eyeHeight, 0.0))
                    player.swingMainHand()
                    val baseDamage = player.getAttributeValue(Attribute.ATTACK_DAMAGE).toFloat()
                    val skill = BotAI.skillLevels[player.uuid]
                    val critChance = skill?.criticalHitChance ?: 0.5f
                    val multiplier = if (Random.nextFloat() < critChance) 1.5f else 1.0f
                    target.damage(Damage.fromEntity(player, baseDamage * multiplier))
                    isComplete = true
                }
            }
        }

        override fun cancel(player: Player) {
            player.isSprinting = false
        }
    }

    class ShieldBlock(private val durationTicks: Int = 60) : BotAction {
        override var isComplete = false
            private set
        private var elapsed = 0
        private var activated = false
        private var reactionDelay = 0

        override fun start(player: Player) {
            val offhand = player.inventory.getItemStack(player.inventory.size - 1)
            if (offhand.material() != Material.SHIELD) {
                isComplete = true
                return
            }
            val skill = BotAI.skillLevels[player.uuid]
            reactionDelay = skill?.reactionTimeTicks ?: 0
        }

        override fun tick(player: Player) {
            if (reactionDelay > 0) {
                reactionDelay--
                return
            }
            if (!activated) {
                val offhand = player.inventory.getItemStack(player.inventory.size - 1)
                if (offhand.material() != Material.SHIELD) {
                    isComplete = true
                    return
                }
                player.editEntityMeta(LivingEntityMeta::class.java) {
                    it.setHandActive(true)
                    it.setActiveHand(PlayerHand.OFF)
                }
                activated = true
            }
            elapsed++
            if (elapsed >= durationTicks) {
                player.editEntityMeta(LivingEntityMeta::class.java) {
                    it.setHandActive(false)
                }
                isComplete = true
            }
        }

        override fun cancel(player: Player) {
            if (activated) {
                player.editEntityMeta(LivingEntityMeta::class.java) {
                    it.setHandActive(false)
                }
            }
        }
    }

    class ShootBow(private val target: LivingEntity, private val chargeTicks: Int = 20) : BotAction {
        override var isComplete = false
            private set

        private enum class Phase { EQUIP, DRAW, RELEASE }
        private var phase = Phase.EQUIP
        private var elapsed = 0
        private var aimOffset = Vec.ZERO

        override fun start(player: Player) {
            val skill = BotAI.skillLevels[player.uuid]
            val inaccuracy = (1f - (skill?.aimAccuracy ?: 0.7f)) * 4.0
            val rng = ThreadLocalRandom.current()
            aimOffset = Vec(
                rng.nextGaussian() * inaccuracy,
                rng.nextGaussian() * inaccuracy * 0.5,
                rng.nextGaussian() * inaccuracy,
            )
        }

        override fun tick(player: Player) {
            if (!target.isActive || target.isRemoved) {
                isComplete = true
                return
            }
            val aimTarget = target.position.add(aimOffset.x(), target.eyeHeight + aimOffset.y(), aimOffset.z())
            when (phase) {
                Phase.EQUIP -> {
                    val bowSlot = findBowSlot(player)
                    if (bowSlot < 0) {
                        isComplete = true
                        return
                    }
                    player.setHeldItemSlot(bowSlot.coerceIn(0, 8).toByte())
                    BotMovement.lookAt(player, aimTarget)
                    val event = PlayerUseItemEvent(player, PlayerHand.MAIN, player.itemInMainHand, 0L)
                    EventDispatcher.call(event)
                    player.editEntityMeta(LivingEntityMeta::class.java) {
                        it.setHandActive(true)
                        it.setActiveHand(PlayerHand.MAIN)
                    }
                    elapsed = 0
                    phase = Phase.DRAW
                }
                Phase.DRAW -> {
                    BotMovement.lookAt(player, aimTarget)
                    elapsed++
                    if (elapsed >= chargeTicks) phase = Phase.RELEASE
                }
                Phase.RELEASE -> {
                    BotMovement.lookAt(player, aimTarget)
                    player.editEntityMeta(LivingEntityMeta::class.java) {
                        it.setHandActive(false)
                    }
                    player.setItemInMainHand(ItemStack.AIR)
                    val bowSlot = findBowSlot(player)
                    if (bowSlot >= 0) player.setHeldItemSlot(bowSlot.coerceIn(0, 8).toByte())
                    isComplete = true
                }
            }
        }

        override fun cancel(player: Player) {
            player.editEntityMeta(LivingEntityMeta::class.java) {
                it.setHandActive(false)
            }
        }

        private fun findBowSlot(player: Player): Int {
            for (i in 0..8) {
                if (player.inventory.getItemStack(i).material() == Material.BOW) return i
            }
            return -1
        }
    }

    class ThrowProjectile(private val type: Material, private val target: Point) : BotAction {
        override var isComplete = false
            private set

        override fun tick(player: Player) {
            val slot = findSlot(player, type)
            if (slot < 0) {
                isComplete = true
                return
            }
            player.setHeldItemSlot(slot.coerceIn(0, 8).toByte())
            BotMovement.lookAt(player, target)
            val event = PlayerUseItemEvent(player, PlayerHand.MAIN, player.itemInMainHand, 0L)
            EventDispatcher.call(event)
            isComplete = true
        }

        private fun findSlot(player: Player, material: Material): Int {
            for (i in 0..8) {
                if (player.inventory.getItemStack(i).material() == material) return i
            }
            return -1
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

    class DrinkPotion : BotAction {
        override var isComplete = false
            private set

        override fun tick(player: Player) {
            val potionSlot = findPotionSlot(player)
            if (potionSlot < 0) {
                isComplete = true
                return
            }
            player.setHeldItemSlot(potionSlot.coerceIn(0, 8).toByte())
            val event = PlayerUseItemEvent(player, PlayerHand.MAIN, player.itemInMainHand, 0L)
            EventDispatcher.call(event)
            isComplete = true
        }

        private fun findPotionSlot(player: Player): Int {
            for (i in 0 until player.inventory.size) {
                val mat = player.inventory.getItemStack(i).material()
                if (mat == Material.POTION || mat == Material.SPLASH_POTION) return i
            }
            return -1
        }
    }

    class Sequence(private val actions: List<BotAction>) : BotAction {
        override val isComplete: Boolean get() = currentIndex >= actions.size
        private var currentIndex = 0

        override fun start(player: Player) {
            if (actions.isNotEmpty()) actions[0].start(player)
        }

        override fun tick(player: Player) {
            if (currentIndex >= actions.size) return
            val current = actions[currentIndex]
            current.tick(player)
            if (current.isComplete) {
                currentIndex++
                if (currentIndex < actions.size) actions[currentIndex].start(player)
            }
        }

        override fun cancel(player: Player) {
            if (currentIndex < actions.size) actions[currentIndex].cancel(player)
        }
    }
}

private fun blockToMaterial(block: Block): Material? = when {
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

private val FOOD_MAP = setOf(
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

