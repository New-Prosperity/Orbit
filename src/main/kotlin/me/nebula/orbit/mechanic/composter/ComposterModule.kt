package me.nebula.orbit.mechanic.composter

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.ItemEntity
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.TaskSchedule
import java.time.Duration

private val COMPOSTABLE_30 = setOf(
    Material.KELP, Material.BEETROOT_SEEDS, Material.DRIED_KELP, Material.SHORT_GRASS,
    Material.HANGING_ROOTS, Material.MANGROVE_ROOTS, Material.SEAGRASS,
    Material.SWEET_BERRIES, Material.GLOW_BERRIES,
)

private val COMPOSTABLE_50 = setOf(
    Material.CACTUS, Material.MELON_SLICE, Material.SUGAR_CANE,
    Material.TALL_GRASS, Material.VINE, Material.LILY_PAD,
    Material.PUMPKIN_SEEDS, Material.MELON_SEEDS, Material.WHEAT_SEEDS,
    Material.COCOA_BEANS, Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS,
)

private val COMPOSTABLE_65 = setOf(
    Material.APPLE, Material.BEETROOT, Material.CARROT, Material.POTATO,
    Material.WHEAT, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM,
    Material.FERN, Material.LARGE_FERN,
)

private val COMPOSTABLE_85 = setOf(
    Material.BAKED_POTATO, Material.BREAD, Material.COOKIE, Material.HAY_BLOCK,
    Material.PUMPKIN, Material.CARVED_PUMPKIN, Material.MELON,
)

private val COMPOSTABLE_100 = setOf(
    Material.CAKE, Material.PUMPKIN_PIE,
)

class ComposterModule : OrbitModule("composter") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:composter") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val block = event.block
            val level = block.getProperty("level")?.toIntOrNull() ?: 0

            if (level == 8) {
                instance.setBlock(pos, block.withProperty("level", "0"))
                val boneMeal = ItemEntity(ItemStack.of(Material.BONE_MEAL))
                boneMeal.setPickupDelay(Duration.ofMillis(500))
                boneMeal.setInstance(instance, Pos(pos.x() + 0.5, pos.y() + 1.0, pos.z() + 0.5))
                boneMeal.scheduler().buildTask { boneMeal.remove() }
                    .delay(TaskSchedule.minutes(5)).schedule()

                instance.playSound(
                    Sound.sound(SoundEvent.BLOCK_COMPOSTER_EMPTY.key(), Sound.Source.BLOCK, 1f, 1f),
                    pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
                )
                return@addListener
            }

            if (level >= 7) return@addListener

            val held = event.player.getItemInMainHand()
            val chance = compostChance(held.material())
            if (chance <= 0f) return@addListener

            val slot = event.player.heldSlot.toInt()
            if (held.amount() > 1) {
                event.player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
            } else {
                event.player.inventory.setItemStack(slot, ItemStack.AIR)
            }

            if (kotlin.random.Random.nextFloat() < chance) {
                val newLevel = (level + 1).coerceAtMost(7)
                instance.setBlock(pos, block.withProperty("level", newLevel.toString()))

                instance.playSound(
                    Sound.sound(SoundEvent.BLOCK_COMPOSTER_FILL_SUCCESS.key(), Sound.Source.BLOCK, 1f, 1f),
                    pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
                )

                if (newLevel == 7) {
                    instance.setBlock(pos, block.withProperty("level", "8"))
                }
            } else {
                instance.playSound(
                    Sound.sound(SoundEvent.BLOCK_COMPOSTER_FILL.key(), Sound.Source.BLOCK, 1f, 1f),
                    pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
                )
            }
        }
    }

    private fun compostChance(material: Material): Float = when (material) {
        in COMPOSTABLE_30 -> 0.3f
        in COMPOSTABLE_50 -> 0.5f
        in COMPOSTABLE_65 -> 0.65f
        in COMPOSTABLE_85 -> 0.85f
        in COMPOSTABLE_100 -> 1.0f
        else -> 0f
    }
}
