package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import kotlin.math.sqrt
import kotlin.random.Random

private val WEAPON_MATERIALS = setOf(
    Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
    Material.IRON_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,
    Material.STONE_SWORD, Material.STONE_AXE, Material.WOODEN_SWORD,
)

private val HEALING_POTIONS = setOf(Material.POTION, Material.SPLASH_POTION)

abstract class BotGoal {
    abstract fun calculateUtility(brain: BotBrain): Float
    abstract fun shouldActivate(brain: BotBrain): Boolean
    abstract fun createActions(brain: BotBrain): List<BotAction>
    open fun shouldCancel(brain: BotBrain): Boolean = false
}

private val LOG_BLOCKS = setOf(
    Block.OAK_LOG, Block.BIRCH_LOG, Block.SPRUCE_LOG,
    Block.JUNGLE_LOG, Block.ACACIA_LOG, Block.DARK_OAK_LOG,
    Block.MANGROVE_LOG, Block.CHERRY_LOG,
)

private val LOG_MATERIALS = setOf(
    Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG,
    Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
    Material.MANGROVE_LOG, Material.CHERRY_LOG,
)

private val PLANK_MATERIALS = setOf(
    Material.OAK_PLANKS, Material.BIRCH_PLANKS, Material.SPRUCE_PLANKS,
    Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS,
    Material.MANGROVE_PLANKS, Material.CHERRY_PLANKS,
)

private val ARMOR_TIERS = listOf(
    listOf(Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS),
    listOf(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS),
    listOf(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS),
    listOf(Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS),
    listOf(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS),
    listOf(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS),
)

private val EQUIPMENT_SLOTS = listOf(
    EquipmentSlot.HELMET, EquipmentSlot.CHESTPLATE, EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS,
)

private val FOOD_MATERIALS = setOf(
    Material.APPLE, Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE,
    Material.BREAD, Material.COOKED_BEEF, Material.COOKED_PORKCHOP,
    Material.COOKED_CHICKEN, Material.COOKED_MUTTON, Material.COOKED_SALMON,
    Material.COOKED_COD, Material.GOLDEN_CARROT, Material.BAKED_POTATO,
    Material.COOKIE, Material.MELON_SLICE, Material.DRIED_KELP,
    Material.SWEET_BERRIES, Material.CARROT, Material.POTATO,
    Material.BEETROOT, Material.MUSHROOM_STEW, Material.BEETROOT_SOUP,
    Material.RABBIT_STEW, Material.ROTTEN_FLESH,
)

private val BUILD_BLOCKS = setOf(
    Material.COBBLESTONE, Material.DIRT, Material.OAK_PLANKS,
    Material.BIRCH_PLANKS, Material.SPRUCE_PLANKS, Material.STONE,
    Material.SAND, Material.SANDSTONE,
)

class SurviveGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val player = brain.player
        val healthRatio = player.health / player.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
        if (player.health < 4f) return 0.95f
        return (1.0f - healthRatio).coerceIn(0f, 1f)
    }

    override fun shouldActivate(brain: BotBrain): Boolean {
        val player = brain.player
        return player.health < 8f && brain.hasItemMatching { it in FOOD_MATERIALS }
    }

    override fun createActions(brain: BotBrain): List<BotAction> = listOf(BotAction.EatFood())

    override fun shouldCancel(brain: BotBrain): Boolean =
        brain.player.health >= brain.player.getAttributeValue(Attribute.MAX_HEALTH).toFloat() || !brain.hasItemMatching { it in FOOD_MATERIALS }
}

