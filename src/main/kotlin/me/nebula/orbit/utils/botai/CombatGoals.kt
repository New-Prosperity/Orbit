package me.nebula.orbit.utils.botai

import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.item.Material

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
        val target = findTarget(brain) ?: return listOf(Wait(10))
        brain.memory.updatePlayerSighting(target.uuid, target.position, true)
        return listOf(AttackEntity(target, 3))
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
        val target = findTarget(brain) ?: return listOf(Wait(10))
        brain.memory.updatePlayerSighting(target.uuid, target.position, true)
        return listOf(
            SprintTo(target.position),
            CriticalHit(target),
            CriticalHit(target),
            CriticalHit(target),
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
        ShieldBlock(40),
        Wait(10),
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
        val target = findTarget(brain) ?: return listOf(Wait(10))
        brain.memory.updatePlayerSighting(target.uuid, target.position, true)
        val dist = brain.player.position.distance(target.position)
        if (dist <= 8.0) {
            return listOf(AttackEntity(target, 2))
        }
        return listOf(ShootBow(target, 20))
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
