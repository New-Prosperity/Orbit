package me.nebula.orbit.mechanic.silktouch

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.timer.TaskSchedule
import java.time.Duration

private val SILK_TOUCH_DROPS: Map<Block, Material> = mapOf(
    Block.STONE to Material.STONE,
    Block.COAL_ORE to Material.COAL_ORE,
    Block.DEEPSLATE_COAL_ORE to Material.DEEPSLATE_COAL_ORE,
    Block.IRON_ORE to Material.IRON_ORE,
    Block.DEEPSLATE_IRON_ORE to Material.DEEPSLATE_IRON_ORE,
    Block.COPPER_ORE to Material.COPPER_ORE,
    Block.DEEPSLATE_COPPER_ORE to Material.DEEPSLATE_COPPER_ORE,
    Block.GOLD_ORE to Material.GOLD_ORE,
    Block.DEEPSLATE_GOLD_ORE to Material.DEEPSLATE_GOLD_ORE,
    Block.DIAMOND_ORE to Material.DIAMOND_ORE,
    Block.DEEPSLATE_DIAMOND_ORE to Material.DEEPSLATE_DIAMOND_ORE,
    Block.LAPIS_ORE to Material.LAPIS_ORE,
    Block.DEEPSLATE_LAPIS_ORE to Material.DEEPSLATE_LAPIS_ORE,
    Block.REDSTONE_ORE to Material.REDSTONE_ORE,
    Block.DEEPSLATE_REDSTONE_ORE to Material.DEEPSLATE_REDSTONE_ORE,
    Block.EMERALD_ORE to Material.EMERALD_ORE,
    Block.DEEPSLATE_EMERALD_ORE to Material.DEEPSLATE_EMERALD_ORE,
    Block.NETHER_GOLD_ORE to Material.NETHER_GOLD_ORE,
    Block.NETHER_QUARTZ_ORE to Material.NETHER_QUARTZ_ORE,
    Block.GLASS to Material.GLASS,
    Block.WHITE_STAINED_GLASS to Material.WHITE_STAINED_GLASS,
    Block.ORANGE_STAINED_GLASS to Material.ORANGE_STAINED_GLASS,
    Block.MAGENTA_STAINED_GLASS to Material.MAGENTA_STAINED_GLASS,
    Block.LIGHT_BLUE_STAINED_GLASS to Material.LIGHT_BLUE_STAINED_GLASS,
    Block.YELLOW_STAINED_GLASS to Material.YELLOW_STAINED_GLASS,
    Block.LIME_STAINED_GLASS to Material.LIME_STAINED_GLASS,
    Block.PINK_STAINED_GLASS to Material.PINK_STAINED_GLASS,
    Block.GRAY_STAINED_GLASS to Material.GRAY_STAINED_GLASS,
    Block.LIGHT_GRAY_STAINED_GLASS to Material.LIGHT_GRAY_STAINED_GLASS,
    Block.CYAN_STAINED_GLASS to Material.CYAN_STAINED_GLASS,
    Block.PURPLE_STAINED_GLASS to Material.PURPLE_STAINED_GLASS,
    Block.BLUE_STAINED_GLASS to Material.BLUE_STAINED_GLASS,
    Block.BROWN_STAINED_GLASS to Material.BROWN_STAINED_GLASS,
    Block.GREEN_STAINED_GLASS to Material.GREEN_STAINED_GLASS,
    Block.RED_STAINED_GLASS to Material.RED_STAINED_GLASS,
    Block.BLACK_STAINED_GLASS to Material.BLACK_STAINED_GLASS,
    Block.GLOWSTONE to Material.GLOWSTONE,
    Block.ICE to Material.ICE,
    Block.BLUE_ICE to Material.BLUE_ICE,
    Block.PACKED_ICE to Material.PACKED_ICE,
    Block.BOOKSHELF to Material.BOOKSHELF,
    Block.GRASS_BLOCK to Material.GRASS_BLOCK,
    Block.MYCELIUM to Material.MYCELIUM,
    Block.SEA_LANTERN to Material.SEA_LANTERN,
    Block.TURTLE_EGG to Material.TURTLE_EGG,
    Block.SCULK to Material.SCULK,
    Block.SCULK_CATALYST to Material.SCULK_CATALYST,
    Block.SCULK_SENSOR to Material.SCULK_SENSOR,
    Block.SCULK_VEIN to Material.SCULK_VEIN,
)

class SilkTouchModule : OrbitModule("silk-touch") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val player = event.player
            val item = player.getItemInMainHand()
            if (item.isAir) return@addListener

            val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@addListener
            val level = enchantments.level(Enchantment.SILK_TOUCH)
            if (level <= 0) return@addListener

            val block = event.block
            val dropMaterial = SILK_TOUCH_DROPS.entries
                .firstOrNull { (key, _) -> block.compare(key) }
                ?.value ?: return@addListener

            val instance = player.instance ?: return@addListener
            val blockPos = event.blockPosition
            val center = Pos(blockPos.x() + 0.5, blockPos.y() + 0.5, blockPos.z() + 0.5)

            val itemEntity = ItemEntity(ItemStack.of(dropMaterial))
            itemEntity.setPickupDelay(Duration.ofMillis(500))
            itemEntity.setInstance(instance, center)

            itemEntity.scheduler().buildTask { itemEntity.remove() }
                .delay(TaskSchedule.minutes(5))
                .schedule()
        }
    }
}