class FleeGoal(private val healthThreshold: Float = 6f, private val fleeRange: Double = 16.0) : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val player = brain.player
        val healthRatio = player.health / player.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
        val enemy = findNearestThreat(brain)
        if (enemy == null) return 0f
        val enemyDist = player.position.distance(enemy.position)
        if (enemyDist > fleeRange) return 0f
        return ((1.0f - healthRatio) * (1.0f - (enemyDist / fleeRange).toFloat())).coerceIn(0f, 1f)
    }

    override fun shouldActivate(brain: BotBrain): Boolean {
        val player = brain.player
        if (player.health >= healthThreshold) return false
        return findNearestThreat(brain) != null
    }

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val threat = findNearestThreat(brain) ?: return listOf(BotAction.Wait(20))
        val dx = player.position.x() - threat.position.x()
        val dz = player.position.z() - threat.position.z()
        val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1)
        val fleeX = player.position.x() + (dx / dist) * 16.0
        val fleeZ = player.position.z() + (dz / dist) * 16.0
        val fleePos = Pos(fleeX, player.position.y(), fleeZ)
        return listOf(BotAction.SprintTo(fleePos))
    }

    override fun shouldCancel(brain: BotBrain): Boolean =
        brain.player.health >= healthThreshold || findNearestThreat(brain) == null

    private fun findNearestThreat(brain: BotBrain): Player? {
        val memory = brain.memory
        val highestThreat = memory.getHighestThreat()
        if (highestThreat != null) {
            val threatPlayer = brain.findEntityByUuid(highestThreat)
            if (threatPlayer != null && threatPlayer.position.distance(brain.player.position) <= fleeRange) {
                return threatPlayer
            }
        }
        val nearbyThreats = memory.nearbyThreats(brain.player.position, fleeRange)
        for (threat in nearbyThreats) {
            val threatPlayer = brain.findEntityByUuid(threat.uuid)
            if (threatPlayer != null) return threatPlayer
        }
        return brain.findNearestPlayer(fleeRange)
    }
}

class AttackNearestGoal(private val range: Double = 24.0) : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val target = findTarget(brain) ?: return 0f
        val dist = brain.player.position.distance(target.position)
        if (dist > range) return 0f
        val distanceFactor = (1.0f - (dist / range).toFloat()).coerceIn(0f, 1f)
        val weaponBonus = if (hasGoodWeapon(brain)) 0.15f else 0f
        return (0.7f * distanceFactor + weaponBonus).coerceIn(0f, 1f)
    }

    override fun shouldActivate(brain: BotBrain): Boolean = findTarget(brain) != null

    override fun createActions(brain: BotBrain): List<BotAction> {
        val target = findTarget(brain) ?: return listOf(BotAction.Wait(10))
        brain.memory.updatePlayerSighting(target.uuid, target.position, true)
        return listOf(BotAction.AttackEntity(target, 3))
    }

    override fun shouldCancel(brain: BotBrain): Boolean = findTarget(brain) == null

    private fun findTarget(brain: BotBrain): Player? {
        val highestThreat = brain.memory.getHighestThreat()
        if (highestThreat != null) {
            val target = brain.findEntityByUuid(highestThreat)
            if (target != null && target.position.distance(brain.player.position) <= range) {
                return target
            }
        }
        return brain.findNearestPlayer(range)
    }

    private fun hasGoodWeapon(brain: BotBrain): Boolean {
        val held = brain.player.inventory.getItemStack(brain.player.heldSlot.toInt())
        return held.material() in setOf(
            Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.IRON_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,
        )
    }
}

class EquipBestArmorGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val unequipped = countUnequippedArmor(brain)
        return (unequipped * 0.15f).coerceIn(0f, 1f)
    }

    override fun shouldActivate(brain: BotBrain): Boolean = countUnequippedArmor(brain) > 0

    override fun createActions(brain: BotBrain): List<BotAction> {
        val actions = mutableListOf<BotAction>()
        for ((slotIndex, slot) in EQUIPMENT_SLOTS.withIndex()) {
            val current = brain.player.getEquipment(slot)
            val best = findBestForSlot(brain, slotIndex, current)
            if (best != null) {
                actions.add(BotAction.EquipItem(slot, best))
            }
        }
        return actions.ifEmpty { listOf(BotAction.Wait(5)) }
    }

    private fun countUnequippedArmor(brain: BotBrain): Int {
        var count = 0
        for ((slotIndex, slot) in EQUIPMENT_SLOTS.withIndex()) {
            val current = brain.player.getEquipment(slot)
            if (findBestForSlot(brain, slotIndex, current) != null) count++
        }
        return count
    }

    private fun findBestForSlot(brain: BotBrain, slotIndex: Int, current: ItemStack): ItemStack? {
        val currentTier = ARMOR_TIERS.indexOfFirst { current.material() == it[slotIndex] }
        for ((tierIndex, tier) in ARMOR_TIERS.withIndex()) {
            if (currentTier in 0..tierIndex) continue
            val material = tier[slotIndex]
            val slot = brain.findSlot(material)
            if (slot >= 0) return brain.player.inventory.getItemStack(slot)
        }
        return null
    }
}

