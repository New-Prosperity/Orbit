package me.nebula.orbit.mechanic.smeltingdrop

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import java.time.Duration

private val AUTO_SMELT_TAG: Tag<Boolean> = Tag.Boolean("auto_smelt")

private val SMELT_MAP: Map<Block, Material> = mapOf(
    Block.IRON_ORE to Material.IRON_INGOT,
    Block.DEEPSLATE_IRON_ORE to Material.IRON_INGOT,
    Block.GOLD_ORE to Material.GOLD_INGOT,
    Block.DEEPSLATE_GOLD_ORE to Material.GOLD_INGOT,
    Block.COPPER_ORE to Material.COPPER_INGOT,
    Block.DEEPSLATE_COPPER_ORE to Material.COPPER_INGOT,
    Block.SAND to Material.GLASS,
    Block.RED_SAND to Material.GLASS,
    Block.COBBLESTONE to Material.STONE,
    Block.ANCIENT_DEBRIS to Material.NETHERITE_SCRAP,
    Block.RAW_IRON_BLOCK to Material.IRON_BLOCK,
    Block.RAW_GOLD_BLOCK to Material.GOLD_BLOCK,
)

class SmeltingDropModule : OrbitModule("smelting-drop") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val tool = event.player.getItemInMainHand()
            if (tool.getTag(AUTO_SMELT_TAG) != true) return@addListener

            val block = event.block
            val smeltedMaterial = SMELT_MAP.entries.firstOrNull { (source, _) ->
                block.name() == source.name()
            }?.value ?: return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val center = Pos(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5)

            val itemEntity = ItemEntity(ItemStack.of(smeltedMaterial))
            itemEntity.setPickupDelay(Duration.ofMillis(500))
            itemEntity.setInstance(instance, center)

            itemEntity.scheduler().buildTask { itemEntity.remove() }
                .delay(TaskSchedule.minutes(5))
                .schedule()
        }
    }
}
