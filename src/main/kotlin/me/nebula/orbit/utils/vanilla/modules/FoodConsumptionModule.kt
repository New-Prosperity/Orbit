package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.progression.mission.MissionTracker
import me.nebula.orbit.utils.achievement.AchievementTriggerManager
import me.nebula.orbit.utils.sound.playSound
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private data class FoodValues(val nutrition: Int, val saturation: Float)

private val FOOD_MAP = mapOf(
    Material.APPLE to FoodValues(4, 2.4f),
    Material.BAKED_POTATO to FoodValues(5, 6.0f),
    Material.BEEF to FoodValues(3, 1.8f),
    Material.BEETROOT to FoodValues(1, 1.2f),
    Material.BEETROOT_SOUP to FoodValues(6, 7.2f),
    Material.BREAD to FoodValues(5, 6.0f),
    Material.CARROT to FoodValues(3, 3.6f),
    Material.CHICKEN to FoodValues(2, 1.2f),
    Material.CHORUS_FRUIT to FoodValues(4, 2.4f),
    Material.COD to FoodValues(2, 0.4f),
    Material.COOKED_BEEF to FoodValues(8, 12.8f),
    Material.COOKED_CHICKEN to FoodValues(6, 7.2f),
    Material.COOKED_COD to FoodValues(5, 6.0f),
    Material.COOKED_MUTTON to FoodValues(6, 9.6f),
    Material.COOKED_PORKCHOP to FoodValues(8, 12.8f),
    Material.COOKED_RABBIT to FoodValues(5, 6.0f),
    Material.COOKED_SALMON to FoodValues(6, 9.6f),
    Material.COOKIE to FoodValues(2, 0.4f),
    Material.DRIED_KELP to FoodValues(1, 0.6f),
    Material.ENCHANTED_GOLDEN_APPLE to FoodValues(4, 9.6f),
    Material.GLOW_BERRIES to FoodValues(2, 0.4f),
    Material.GOLDEN_APPLE to FoodValues(4, 9.6f),
    Material.GOLDEN_CARROT to FoodValues(6, 14.4f),
    Material.HONEY_BOTTLE to FoodValues(6, 1.2f),
    Material.MELON_SLICE to FoodValues(2, 1.2f),
    Material.MUSHROOM_STEW to FoodValues(6, 7.2f),
    Material.MUTTON to FoodValues(2, 1.2f),
    Material.POISONOUS_POTATO to FoodValues(2, 1.2f),
    Material.PORKCHOP to FoodValues(3, 1.8f),
    Material.POTATO to FoodValues(1, 0.6f),
    Material.PUFFERFISH to FoodValues(1, 0.2f),
    Material.PUMPKIN_PIE to FoodValues(8, 4.8f),
    Material.RABBIT to FoodValues(3, 1.8f),
    Material.RABBIT_STEW to FoodValues(10, 12.0f),
    Material.SALMON to FoodValues(2, 0.4f),
    Material.SPIDER_EYE to FoodValues(2, 3.2f),
    Material.SUSPICIOUS_STEW to FoodValues(6, 7.2f),
    Material.SWEET_BERRIES to FoodValues(2, 0.4f),
    Material.TROPICAL_FISH to FoodValues(1, 0.2f),
)

object FoodConsumptionModule : VanillaModule {

    override val id = "food-consumption"
    override val description = "Eating food restores food level and saturation"

    private val foodEatenCounters = ConcurrentHashMap<UUID, AtomicLong>()

    fun resetCounters() = foodEatenCounters.clear()

    fun foodEatenBy(uuid: UUID): Long = foodEatenCounters[uuid]?.get() ?: 0L

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-food-consumption")

        node.addListener(PlayerUseItemEvent::class.java) { event ->
            val player = event.player
            val material = event.itemStack.material()
            val food = FOOD_MAP[material] ?: return@addListener

            if (player.food >= 20 && material != Material.GOLDEN_APPLE && material != Material.ENCHANTED_GOLDEN_APPLE && material != Material.CHORUS_FRUIT) {
                return@addListener
            }

            player.food = (player.food + food.nutrition).coerceAtMost(20)
            player.foodSaturation = (player.foodSaturation + food.saturation).coerceAtMost(player.food.toFloat())
            player.playSound(SoundEvent.ENTITY_PLAYER_BURP)

            if (player.gameMode != GameMode.CREATIVE) {
                player.setItemInMainHand(player.itemInMainHand.consume(1))
            }

            if (material == Material.BEETROOT_SOUP || material == Material.MUSHROOM_STEW || material == Material.RABBIT_STEW) {
                player.inventory.addItemStack(ItemStack.of(Material.BOWL))
            }

            if (material == Material.HONEY_BOTTLE) {
                player.inventory.addItemStack(ItemStack.of(Material.GLASS_BOTTLE))
            }

            MissionTracker.onEatFood(player)
            val count = foodEatenCounters.computeIfAbsent(player.uuid) { AtomicLong() }.incrementAndGet()
            AchievementTriggerManager.evaluate(player, "food_eaten", count)
        }

        return node
    }
}
