package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import kotlin.random.Random

class MineOreGoal(private val ore: Block) : BotGoal() {

    private val oreKey = ore.name()

    override fun calculateUtility(brain: BotBrain): Float {
        val visible = brain.vision.canSee(ore)
        if (visible != null) return 0.55f
        val remembered = brain.memory.nearestResource(oreKey, brain.player.position)
        return if (remembered != null) 0.4f else 0f
    }

    override fun shouldActivate(brain: BotBrain): Boolean =
        brain.vision.canSee(ore) != null ||
            brain.memory.nearestResource(oreKey, brain.player.position) != null

    override fun createActions(brain: BotBrain): List<BotAction> {
        val visible = brain.vision.canSee(ore)
        if (visible != null) {
            brain.memory.rememberResource(oreKey, visible.pos)
            return listOf(
                BreakBlock(visible.pos),
                PickupNearbyItems(5.0),
            )
        }
        val remembered = brain.memory.nearestResource(oreKey, brain.player.position)
        if (remembered != null) {
            val instance = brain.player.instance
            if (instance != null && instance.getBlock(remembered.blockX(), remembered.blockY(), remembered.blockZ()).compare(ore)) {
                return listOf(
                    BreakBlock(remembered),
                    PickupNearbyItems(5.0),
                )
            }
            brain.memory.forgetResourceAt(oreKey, remembered)
        }
        return listOf(Wait(10))
    }

    override fun shouldCancel(brain: BotBrain): Boolean {
        val dropMaterial = MiningKnowledge.blockDrops(ore).firstOrNull()?.material() ?: return false
        return brain.countItem(dropMaterial) >= 16
    }
}

class GatherWoodGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val hasWood = LOG_MATERIALS.any { brain.hasItem(it) } || PLANK_MATERIALS.any { brain.hasItem(it) }
        return if (hasWood) 0f else 0.4f
    }

    override fun shouldActivate(brain: BotBrain): Boolean =
        LOG_MATERIALS.none { brain.hasItem(it) } && PLANK_MATERIALS.none { brain.hasItem(it) }

    override fun createActions(brain: BotBrain): List<BotAction> {
        val visible = brain.vision.canSeeAny(*LOG_BLOCKS.toTypedArray())
        if (visible != null) {
            brain.memory.rememberResource("log", visible.pos)
            return listOf(
                BreakBlock(visible.pos),
                PickupNearbyItems(5.0),
            )
        }
        val remembered = brain.memory.nearestResource("log", brain.player.position)
        if (remembered != null) {
            val instance = brain.player.instance
            if (instance != null && LOG_BLOCKS.any { instance.getBlock(remembered).compare(it) }) {
                return listOf(
                    BreakBlock(remembered),
                    PickupNearbyItems(5.0),
                )
            }
            brain.memory.forgetResourceAt("log", remembered)
        }
        val dir = brain.exploration.exploreDirection()
        val target = Pos(
            brain.player.position.x() + dir.x() * 20.0,
            brain.player.position.y(),
            brain.player.position.z() + dir.z() * 20.0,
        )
        return listOf(WalkTo(target))
    }

    override fun shouldCancel(brain: BotBrain): Boolean =
        LOG_MATERIALS.any { brain.hasItem(it) } || PLANK_MATERIALS.any { brain.hasItem(it) }
}

