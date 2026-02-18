package me.nebula.orbit.mechanic.banner

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

private val BANNER_BLOCKS = buildSet {
    val colors = listOf(
        "white", "orange", "magenta", "light_blue", "yellow", "lime",
        "pink", "gray", "light_gray", "cyan", "purple", "blue",
        "brown", "green", "red", "black",
    )
    for (color in colors) {
        add("minecraft:${color}_banner")
        add("minecraft:${color}_wall_banner")
    }
}

private val BANNER_TO_MATERIAL = mapOf(
    "white" to Material.WHITE_BANNER, "orange" to Material.ORANGE_BANNER,
    "magenta" to Material.MAGENTA_BANNER, "light_blue" to Material.LIGHT_BLUE_BANNER,
    "yellow" to Material.YELLOW_BANNER, "lime" to Material.LIME_BANNER,
    "pink" to Material.PINK_BANNER, "gray" to Material.GRAY_BANNER,
    "light_gray" to Material.LIGHT_GRAY_BANNER, "cyan" to Material.CYAN_BANNER,
    "purple" to Material.PURPLE_BANNER, "blue" to Material.BLUE_BANNER,
    "brown" to Material.BROWN_BANNER, "green" to Material.GREEN_BANNER,
    "red" to Material.RED_BANNER, "black" to Material.BLACK_BANNER,
)

class BannerModule : OrbitModule("banner") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in BANNER_BLOCKS) return@addListener

            val facing = event.block.getProperty("rotation") ?: event.block.getProperty("facing")
            if (facing != null) return@addListener
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() !in BANNER_BLOCKS) return@addListener

            val blockName = event.block.name()
            val colorKey = blockName.removePrefix("minecraft:").removeSuffix("_banner").removeSuffix("_wall")
            val material = BANNER_TO_MATERIAL[colorKey] ?: return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val itemEntity = net.minestom.server.entity.ItemEntity(ItemStack.of(material))
            itemEntity.setInstance(instance, net.minestom.server.coordinate.Vec(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5))
            itemEntity.setPickupDelay(java.time.Duration.ofMillis(500))
        }
    }
}
