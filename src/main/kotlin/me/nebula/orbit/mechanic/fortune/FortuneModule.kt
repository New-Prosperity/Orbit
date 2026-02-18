package me.nebula.orbit.mechanic.fortune

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
import java.util.concurrent.ThreadLocalRandom

private val FORTUNE_ORE_DROPS: Map<Block, Material> = mapOf(
    Block.COAL_ORE to Material.COAL,
    Block.DEEPSLATE_COAL_ORE to Material.COAL,
    Block.DIAMOND_ORE to Material.DIAMOND,
    Block.DEEPSLATE_DIAMOND_ORE to Material.DIAMOND,
    Block.EMERALD_ORE to Material.EMERALD,
    Block.DEEPSLATE_EMERALD_ORE to Material.EMERALD,
    Block.LAPIS_ORE to Material.LAPIS_LAZULI,
    Block.DEEPSLATE_LAPIS_ORE to Material.LAPIS_LAZULI,
    Block.REDSTONE_ORE to Material.REDSTONE,
    Block.DEEPSLATE_REDSTONE_ORE to Material.REDSTONE,
    Block.COPPER_ORE to Material.RAW_COPPER,
    Block.DEEPSLATE_COPPER_ORE to Material.RAW_COPPER,
    Block.IRON_ORE to Material.RAW_IRON,
    Block.DEEPSLATE_IRON_ORE to Material.RAW_IRON,
    Block.GOLD_ORE to Material.RAW_GOLD,
    Block.DEEPSLATE_GOLD_ORE to Material.RAW_GOLD,
    Block.NETHER_GOLD_ORE to Material.GOLD_NUGGET,
    Block.NETHER_QUARTZ_ORE to Material.QUARTZ,
)

class FortuneModule : OrbitModule("fortune") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val player = event.player
            val item = player.getItemInMainHand()
            if (item.isAir) return@addListener

            val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@addListener
            val level = enchantments.level(Enchantment.FORTUNE)
            if (level <= 0) return@addListener

            val block = event.block
            val dropMaterial = FORTUNE_ORE_DROPS.entries
                .firstOrNull { (key, _) -> block.compare(key) }
                ?.value ?: return@addListener

            val instance = player.instance ?: return@addListener
            val blockPos = event.blockPosition
            val center = Pos(blockPos.x() + 0.5, blockPos.y() + 0.5, blockPos.z() + 0.5)

            val random = ThreadLocalRandom.current()
            val bonusCount = random.nextInt(0, level + 1)
            if (bonusCount <= 0) return@addListener

            repeat(bonusCount) {
                val itemEntity = ItemEntity(ItemStack.of(dropMaterial))
                itemEntity.setPickupDelay(Duration.ofMillis(500))
                itemEntity.setInstance(instance, center)

                itemEntity.scheduler().buildTask { itemEntity.remove() }
                    .delay(TaskSchedule.minutes(5))
                    .schedule()
            }
        }
    }
}