class StripMineGoal(private val targetY: Int = 11) : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        var base = 0.45f
        if (brain.hasPickaxe() && !brain.hasItem(Material.IRON_INGOT) && !brain.hasItem(Material.DIAMOND)) {
            base += 0.2f
        }
        if (brain.player.position.y() <= targetY + 5) base += 0.1f
        return base * (brain.personality.resourcefulness + 0.5f)
    }

    override fun shouldActivate(brain: BotBrain): Boolean = brain.hasPickaxe()

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val actions = mutableListOf<BotAction>()
        if (player.position.blockY() > targetY + 2) {
            val direction = randomCardinal()
            val depthNeeded = player.position.blockY() - targetY
            actions.add(MineStaircase(direction, depthNeeded))
        } else {
            val minedDirections = brain.memory.recallLocations("mined_tunnel")
            val direction = pickUnminedDirection(player.position, minedDirections)
            brain.memory.rememberLocation("mined_tunnel", Pos(
                player.position.x() + direction.x() * 20,
                player.position.y(),
                player.position.z() + direction.z() * 20,
            ))
            actions.add(MineTunnel(direction, 20))
            actions.add(PickupNearbyItems(5.0))
        }
        return actions
    }

    private fun randomCardinal(): Vec {
        val directions = listOf(Vec(1.0, 0.0, 0.0), Vec(-1.0, 0.0, 0.0), Vec(0.0, 0.0, 1.0), Vec(0.0, 0.0, -1.0))
        return directions[Random.nextInt(directions.size)]
    }

    private fun pickUnminedDirection(pos: Point, mined: List<Point>): Vec {
        val directions = listOf(Vec(1.0, 0.0, 0.0), Vec(-1.0, 0.0, 0.0), Vec(0.0, 0.0, 1.0), Vec(0.0, 0.0, -1.0))
        for (dir in directions.shuffled()) {
            val endPoint = Pos(pos.x() + dir.x() * 20, pos.y(), pos.z() + dir.z() * 20)
            val isUsed = mined.any { it.distanceSquared(endPoint) < 100.0 }
            if (!isUsed) return dir
        }
        return directions[Random.nextInt(directions.size)]
    }
}

class CaveExploreGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        var base = 0.4f
        val interest = brain.exploration.findInterest()
        if (interest is ExplorationInterest.CaveOpening) base += 0.15f
        if (brain.hasItem(Material.TORCH)) base += 0.1f
        return base * (brain.personality.curiosity + 0.5f)
    }

    override fun shouldActivate(brain: BotBrain): Boolean = brain.hasPickaxe()

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val actions = mutableListOf<BotAction>()

        val interest = brain.exploration.findInterest()
        val cavePos = when (interest) {
            is ExplorationInterest.CaveOpening -> interest.pos
            else -> brain.memory.nearestRecalled("explored_cave", player.position)
        }

        if (cavePos == null) {
            val dir = brain.exploration.exploreDirection()
            return listOf(WalkTo(Pos(
                player.position.x() + dir.x() * 16.0,
                player.position.y(),
                player.position.z() + dir.z() * 16.0,
            )))
        }

        brain.memory.rememberLocation("explored_cave", cavePos)
        actions.add(WalkTo(Pos(cavePos.x(), cavePos.y(), cavePos.z())))

        val visibleOres = brain.vision.visibleOres()
        for (ore in visibleOres) {
            actions.add(MineVein(ore.pos, ore.block))
        }

        actions.add(PlaceTorch(cavePos))
        actions.add(PickupNearbyItems(6.0))
        return actions
    }
}

class GatherResourceGoal(private val resource: Material, private val minCount: Int = 16) : BotGoal() {

    private val resourceKey = resource.name()

    override fun calculateUtility(brain: BotBrain): Float {
        if (brain.countItem(resource) >= minCount) return 0f
        return 0.5f * (brain.personality.resourcefulness + 0.5f)
    }

    override fun shouldActivate(brain: BotBrain): Boolean = brain.countItem(resource) < minCount

    override fun createActions(brain: BotBrain): List<BotAction> {
        val oreBlock = MiningKnowledge.materialToOre(resource)
        if (oreBlock != null) {
            val visible = brain.vision.canSee(oreBlock)
            if (visible != null) {
                brain.memory.rememberResource(resourceKey, visible.pos)
                return listOf(MineVein(visible.pos, oreBlock), PickupNearbyItems(5.0))
            }
            val known = brain.memory.nearestResource(resourceKey, brain.player.position)
            if (known != null) {
                val instance = brain.player.instance
                if (instance != null && instance.getBlock(known.blockX(), known.blockY(), known.blockZ()).compare(oreBlock)) {
                    return listOf(MineVein(known, oreBlock), PickupNearbyItems(5.0))
                }
                brain.memory.forgetResourceAt(resourceKey, known)
            }
        }
        val dir = brain.exploration.exploreDirection()
        return listOf(WalkTo(Pos(
            brain.player.position.x() + dir.x() * 24.0,
            brain.player.position.y(),
            brain.player.position.z() + dir.z() * 24.0,
        )))
    }

    override fun shouldCancel(brain: BotBrain): Boolean = brain.countItem(resource) >= minCount
}
