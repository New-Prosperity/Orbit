package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

private const val HYSTERESIS_THRESHOLD = 0.1f
private const val SIGHTING_INTERVAL = 5
private const val GOAL_EVAL_INTERVAL = 10
private const val MIN_COMMITMENT_TICKS = 30
private const val INVENTORY_CHECK_INTERVAL = 10

class BotBrain(
    val player: Player,
    private val goals: List<BotGoal>,
    val personality: BotPersonality = BotPersonality(),
    val skill: BotSkillLevel = BotSkillLevels.AVERAGE,
    val memory: BotMemory = BotMemory(),
) {

    val vision = BotVision(player)
    val exploration = BotExploration(this, vision)

    var currentGoal: BotGoal? = null
        private set
    private var currentActions: List<BotAction> = emptyList()
    private var currentActionIndex = 0
    private var currentGoalUtility = 0f
    private var sightingTickCounter = 0
    private var goalEvalCounter = (player.uuid.hashCode() and 0x7FFFFFFF) % (GOAL_EVAL_INTERVAL + skill.decisionDelay)
    private var lastHealth = 0f
    private var idleEvalInterval = GOAL_EVAL_INTERVAL
    private var inventoryCache: Map<Material, Int>? = null
    private var inventoryCacheTick = -1L
    private var goalCommitmentTicks = 0
    private var pauseTicks = 0
    private var sprintPauseTicks = 0
    private var inventoryDirty = false
    private var lastInventoryHash = 0
    private var inventoryCheckCounter = 0

    init {
        BotAI.skillLevels[player.uuid] = skill
    }

    fun tick() {
        memory.tick()
        player.instance?.let { vision.scan(it) }
        exploration.tick()
        updatePlayerSightings()
        humanize()
        if (pauseTicks > 0) return
        checkInventoryChanged()
        tickCurrentAction()

        val healthDelta = abs(player.health - lastHealth)
        lastHealth = player.health

        val current = currentGoal
        if (current != null && current.shouldCancel(this)) {
            cancelCurrent()
            requestReevaluation()
        }

        goalCommitmentTicks++
        if (currentGoal != null && goalCommitmentTicks < MIN_COMMITMENT_TICKS && player.health >= 4f) {
            return
        }

        val evalInterval = GOAL_EVAL_INTERVAL + skill.decisionDelay

        val needsEval = currentGoal == null ||
            currentActions.isEmpty() ||
            currentActionIndex >= currentActions.size ||
            healthDelta > 2f

        if (!needsEval && ++goalEvalCounter % evalInterval != 0) return

        if (currentGoal == null && goalEvalCounter % idleEvalInterval != 0) {
            idleEvalInterval = (idleEvalInterval * 2).coerceAtMost(100)
            return
        }

        var bestGoal: BotGoal? = null
        var bestUtility = 0f
        for (goal in goals) {
            if (!goal.shouldActivate(this)) continue
            val utility = applyPersonality(goal, goal.calculateUtility(this))
            if (utility > bestUtility) {
                bestUtility = utility
                bestGoal = goal
            }
        }

        if (bestGoal != null) {
            val cur = currentGoal
            if (cur == null || bestGoal !== cur && bestUtility > currentGoalUtility + HYSTERESIS_THRESHOLD) {
                idleEvalInterval = GOAL_EVAL_INTERVAL
                switchGoal(bestGoal, bestUtility)
            }
        }
    }

    fun requestReevaluation() {
        goalEvalCounter = GOAL_EVAL_INTERVAL - 1
    }

    private fun applyPersonality(goal: BotGoal, baseUtility: Float): Float {
        val multiplier = when (goal) {
            is AttackNearestGoal -> personality.aggression + 0.5f
            is FleeGoal -> personality.caution + 0.5f
            is GatherWoodGoal -> personality.resourcefulness + 0.5f
            is CraftToolGoal -> personality.resourcefulness + 0.5f
            is ExploreGoal -> personality.curiosity + 0.5f
            is BuildShelterGoal -> personality.resourcefulness + 0.5f
            is MineOreGoal -> personality.resourcefulness + 0.5f
            is StripMineGoal -> personality.resourcefulness + 0.5f
            is CaveExploreGoal -> personality.curiosity + 0.5f
            is SmeltOresGoal -> personality.resourcefulness + 0.5f
            is ToolProgressionGoal -> personality.resourcefulness + 0.5f
            is GatherResourceGoal -> personality.resourcefulness + 0.5f
            is PlaceFurnaceGoal -> personality.resourcefulness + 0.5f
            is CriticalAttackGoal -> personality.aggression + 0.5f
            is ShieldDefenseGoal -> personality.caution + 0.5f
            is RangedAttackGoal -> personality.caution + 0.5f
            is UsePotionGoal -> personality.caution + 0.5f
            is BridgeGoal -> personality.resourcefulness + 0.5f
            else -> 1f
        }
        return (baseUtility * multiplier).coerceIn(0f, 1.5f)
    }

    private fun switchGoal(goal: BotGoal, utility: Float) {
        val actions = goal.createActions(this)
        if (actions.isEmpty()) return
        cancelCurrent()
        currentGoal = goal
        currentGoalUtility = utility
        currentActions = actions
        currentActionIndex = 0
        goalCommitmentTicks = 0
        currentActions[0].start(player)
    }

    private fun tickCurrentAction() {
        if (currentActions.isEmpty() || currentActionIndex >= currentActions.size) {
            if (currentGoal != null) cancelCurrent()
            return
        }
        val action = currentActions[currentActionIndex]
        action.tick(player)
        if (action.isComplete) {
            currentActionIndex++
            if (currentActionIndex < currentActions.size) {
                currentActions[currentActionIndex].start(player)
            } else {
                cancelCurrent()
            }
        }
    }

    private fun cancelCurrent() {
        if (currentActionIndex < currentActions.size) {
            currentActions[currentActionIndex].cancel(player)
        }
        currentGoal = null
        currentActions = emptyList()
        currentActionIndex = 0
        currentGoalUtility = 0f
    }

    private fun updatePlayerSightings() {
        if (++sightingTickCounter % SIGHTING_INTERVAL != 0) return
        val instance = player.instance ?: return
        instance.players.forEach { other ->
            if (other !== player && other.position.distanceSquared(player.position) <= 1024.0) {
                memory.updatePlayerSighting(
                    other.uuid,
                    other.position,
                    memory.isKnownThreat(other.uuid),
                )
            }
        }
    }

    fun hasPickaxe(): Boolean = hasItemMatching { MiningKnowledge.isPickaxe(it) }

    private fun refreshInventoryCache() {
        val cache = HashMap<Material, Int>()
        for (i in 0 until player.inventory.size) {
            val stack = player.inventory.getItemStack(i)
            if (!stack.isAir) cache.merge(stack.material(), stack.amount(), Int::plus)
        }
        inventoryCache = cache
        inventoryCacheTick = player.aliveTicks
    }

    fun hasItem(material: Material): Boolean {
        if (inventoryCacheTick != player.aliveTicks) refreshInventoryCache()
        return (inventoryCache?.get(material) ?: 0) > 0
    }

    fun hasItemMatching(predicate: (Material) -> Boolean): Boolean {
        if (inventoryCacheTick != player.aliveTicks) refreshInventoryCache()
        return inventoryCache?.keys?.any(predicate) ?: false
    }

    fun countItem(material: Material): Int {
        if (inventoryCacheTick != player.aliveTicks) refreshInventoryCache()
        return inventoryCache?.get(material) ?: 0
    }

    fun findSlot(material: Material): Int {
        for (i in 0 until player.inventory.size) {
            if (player.inventory.getItemStack(i).material() == material) return i
        }
        return -1
    }

    fun consumeItem(material: Material, count: Int) {
        var remaining = count
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

    fun giveItem(item: ItemStack) {
        player.inventory.addItemStack(item)
    }

    fun findNearestBlock(block: Block): Point? {
        val visible = vision.canSee(block)
        if (visible != null) return visible.pos
        val key = block.name()
        return memory.nearestRecalled(key, player.position)
            ?: memory.nearestResource(key, player.position)
    }

    fun findNearestEntity(type: EntityType, radius: Double): Entity? {
        val instance = player.instance ?: return null
        val radiusSq = radius * radius
        var nearest: Entity? = null
        var nearestDistSq = radiusSq
        for (entity in instance.getNearbyEntities(player.position, radius)) {
            if (entity === player || entity.entityType != type || !entity.isActive || entity.isRemoved) continue
            val distSq = entity.position.distanceSquared(player.position)
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq
                nearest = entity
            }
        }
        return nearest
    }

    fun findNearestPlayer(radius: Double): Player? {
        val instance = player.instance ?: return null
        val radiusSq = radius * radius
        var nearest: Player? = null
        var nearestDistSq = radiusSq
        for (other in instance.players) {
            if (other === player || !other.isOnline) continue
            val distSq = other.position.distanceSquared(player.position)
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq
                nearest = other
            }
        }
        return nearest
    }

    fun findNearestItem(radius: Double): ItemEntity? {
        val instance = player.instance ?: return null
        val radiusSq = radius * radius
        var nearest: ItemEntity? = null
        var nearestDistSq = radiusSq
        for (entity in instance.getNearbyEntities(player.position, radius)) {
            if (entity !is ItemEntity || !entity.isActive || entity.isRemoved) continue
            val distSq = entity.position.distanceSquared(player.position)
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq
                nearest = entity
            }
        }
        return nearest
    }

    fun findEntityByUuid(uuid: UUID): Player? {
        val instance = player.instance ?: return null
        return instance.players.firstOrNull { it.uuid == uuid && it.isOnline }
    }

    fun markInventoryDirty() {
        inventoryDirty = true
    }

    fun clear() {
        cancelCurrent()
        memory.clear()
        BotAI.skillLevels.remove(player.uuid)
    }

    private fun humanize() {
        if (pauseTicks > 0) {
            pauseTicks--
            return
        }
        if (Random.nextInt(200) == 0) {
            pauseTicks = 10 + Random.nextInt(20)
            return
        }

        if (sprintPauseTicks > 0) {
            sprintPauseTicks--
            player.isSprinting = false
        } else if (currentGoal !is AttackNearestGoal && currentGoal !is CriticalAttackGoal) {
            if (Random.nextInt(150) == 0) {
                player.isSprinting = false
                sprintPauseTicks = 20 + Random.nextInt(20)
            }
        }

        if (Random.nextInt(40) == 0) {
            val yawOffset = Random.nextFloat() * 20f - 10f
            val pitchOffset = Random.nextFloat() * 10f - 5f
            player.setView(
                player.position.yaw() + yawOffset,
                (player.position.pitch() + pitchOffset).coerceIn(-90f, 90f),
            )
        }
    }

    private fun checkInventoryChanged() {
        if (!inventoryDirty && ++inventoryCheckCounter % INVENTORY_CHECK_INTERVAL != 0) return
        if (!inventoryDirty) {
            var hash = 0
            for (i in 0 until player.inventory.size) {
                val stack = player.inventory.getItemStack(i)
                if (!stack.isAir) hash = hash * 31 + stack.material().hashCode() * 17 + stack.amount()
            }
            if (hash != lastInventoryHash) {
                lastInventoryHash = hash
                inventoryDirty = true
            }
        }
        if (inventoryDirty) {
            inventoryDirty = false
            organizeHotbar()
        }
    }

    private fun organizeHotbar() {
        val inv = player.inventory
        val bestSword = findBestSlot(HOTBAR_SWORDS)
        val bestPickaxe = findBestSlot(HOTBAR_PICKAXES)
        val bestAxe = findBestSlot(HOTBAR_AXES)
        val bowSlot = findAnySlot(HOTBAR_BOW)
        val blockSlot = findAnySlot(HOTBAR_BLOCKS)
        val foodSlot = findAnySlot(HOTBAR_FOOD)

        val assignments = mutableMapOf<Int, Int>()
        bestSword?.let { assignments[0] = it }
        bestPickaxe?.let { assignments[1] = it }
        bestAxe?.let { assignments[2] = it }
        bowSlot?.let { assignments[3] = it }
        blockSlot?.let { assignments[4] = it }
        foodSlot?.let { assignments[5] = it }

        val usedSources = mutableSetOf<Int>()
        val usedTargets = mutableSetOf<Int>()

        for ((target, source) in assignments) {
            if (source == target) {
                usedSources.add(source)
                usedTargets.add(target)
            }
        }

        for ((target, source) in assignments) {
            if (source in usedSources || target in usedTargets) continue
            val sourceStack = inv.getItemStack(source)
            val targetStack = inv.getItemStack(target)
            inv.setItemStack(target, sourceStack)
            inv.setItemStack(source, targetStack)
            usedSources.add(source)
            usedTargets.add(target)
        }

        val shield = findAnySlot(HOTBAR_SHIELD)
        if (shield != null) {
            val offhandSlot = inv.size - 1
            if (inv.getItemStack(offhandSlot).material() != Material.SHIELD) {
                val shieldStack = inv.getItemStack(shield)
                val offhandStack = inv.getItemStack(offhandSlot)
                inv.setItemStack(offhandSlot, shieldStack)
                inv.setItemStack(shield, offhandStack)
            }
        }
    }

    private fun findBestSlot(tiers: List<Material>): Int? {
        for (material in tiers) {
            val slot = findSlot(material)
            if (slot >= 0) return slot
        }
        return null
    }

    private fun findAnySlot(materials: Set<Material>): Int? {
        for (i in 0 until player.inventory.size) {
            if (player.inventory.getItemStack(i).material() in materials) return i
        }
        return null
    }

    companion object {
        private val HOTBAR_SWORDS = listOf(
            Material.NETHERITE_SWORD, Material.DIAMOND_SWORD, Material.IRON_SWORD,
            Material.STONE_SWORD, Material.WOODEN_SWORD,
        )
        private val HOTBAR_PICKAXES = listOf(
            Material.NETHERITE_PICKAXE, Material.DIAMOND_PICKAXE, Material.IRON_PICKAXE,
            Material.STONE_PICKAXE, Material.WOODEN_PICKAXE,
        )
        private val HOTBAR_AXES = listOf(
            Material.NETHERITE_AXE, Material.DIAMOND_AXE, Material.IRON_AXE,
            Material.STONE_AXE, Material.WOODEN_AXE,
        )
        private val HOTBAR_BOW = setOf(Material.BOW, Material.CROSSBOW)
        private val HOTBAR_BLOCKS = setOf(
            Material.COBBLESTONE, Material.DIRT, Material.OAK_PLANKS, Material.BIRCH_PLANKS,
            Material.SPRUCE_PLANKS, Material.STONE, Material.SAND, Material.SANDSTONE,
        )
        private val HOTBAR_FOOD = setOf(
            Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE, Material.GOLDEN_CARROT,
            Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.BREAD, Material.APPLE,
            Material.COOKED_CHICKEN, Material.COOKED_MUTTON, Material.BAKED_POTATO,
        )
        private val HOTBAR_SHIELD = setOf(Material.SHIELD)
    }
}
