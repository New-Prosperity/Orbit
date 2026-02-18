package me.nebula.orbit.mechanic.wax

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

private val WAX_MAP: Map<String, String> = mapOf(
    "minecraft:copper_block" to "minecraft:waxed_copper_block",
    "minecraft:exposed_copper" to "minecraft:waxed_exposed_copper",
    "minecraft:weathered_copper" to "minecraft:waxed_weathered_copper",
    "minecraft:oxidized_copper" to "minecraft:waxed_oxidized_copper",
    "minecraft:cut_copper" to "minecraft:waxed_cut_copper",
    "minecraft:exposed_cut_copper" to "minecraft:waxed_exposed_cut_copper",
    "minecraft:weathered_cut_copper" to "minecraft:waxed_weathered_cut_copper",
    "minecraft:oxidized_cut_copper" to "minecraft:waxed_oxidized_cut_copper",
    "minecraft:cut_copper_stairs" to "minecraft:waxed_cut_copper_stairs",
    "minecraft:exposed_cut_copper_stairs" to "minecraft:waxed_exposed_cut_copper_stairs",
    "minecraft:weathered_cut_copper_stairs" to "minecraft:waxed_weathered_cut_copper_stairs",
    "minecraft:oxidized_cut_copper_stairs" to "minecraft:waxed_oxidized_cut_copper_stairs",
    "minecraft:cut_copper_slab" to "minecraft:waxed_cut_copper_slab",
    "minecraft:exposed_cut_copper_slab" to "minecraft:waxed_exposed_cut_copper_slab",
    "minecraft:weathered_cut_copper_slab" to "minecraft:waxed_weathered_cut_copper_slab",
    "minecraft:oxidized_cut_copper_slab" to "minecraft:waxed_oxidized_cut_copper_slab",
    "minecraft:copper_door" to "minecraft:waxed_copper_door",
    "minecraft:exposed_copper_door" to "minecraft:waxed_exposed_copper_door",
    "minecraft:weathered_copper_door" to "minecraft:waxed_weathered_copper_door",
    "minecraft:oxidized_copper_door" to "minecraft:waxed_oxidized_copper_door",
    "minecraft:copper_trapdoor" to "minecraft:waxed_copper_trapdoor",
    "minecraft:exposed_copper_trapdoor" to "minecraft:waxed_exposed_copper_trapdoor",
    "minecraft:weathered_copper_trapdoor" to "minecraft:waxed_weathered_copper_trapdoor",
    "minecraft:oxidized_copper_trapdoor" to "minecraft:waxed_oxidized_copper_trapdoor",
    "minecraft:copper_grate" to "minecraft:waxed_copper_grate",
    "minecraft:exposed_copper_grate" to "minecraft:waxed_exposed_copper_grate",
    "minecraft:weathered_copper_grate" to "minecraft:waxed_weathered_copper_grate",
    "minecraft:oxidized_copper_grate" to "minecraft:waxed_oxidized_copper_grate",
    "minecraft:copper_bulb" to "minecraft:waxed_copper_bulb",
    "minecraft:exposed_copper_bulb" to "minecraft:waxed_exposed_copper_bulb",
    "minecraft:weathered_copper_bulb" to "minecraft:waxed_weathered_copper_bulb",
    "minecraft:oxidized_copper_bulb" to "minecraft:waxed_oxidized_copper_bulb",
    "minecraft:chiseled_copper" to "minecraft:waxed_chiseled_copper",
    "minecraft:exposed_chiseled_copper" to "minecraft:waxed_exposed_chiseled_copper",
    "minecraft:weathered_chiseled_copper" to "minecraft:waxed_weathered_chiseled_copper",
    "minecraft:oxidized_chiseled_copper" to "minecraft:waxed_oxidized_chiseled_copper",
)

class WaxModule : OrbitModule("wax") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val held = event.player.getItemInMainHand()
            if (held.material() != Material.HONEYCOMB) return@addListener

            val blockName = event.block.name()
            val waxed = WAX_MAP[blockName] ?: return@addListener
            val resolved = Block.fromKey(waxed) ?: return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val properties = event.block.properties()
            var result = resolved
            for ((key, value) in properties) {
                runCatching { result = result.withProperty(key, value) }
            }

            instance.setBlock(pos, result)

            val newCount = held.amount() - 1
            event.player.setItemInMainHand(
                if (newCount <= 0) ItemStack.AIR else held.withAmount(newCount)
            )

            instance.playSound(
                Sound.sound(SoundEvent.ITEM_HONEYCOMB_WAX_ON.key(), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }
}