class CraftToolGoal(private val tool: Material) : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        if (brain.hasItem(tool)) return 0f
        val recipe = recipeFor(tool) ?: return 0f
        val hasMaterials = recipe.all { (mat, count) -> brain.countItem(mat) >= count }
        return if (hasMaterials) 0.6f else 0.3f
    }

    override fun shouldActivate(brain: BotBrain): Boolean {
        if (brain.hasItem(tool)) return false
        val recipe = recipeFor(tool) ?: return false
        return recipe.all { (mat, count) -> brain.countItem(mat) >= count }
    }

    override fun createActions(brain: BotBrain): List<BotAction> {
        val recipe = recipeFor(tool) ?: return listOf(BotAction.Wait(10))
        val tablePos = brain.vision.canSee(Block.CRAFTING_TABLE)?.pos
            ?: brain.memory.nearestRecalled("crafting_table", brain.player.position)
        if (tablePos != null) brain.memory.rememberLocation("crafting_table", tablePos)
        return listOf(BotAction.CraftItem(ItemStack.of(tool), recipe, tablePos))
    }

    override fun shouldCancel(brain: BotBrain): Boolean = brain.hasItem(tool)

    private fun recipeFor(material: Material): List<Pair<Material, Int>>? = when (material) {
        Material.WOODEN_SWORD -> listOf(Material.OAK_PLANKS to 2, Material.STICK to 1)
        Material.WOODEN_PICKAXE -> listOf(Material.OAK_PLANKS to 3, Material.STICK to 2)
        Material.WOODEN_AXE -> listOf(Material.OAK_PLANKS to 3, Material.STICK to 2)
        Material.WOODEN_SHOVEL -> listOf(Material.OAK_PLANKS to 1, Material.STICK to 2)
        Material.STONE_SWORD -> listOf(Material.COBBLESTONE to 2, Material.STICK to 1)
        Material.STONE_PICKAXE -> listOf(Material.COBBLESTONE to 3, Material.STICK to 2)
        Material.STONE_AXE -> listOf(Material.COBBLESTONE to 3, Material.STICK to 2)
        Material.IRON_SWORD -> listOf(Material.IRON_INGOT to 2, Material.STICK to 1)
        Material.IRON_PICKAXE -> listOf(Material.IRON_INGOT to 3, Material.STICK to 2)
        Material.DIAMOND_SWORD -> listOf(Material.DIAMOND to 2, Material.STICK to 1)
        Material.DIAMOND_PICKAXE -> listOf(Material.DIAMOND to 3, Material.STICK to 2)
        Material.CRAFTING_TABLE -> listOf(Material.OAK_PLANKS to 4)
        Material.STICK -> listOf(Material.OAK_PLANKS to 2)
        Material.OAK_PLANKS -> listOf(Material.OAK_LOG to 1)
        else -> null
    }
}

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
                BotAction.BreakBlock(visible.pos),
                BotAction.PickupNearbyItems(5.0),
            )
        }
        val remembered = brain.memory.nearestResource(oreKey, brain.player.position)
        if (remembered != null) {
            val instance = brain.player.instance
            if (instance != null && instance.getBlock(remembered.blockX(), remembered.blockY(), remembered.blockZ()).compare(ore)) {
                return listOf(
                    BotAction.BreakBlock(remembered),
                    BotAction.PickupNearbyItems(5.0),
                )
            }
            brain.memory.forgetResourceAt(oreKey, remembered)
        }
        return listOf(BotAction.Wait(10))
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
                BotAction.BreakBlock(visible.pos),
                BotAction.PickupNearbyItems(5.0),
            )
        }
        val remembered = brain.memory.nearestResource("log", brain.player.position)
        if (remembered != null) {
            val instance = brain.player.instance
            if (instance != null && LOG_BLOCKS.any { instance.getBlock(remembered).compare(it) }) {
                return listOf(
                    BotAction.BreakBlock(remembered),
                    BotAction.PickupNearbyItems(5.0),
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
        return listOf(BotAction.WalkTo(target))
    }

    override fun shouldCancel(brain: BotBrain): Boolean =
        LOG_MATERIALS.any { brain.hasItem(it) } || PLANK_MATERIALS.any { brain.hasItem(it) }
}

class BuildShelterGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val blockCount = BUILD_BLOCKS.sumOf { brain.countItem(it) }
        return if (blockCount >= 20) 0.4f else 0f
    }

    override fun shouldActivate(brain: BotBrain): Boolean {
        val blockCount = BUILD_BLOCKS.sumOf { brain.countItem(it) }
        return blockCount >= 20
    }

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val baseX = player.position.blockX() + 2
        val baseY = player.position.blockY()
        val baseZ = player.position.blockZ() + 2

        val material = BUILD_BLOCKS.firstOrNull { brain.hasItem(it) } ?: return listOf(BotAction.Wait(10))
        val block = materialToBlock(material) ?: return listOf(BotAction.Wait(10))

        val actions = mutableListOf<BotAction>()
        for (x in 0..2) {
            for (z in 0..2) {
                actions.add(BotAction.PlaceBlock(Pos(baseX + x.toDouble(), baseY.toDouble(), baseZ + z.toDouble()), block))
            }
        }
        for (x in 0..2) {
            for (z in 0..2) {
                if (x == 0 || x == 2 || z == 0 || z == 2) {
                    for (y in 1..2) {
                        actions.add(BotAction.PlaceBlock(Pos(baseX + x.toDouble(), baseY + y.toDouble(), baseZ + z.toDouble()), block))
                    }
                }
            }
        }
        for (x in 0..2) {
            for (z in 0..2) {
                actions.add(BotAction.PlaceBlock(Pos(baseX + x.toDouble(), baseY + 3.0, baseZ + z.toDouble()), block))
            }
        }
        brain.memory.rememberLocation("shelter", player.position)
        return actions
    }

    private fun materialToBlock(material: Material): Block? = when (material) {
        Material.COBBLESTONE -> Block.COBBLESTONE
        Material.DIRT -> Block.DIRT
        Material.OAK_PLANKS -> Block.OAK_PLANKS
        Material.BIRCH_PLANKS -> Block.BIRCH_PLANKS
        Material.SPRUCE_PLANKS -> Block.SPRUCE_PLANKS
        Material.STONE -> Block.STONE
        Material.SAND -> Block.SAND
        Material.SANDSTONE -> Block.SANDSTONE
        else -> null
    }
}

class ExploreGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float = 0.1f

    override fun shouldActivate(brain: BotBrain): Boolean = true

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val interest = brain.exploration.findInterest()
        if (interest != null && interest !is ExplorationInterest.UnexploredDirection) {
            return listOf(BotAction.WalkTo(Pos(interest.pos.x(), interest.pos.y(), interest.pos.z())))
        }

        val dir = brain.exploration.exploreDirection()
        val dangerZones = brain.memory.recallLocations("danger_zone")
        var targetX = player.position.x() + dir.x() * 32.0
        var targetZ = player.position.z() + dir.z() * 32.0

        var attempts = 0
        while (attempts < 5 && dangerZones.any { zone ->
            val dx = targetX - zone.x()
            val dz = targetZ - zone.z()
            dx * dx + dz * dz < 100.0
        }) {
            targetX += Random.nextDouble(-8.0, 8.0)
            targetZ += Random.nextDouble(-8.0, 8.0)
            attempts++
        }

        val target = Pos(targetX, player.position.y(), targetZ)
        return listOf(BotAction.WalkTo(target))
    }
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
            actions.add(BotAction.MineStaircase(direction, depthNeeded))
        } else {
            val minedDirections = brain.memory.recallLocations("mined_tunnel")
            val direction = pickUnminedDirection(player.position, minedDirections)
            brain.memory.rememberLocation("mined_tunnel", Pos(
                player.position.x() + direction.x() * 20,
                player.position.y(),
                player.position.z() + direction.z() * 20,
            ))
            actions.add(BotAction.MineTunnel(direction, 20))
            actions.add(BotAction.PickupNearbyItems(5.0))
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
            return listOf(BotAction.WalkTo(Pos(
                player.position.x() + dir.x() * 16.0,
                player.position.y(),
                player.position.z() + dir.z() * 16.0,
            )))
        }

        brain.memory.rememberLocation("explored_cave", cavePos)
        actions.add(BotAction.WalkTo(Pos(cavePos.x(), cavePos.y(), cavePos.z())))

        val visibleOres = brain.vision.visibleOres()
        for (ore in visibleOres) {
            actions.add(BotAction.MineVein(ore.pos, ore.block))
        }

        actions.add(BotAction.PlaceTorch(cavePos))
        actions.add(BotAction.PickupNearbyItems(6.0))
        return actions
    }
}

class SmeltOresGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val hasRawOres = MiningKnowledge.SMELTABLE_RAW_ORES.any { brain.hasItem(it) }
        if (!hasRawOres) return 0f
        return 0.5f * (brain.personality.resourcefulness + 0.5f)
    }

    override fun shouldActivate(brain: BotBrain): Boolean =
        MiningKnowledge.SMELTABLE_RAW_ORES.any { brain.hasItem(it) } &&
            (brain.hasItem(Material.COAL) || brain.hasItem(Material.CHARCOAL))

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val furnacePos = brain.vision.canSee(Block.FURNACE)?.pos
            ?: brain.memory.nearestRecalled("furnace", player.position)
        if (furnacePos == null) return listOf(BotAction.Wait(20))
        brain.memory.rememberLocation("furnace", furnacePos)
        val fuel = if (brain.hasItem(Material.COAL)) Material.COAL else Material.CHARCOAL
        val actions = mutableListOf<BotAction>()
        for (ore in MiningKnowledge.SMELTABLE_RAW_ORES) {
            if (brain.hasItem(ore)) {
                actions.add(BotAction.SmeltItems(furnacePos, ore, fuel))
            }
        }
        return actions.ifEmpty { listOf(BotAction.Wait(10)) }
    }
}

class ToolProgressionGoal : BotGoal() {

    private val PICKAXE_TIERS = listOf(
        Material.DIAMOND_PICKAXE to listOf(Material.DIAMOND to 3, Material.STICK to 2),
        Material.IRON_PICKAXE to listOf(Material.IRON_INGOT to 3, Material.STICK to 2),
        Material.STONE_PICKAXE to listOf(Material.COBBLESTONE to 3, Material.STICK to 2),
        Material.WOODEN_PICKAXE to listOf(Material.OAK_PLANKS to 3, Material.STICK to 2),
    )

    private val SWORD_TIERS = listOf(
        Material.DIAMOND_SWORD to listOf(Material.DIAMOND to 2, Material.STICK to 1),
        Material.IRON_SWORD to listOf(Material.IRON_INGOT to 2, Material.STICK to 1),
        Material.STONE_SWORD to listOf(Material.COBBLESTONE to 2, Material.STICK to 1),
        Material.WOODEN_SWORD to listOf(Material.OAK_PLANKS to 2, Material.STICK to 1),
    )

    private val AXE_TIERS = listOf(
        Material.DIAMOND_AXE to listOf(Material.DIAMOND to 3, Material.STICK to 2),
        Material.IRON_AXE to listOf(Material.IRON_INGOT to 3, Material.STICK to 2),
        Material.STONE_AXE to listOf(Material.COBBLESTONE to 3, Material.STICK to 2),
        Material.WOODEN_AXE to listOf(Material.OAK_PLANKS to 3, Material.STICK to 2),
    )

    override fun calculateUtility(brain: BotBrain): Float {
        val canUpgrade = findBestCraftable(brain, PICKAXE_TIERS) != null ||
            findBestCraftable(brain, SWORD_TIERS) != null ||
            findBestCraftable(brain, AXE_TIERS) != null
        if (!canUpgrade) return 0f
        return 0.65f * (brain.personality.resourcefulness + 0.5f)
    }

    override fun shouldActivate(brain: BotBrain): Boolean =
        findBestCraftable(brain, PICKAXE_TIERS) != null ||
            findBestCraftable(brain, SWORD_TIERS) != null ||
            findBestCraftable(brain, AXE_TIERS) != null

    override fun createActions(brain: BotBrain): List<BotAction> {
        val tablePos = brain.vision.canSee(Block.CRAFTING_TABLE)?.pos
            ?: brain.memory.nearestRecalled("crafting_table", brain.player.position)
        val actions = mutableListOf<BotAction>()
        for (tiers in listOf(PICKAXE_TIERS, SWORD_TIERS, AXE_TIERS)) {
            val upgrade = findBestCraftable(brain, tiers)
            if (upgrade != null) {
                val (material, recipe) = upgrade
                actions.add(BotAction.CraftItem(ItemStack.of(material), recipe, tablePos))
            }
        }
        return actions.ifEmpty { listOf(BotAction.Wait(10)) }
    }

