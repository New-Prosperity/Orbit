package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.item.ItemStack
import kotlin.math.sqrt

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

    override fun createActions(brain: BotBrain): List<BotAction> = listOf(EatFood())

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
        val threat = findNearestThreat(brain) ?: return listOf(Wait(20))
        val dx = player.position.x() - threat.position.x()
        val dz = player.position.z() - threat.position.z()
        val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1)
        val fleeX = player.position.x() + (dx / dist) * 16.0
        val fleeZ = player.position.z() + (dz / dist) * 16.0
        val fleePos = Pos(fleeX, player.position.y(), fleeZ)
        return listOf(SprintTo(fleePos))
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
                actions.add(EquipItem(slot, best))
            }
        }
        return actions.ifEmpty { listOf(Wait(5)) }
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

    override fun createActions(brain: BotBrain): List<BotAction> = listOf(DrinkPotion())

    override fun shouldCancel(brain: BotBrain): Boolean =
        !hasPotions(brain) || brain.player.health >= brain.player.getAttributeValue(Attribute.MAX_HEALTH).toFloat()

    private fun hasPotions(brain: BotBrain): Boolean =
        brain.hasItemMatching { it in HEALING_POTIONS }
}
