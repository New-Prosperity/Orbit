package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Point
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

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
            targetPos?.let { BotMovement.moveToward(player, it, false) }
            return
        }
        scanCooldown = 10
        var nearest: ItemEntity? = null
        var nearestDistSq = radius * radius
        instance.entityTracker.nearbyEntities(player.position, radius, EntityTracker.Target.ITEMS) { item ->
            if (nearest == null || item.position.distanceSquared(player.position) < nearestDistSq) {
                nearest = item
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