    private fun findBestCraftable(
        brain: BotBrain,
        tiers: List<Pair<Material, List<Pair<Material, Int>>>>,
    ): Pair<Material, List<Pair<Material, Int>>>? {
        val currentBest = tiers.indexOfFirst { (mat, _) -> brain.hasItem(mat) }
        for ((index, entry) in tiers.withIndex()) {
            if (currentBest in 0..index) continue
            val (material, recipe) = entry
            val canCraft = recipe.all { (mat, count) -> brain.countItem(mat) >= count }
            if (canCraft) return material to recipe
        }
        return null
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
                return listOf(BotAction.MineVein(visible.pos, oreBlock), BotAction.PickupNearbyItems(5.0))
            }
            val known = brain.memory.nearestResource(resourceKey, brain.player.position)
            if (known != null) {
                val instance = brain.player.instance
                if (instance != null && instance.getBlock(known.blockX(), known.blockY(), known.blockZ()).compare(oreBlock)) {
                    return listOf(BotAction.MineVein(known, oreBlock), BotAction.PickupNearbyItems(5.0))
                }
                brain.memory.forgetResourceAt(resourceKey, known)
            }
        }
        val dir = brain.exploration.exploreDirection()
        return listOf(BotAction.WalkTo(Pos(
            brain.player.position.x() + dir.x() * 24.0,
            brain.player.position.y(),
            brain.player.position.z() + dir.z() * 24.0,
        )))
    }

    override fun shouldCancel(brain: BotBrain): Boolean = brain.countItem(resource) >= minCount
}

class InventoryManagementGoal : BotGoal() {

    private val PRIORITY_ORDER = listOf(
        Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT,
        Material.RAW_GOLD, Material.RAW_IRON, Material.RAW_COPPER, Material.LAPIS_LAZULI,
        Material.REDSTONE, Material.COAL, Material.COPPER_INGOT,
        Material.DIAMOND_PICKAXE, Material.IRON_PICKAXE, Material.STONE_PICKAXE,
        Material.DIAMOND_SWORD, Material.IRON_SWORD, Material.STONE_SWORD,
        Material.COOKED_BEEF, Material.BREAD, Material.APPLE,
        Material.OAK_LOG, Material.OAK_PLANKS, Material.STICK,
        Material.COBBLESTONE, Material.DIRT, Material.GRAVEL,
    )

    override fun calculateUtility(brain: BotBrain): Float {
        val fillRatio = inventoryFillRatio(brain)
        if (fillRatio < 0.8f) return 0f
        return 0.55f
    }

    override fun shouldActivate(brain: BotBrain): Boolean = inventoryFillRatio(brain) >= 0.8f

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val slotsToDrop = mutableListOf<Int>()
        val inventory = player.inventory
        for (i in 0 until inventory.size) {
            val stack = inventory.getItemStack(i)
            if (stack.isAir) continue
            val priority = PRIORITY_ORDER.indexOf(stack.material())
            if (priority < 0 || priority >= PRIORITY_ORDER.size - 3) {
                slotsToDrop.add(i)
            }
        }
        slotsToDrop.sortByDescending { slot ->
            val mat = inventory.getItemStack(slot).material()
            val idx = PRIORITY_ORDER.indexOf(mat)
            if (idx < 0) Int.MAX_VALUE else idx
        }
        val toDrop = slotsToDrop.take(9)
        if (toDrop.isEmpty()) return listOf(BotAction.Wait(20))
        for (slot in toDrop) {
            inventory.setItemStack(slot, ItemStack.AIR)
        }
        return listOf(BotAction.Wait(5))
    }

    private fun inventoryFillRatio(brain: BotBrain): Float {
        var filled = 0
        val inventory = brain.player.inventory
        for (i in 0 until inventory.size) {
            if (!inventory.getItemStack(i).isAir) filled++
        }
        return filled.toFloat() / inventory.size.toFloat()
    }
}

class PlaceFurnaceGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val hasRawOres = MiningKnowledge.SMELTABLE_RAW_ORES.any { brain.hasItem(it) }
        val hasCobble = brain.countItem(Material.COBBLESTONE) >= 8
        val hasFurnace = brain.vision.canSee(Block.FURNACE) != null ||
            brain.memory.nearestRecalled("furnace", brain.player.position) != null
        if (!hasRawOres || !hasCobble || hasFurnace) return 0f
        return 0.5f
    }

    override fun shouldActivate(brain: BotBrain): Boolean {
        val hasRawOres = MiningKnowledge.SMELTABLE_RAW_ORES.any { brain.hasItem(it) }
        val hasCobble = brain.countItem(Material.COBBLESTONE) >= 8
        val noFurnace = brain.vision.canSee(Block.FURNACE) == null &&
            brain.memory.nearestRecalled("furnace", brain.player.position) == null
        return hasRawOres && hasCobble && noFurnace
    }

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val furnaceRecipe = listOf(Material.COBBLESTONE to 8)
        val placePos = Pos(
            player.position.blockX() + 1.0,
            player.position.blockY().toDouble(),
            player.position.blockZ().toDouble(),
        )
        brain.memory.rememberLocation("furnace", placePos)
        return listOf(
            BotAction.CraftItem(ItemStack.of(Material.FURNACE), furnaceRecipe, null),
            BotAction.PlaceBlock(placePos, Block.FURNACE),
        )
    }
}

