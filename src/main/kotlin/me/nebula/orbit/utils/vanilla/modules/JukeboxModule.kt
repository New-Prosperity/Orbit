package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.time.Duration

private val MUSIC_DISCS = setOf(
    Material.MUSIC_DISC_13, Material.MUSIC_DISC_CAT, Material.MUSIC_DISC_BLOCKS,
    Material.MUSIC_DISC_CHIRP, Material.MUSIC_DISC_FAR, Material.MUSIC_DISC_MALL,
    Material.MUSIC_DISC_MELLOHI, Material.MUSIC_DISC_STAL, Material.MUSIC_DISC_STRAD,
    Material.MUSIC_DISC_WARD, Material.MUSIC_DISC_11, Material.MUSIC_DISC_WAIT,
    Material.MUSIC_DISC_OTHERSIDE, Material.MUSIC_DISC_5, Material.MUSIC_DISC_PIGSTEP,
    Material.MUSIC_DISC_RELIC, Material.MUSIC_DISC_CREATOR, Material.MUSIC_DISC_CREATOR_MUSIC_BOX,
    Material.MUSIC_DISC_PRECIPICE,
)

private val TAG_DISC = Tag.String("nebula:jukebox_disc")

object JukeboxModule : VanillaModule {

    override val id = "jukebox"
    override val description = "Insert and eject music discs from jukeboxes"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-jukebox")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:jukebox") return@addListener

            val hasRecord = event.block.getProperty("has_record") ?: "false"
            val x = event.blockPosition.blockX()
            val y = event.blockPosition.blockY()
            val z = event.blockPosition.blockZ()

            if (hasRecord == "true") {
                val discKey = event.block.getTag(TAG_DISC)
                if (discKey != null) {
                    val discMaterial = Material.fromKey(discKey)
                    if (discMaterial != null) {
                        val itemEntity = ItemEntity(ItemStack.of(discMaterial))
                        itemEntity.setPickupDelay(Duration.ofMillis(500))
                        itemEntity.setInstance(event.instance, Pos(x + 0.5, y + 1.0, z + 0.5))
                    }
                }
                event.instance.setBlock(x, y, z, Block.JUKEBOX.withProperty("has_record", "false"))
                return@addListener
            }

            val item = event.player.itemInMainHand
            if (item.material() !in MUSIC_DISCS) return@addListener

            val newBlock = Block.JUKEBOX.withProperty("has_record", "true")
                .withTag(TAG_DISC, item.material().key().toString())
            event.instance.setBlock(x, y, z, newBlock)
            event.player.setItemInMainHand(item.consume(1))
        }

        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() != "minecraft:jukebox") return@addListener
            val hasRecord = event.block.getProperty("has_record") ?: "false"
            if (hasRecord != "true") return@addListener

            val discKey = event.block.getTag(TAG_DISC) ?: return@addListener
            val discMaterial = Material.fromKey(discKey) ?: return@addListener
            val itemEntity = ItemEntity(ItemStack.of(discMaterial))
            itemEntity.setPickupDelay(Duration.ofMillis(500))
            itemEntity.setInstance(event.instance, Pos(
                event.blockPosition.blockX() + 0.5,
                event.blockPosition.blockY() + 0.5,
                event.blockPosition.blockZ() + 0.5,
            ))
        }

        return node
    }
}
