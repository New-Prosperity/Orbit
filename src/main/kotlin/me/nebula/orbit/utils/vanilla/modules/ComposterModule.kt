package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.sound.playSound
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import kotlin.random.Random

private val COMPOST_CHANCES = mapOf(
    Material.SHORT_GRASS to 30,
    Material.OAK_LEAVES to 30, Material.SPRUCE_LEAVES to 30,
    Material.BIRCH_LEAVES to 30, Material.JUNGLE_LEAVES to 30,
    Material.ACACIA_LEAVES to 30, Material.DARK_OAK_LEAVES to 30,
    Material.CHERRY_LEAVES to 30, Material.MANGROVE_LEAVES to 30,
    Material.OAK_SAPLING to 30, Material.SPRUCE_SAPLING to 30,
    Material.BIRCH_SAPLING to 30, Material.JUNGLE_SAPLING to 30,
    Material.ACACIA_SAPLING to 30, Material.DARK_OAK_SAPLING to 30,
    Material.CHERRY_SAPLING to 30,
    Material.KELP to 30, Material.SEAGRASS to 30,
    Material.SWEET_BERRIES to 30, Material.GLOW_BERRIES to 30,
    Material.VINE to 50, Material.TALL_GRASS to 50,
    Material.FERN to 50, Material.LARGE_FERN to 50,
    Material.MELON_SLICE to 50, Material.SUGAR_CANE to 50,
    Material.DRIED_KELP to 50, Material.CACTUS to 50,
    Material.DANDELION to 65, Material.POPPY to 65,
    Material.BLUE_ORCHID to 65, Material.ALLIUM to 65,
    Material.AZURE_BLUET to 65, Material.RED_TULIP to 65,
    Material.ORANGE_TULIP to 65, Material.WHITE_TULIP to 65,
    Material.PINK_TULIP to 65, Material.OXEYE_DAISY to 65,
    Material.CORNFLOWER to 65, Material.LILY_OF_THE_VALLEY to 65,
    Material.WITHER_ROSE to 65, Material.SUNFLOWER to 65,
    Material.LILAC to 65, Material.ROSE_BUSH to 65,
    Material.PEONY to 65,
    Material.APPLE to 65, Material.BEETROOT to 65,
    Material.CARROT to 65, Material.COCOA_BEANS to 65,
    Material.POTATO to 65, Material.WHEAT to 65,
    Material.COOKIE to 85, Material.MELON to 85,
    Material.PUMPKIN to 85, Material.BAKED_POTATO to 85,
    Material.BREAD to 85, Material.HAY_BLOCK to 85,
    Material.CAKE to 100, Material.PUMPKIN_PIE to 100,
)

object ComposterModule : VanillaModule {

    override val id = "composter"
    override val description = "Right-click composter with compostable items. Fill 7 layers to produce bone meal."

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-composter")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:composter") return@addListener

            val level = event.block.getProperty("level")?.toIntOrNull() ?: 0

            if (level == 8) {
                event.instance.setBlock(event.blockPosition, event.block.withProperty("level", "0"))
                val itemEntity = ItemEntity(ItemStack.of(Material.BONE_MEAL))
                itemEntity.setPickupDelay(Duration.ofMillis(500))
                itemEntity.setInstance(event.instance, Pos(
                    event.blockPosition.blockX() + 0.5,
                    event.blockPosition.blockY() + 1.0,
                    event.blockPosition.blockZ() + 0.5,
                ))
                event.player.playSound(SoundEvent.BLOCK_COMPOSTER_EMPTY)
                return@addListener
            }

            if (level >= 7) return@addListener

            val item = event.player.itemInMainHand
            val chance = COMPOST_CHANCES[item.material()] ?: return@addListener

            if (event.player.gameMode != GameMode.CREATIVE) {
                event.player.setItemInMainHand(item.consume(1))
            }

            if (Random.nextInt(100) < chance) {
                val newLevel = level + 1
                if (newLevel >= 7) {
                    event.instance.setBlock(event.blockPosition, event.block.withProperty("level", "8"))
                    event.player.playSound(SoundEvent.BLOCK_COMPOSTER_READY)
                } else {
                    event.instance.setBlock(event.blockPosition, event.block.withProperty("level", newLevel.toString()))
                    event.player.playSound(SoundEvent.BLOCK_COMPOSTER_FILL_SUCCESS)
                }
            } else {
                event.player.playSound(SoundEvent.BLOCK_COMPOSTER_FILL)
            }
        }

        return node
    }
}