class CriticalAttackGoal(private val range: Double = 16.0) : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val target = findTarget(brain) ?: return 0f
        val dist = brain.player.position.distance(target.position)
        if (dist > range) return 0f
        val distanceFactor = (1.0f - (dist / range).toFloat()).coerceIn(0f, 1f)
        val weaponBonus = if (hasGoodWeapon(brain)) 0.2f else 0f
        return (0.75f * distanceFactor + weaponBonus).coerceIn(0f, 1f)
    }

    override fun shouldActivate(brain: BotBrain): Boolean {
        val target = findTarget(brain) ?: return false
        return hasGoodWeapon(brain) && target.position.distance(brain.player.position) <= range
    }

    override fun createActions(brain: BotBrain): List<BotAction> {
        val target = findTarget(brain) ?: return listOf(BotAction.Wait(10))
        brain.memory.updatePlayerSighting(target.uuid, target.position, true)
        return listOf(
            BotAction.SprintTo(target.position),
            BotAction.CriticalHit(target),
            BotAction.CriticalHit(target),
            BotAction.CriticalHit(target),
        )
    }

    override fun shouldCancel(brain: BotBrain): Boolean = findTarget(brain) == null

    private fun findTarget(brain: BotBrain): Player? {
        val highestThreat = brain.memory.getHighestThreat()
        if (highestThreat != null) {
            val target = brain.findEntityByUuid(highestThreat)
            if (target != null && target.position.distance(brain.player.position) <= range) return target
        }
        return brain.findNearestPlayer(range)
    }

    private fun hasGoodWeapon(brain: BotBrain): Boolean {
        val held = brain.player.inventory.getItemStack(brain.player.heldSlot.toInt())
        return held.material() in WEAPON_MATERIALS
    }
}

class ShieldDefenseGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        if (!hasShield(brain)) return 0f
        val player = brain.player
        val healthRatio = player.health / player.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
        val hasNearbyThreat = brain.memory.nearbyThreats(player.position, 8.0).isNotEmpty()
        if (!hasNearbyThreat) return 0f
        return if (player.health < 10f) (0.8f * (1f - healthRatio)).coerceIn(0f, 1f) else 0.3f
    }

    override fun shouldActivate(brain: BotBrain): Boolean {
        if (!hasShield(brain)) return false
        val threats = brain.memory.nearbyThreats(brain.player.position, 8.0)
        return threats.isNotEmpty()
    }

    override fun createActions(brain: BotBrain): List<BotAction> = listOf(
        BotAction.ShieldBlock(40),
        BotAction.Wait(10),
    )

    override fun shouldCancel(brain: BotBrain): Boolean =
        !hasShield(brain) || brain.memory.nearbyThreats(brain.player.position, 8.0).isEmpty()

    private fun hasShield(brain: BotBrain): Boolean {
        val offhand = brain.player.inventory.getItemStack(brain.player.inventory.size - 1)
        return offhand.material() == Material.SHIELD
    }
}

class RangedAttackGoal(private val range: Double = 24.0) : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        val target = findTarget(brain) ?: return 0f
        val dist = brain.player.position.distance(target.position)
        if (dist > range) return 0f
        if (dist <= 8.0) return 0.3f
        val distanceFactor = ((dist - 8.0) / (range - 8.0)).toFloat().coerceIn(0f, 1f)
        return (0.75f * distanceFactor).coerceIn(0f, 1f)
    }

    override fun shouldActivate(brain: BotBrain): Boolean {
        if (!brain.hasItem(Material.BOW)) return false
        if (!brain.hasItem(Material.ARROW)) return false
        val target = findTarget(brain) ?: return false
        return target.position.distance(brain.player.position) <= range
    }

    override fun createActions(brain: BotBrain): List<BotAction> {
        val target = findTarget(brain) ?: return listOf(BotAction.Wait(10))
        brain.memory.updatePlayerSighting(target.uuid, target.position, true)
        val dist = brain.player.position.distance(target.position)
        if (dist <= 8.0) {
            return listOf(BotAction.AttackEntity(target, 2))
        }
        return listOf(BotAction.ShootBow(target, 20))
    }

    override fun shouldCancel(brain: BotBrain): Boolean {
        if (!brain.hasItem(Material.BOW) || !brain.hasItem(Material.ARROW)) return true
        return findTarget(brain) == null
    }

    private fun findTarget(brain: BotBrain): Player? {
        val highestThreat = brain.memory.getHighestThreat()
        if (highestThreat != null) {
            val target = brain.findEntityByUuid(highestThreat)
            if (target != null && target.position.distance(brain.player.position) <= range) return target
        }
        return brain.findNearestPlayer(range)
    }
}

class UsePotionGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        if (!hasPotions(brain)) return 0f
        val player = brain.player
        if (player.health < 6f) return 0.85f
        if (player.health < 10f) return 0.6f
        return 0f
    }

    override fun shouldActivate(brain: BotBrain): Boolean =
        hasPotions(brain) && brain.player.health < 10f

    override fun createActions(brain: BotBrain): List<BotAction> = listOf(BotAction.DrinkPotion())

    override fun shouldCancel(brain: BotBrain): Boolean =
        !hasPotions(brain) || brain.player.health >= brain.player.getAttributeValue(Attribute.MAX_HEALTH).toFloat()

    private fun hasPotions(brain: BotBrain): Boolean =
        brain.hasItemMatching { it in HEALING_POTIONS }
}

class BridgeGoal : BotGoal() {

    override fun calculateUtility(brain: BotBrain): Float {
        if (!hasBuildBlocks(brain)) return 0f
        if (!hasGapAhead(brain)) return 0f
        return 0.4f
    }

    override fun shouldActivate(brain: BotBrain): Boolean =
        hasBuildBlocks(brain) && hasGapAhead(brain)

    override fun createActions(brain: BotBrain): List<BotAction> {
        val player = brain.player
        val yaw = Math.toRadians(player.position.yaw().toDouble())
        val direction = Vec(-kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw))
        val gapLength = measureGap(brain, direction).coerceIn(1, 16)
        val block = findBuildBlock(brain) ?: return listOf(BotAction.Wait(10))
        return listOf(BotAction.BridgeForward(direction, gapLength, block))
    }

    override fun shouldCancel(brain: BotBrain): Boolean = !hasBuildBlocks(brain)

    private fun hasBuildBlocks(brain: BotBrain): Boolean =
        BUILD_BLOCKS.any { brain.countItem(it) >= 4 }

    private fun hasGapAhead(brain: BotBrain): Boolean {
        val player = brain.player
        val instance = player.instance ?: return false
        val yaw = Math.toRadians(player.position.yaw().toDouble())
        val dx = -kotlin.math.sin(yaw)
        val dz = kotlin.math.cos(yaw)
        val checkX = (player.position.x() + dx * 2).toInt()
        val checkZ = (player.position.z() + dz * 2).toInt()
        val floorY = player.position.blockY() - 1
        val block = instance.getBlock(checkX, floorY, checkZ)
        return block.isAir || block.isLiquid
    }

    private fun measureGap(brain: BotBrain, direction: Vec): Int {
        val player = brain.player
        val instance = player.instance ?: return 0
        val floorY = player.position.blockY() - 1
        var length = 0
        for (i in 1..16) {
            val checkX = (player.position.x() + direction.x() * i).toInt()
            val checkZ = (player.position.z() + direction.z() * i).toInt()
            val block = instance.getBlock(checkX, floorY, checkZ)
            if (block.isAir || block.isLiquid) length++
            else break
        }
        return length
    }

    private fun findBuildBlock(brain: BotBrain): Block? {
        for (mat in BUILD_BLOCKS) {
            if (brain.countItem(mat) >= 4) {
                return when (mat) {
                    Material.COBBLESTONE -> Block.COBBLESTONE
                    Material.DIRT -> Block.DIRT
                    Material.OAK_PLANKS -> Block.OAK_PLANKS
                    Material.BIRCH_PLANKS -> Block.BIRCH_PLANKS
                    Material.SPRUCE_PLANKS -> Block.SPRUCE_PLANKS
                    Material.STONE -> Block.STONE
                    Material.SAND -> Block.SAND
                    Material.SANDSTONE -> Block.SANDSTONE
                    else -> null
                }
            }
        }
        return null
    }
}
