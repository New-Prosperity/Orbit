package me.nebula.orbit.mechanic.food

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.item.PlayerFinishItemUseEvent
import net.minestom.server.event.player.PlayerPreEatEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

private val EXHAUSTION_TAG = Tag.Float("mechanic:food:exhaustion").defaultValue(0f)
private val REGEN_TIMER_TAG = Tag.Integer("mechanic:food:regen_timer").defaultValue(0)
private val STARVATION_TIMER_TAG = Tag.Integer("mechanic:food:starvation_timer").defaultValue(0)

fun Player.addExhaustion(amount: Float) {
    updateTag(EXHAUSTION_TAG) { it + amount }
}

fun Player.clearExhaustion() {
    removeTag(EXHAUSTION_TAG)
    removeTag(REGEN_TIMER_TAG)
    removeTag(STARVATION_TIMER_TAG)
}

class FoodModule : OrbitModule("food") {

    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerPreEatEvent::class.java) { event ->
            val food = event.itemStack.get(DataComponents.FOOD) ?: return@addListener
            val consumable = event.itemStack.get(DataComponents.CONSUMABLE)
            event.eatingTime = consumable?.consumeTicks()?.toLong() ?: 32L
            if (event.player.food >= 20 && !food.canAlwaysEat()) {
                event.isCancelled = true
            }
        }

        eventNode.addListener(PlayerFinishItemUseEvent::class.java) { event ->
            val player = event.player
            val food = event.itemStack.get(DataComponents.FOOD) ?: return@addListener
            val saturation = food.nutrition() * food.saturationModifier() * 2f
            player.food = (player.food + food.nutrition()).coerceAtMost(20)
            player.foodSaturation = (player.foodSaturation + saturation).coerceAtMost(player.food.toFloat())
            val slot = if (event.hand == PlayerHand.MAIN) player.heldSlot.toInt() else 45
            val current = player.inventory.getItemStack(slot)
            if (current.amount() > 1) {
                player.inventory.setItemStack(slot, current.withAmount(current.amount() - 1))
            } else {
                player.inventory.setItemStack(slot, ItemStack.AIR)
            }
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(20))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        super.onDisable()
    }

    private fun tick() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            if (player.isDead || player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return@forEach
            processExhaustion(player)
            processRegen(player)
            processStarvation(player)
        }
    }

    private fun processExhaustion(player: Player) {
        val exhaustion = player.getTag(EXHAUSTION_TAG)
        if (exhaustion >= 4f) {
            player.setTag(EXHAUSTION_TAG, exhaustion - 4f)
            if (player.foodSaturation > 0f) {
                player.foodSaturation = (player.foodSaturation - 1f).coerceAtLeast(0f)
            } else if (player.food > 0) {
                player.food = player.food - 1
            }
        }
    }

    private fun processRegen(player: Player) {
        val maxHealth = player.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
        if (player.food >= 18 && player.health < maxHealth) {
            val timer = player.getTag(REGEN_TIMER_TAG) + 1
            if (timer >= 4) {
                player.health = (player.health + 1f).coerceAtMost(maxHealth)
                player.addExhaustion(6f)
                player.setTag(REGEN_TIMER_TAG, 0)
            } else {
                player.setTag(REGEN_TIMER_TAG, timer)
            }
        } else {
            player.setTag(REGEN_TIMER_TAG, 0)
        }
    }

    private fun processStarvation(player: Player) {
        if (player.food <= 0) {
            val timer = player.getTag(STARVATION_TIMER_TAG) + 1
            if (timer >= 4) {
                if (player.health > 1f) {
                    player.damage(DamageType.STARVE, 1f)
                }
                player.setTag(STARVATION_TIMER_TAG, 0)
            } else {
                player.setTag(STARVATION_TIMER_TAG, timer)
            }
        } else {
            player.setTag(STARVATION_TIMER_TAG, 0)
        }
    }
}
