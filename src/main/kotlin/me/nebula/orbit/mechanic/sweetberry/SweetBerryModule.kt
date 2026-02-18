package me.nebula.orbit.mechanic.sweetberry

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

class SweetBerryModule : OrbitModule("sweet-berry") {

    private val lastDamageTag = Tag.Long("mechanic:sweet_berry:last_damage")

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            if (block.name() != "minecraft:sweet_berry_bush") return@addListener

            val age = block.getProperty("age")?.toIntOrNull() ?: 0
            if (age < 2) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val berryCount = if (age == 3) 3 else 2

            event.player.inventory.addItemStack(ItemStack.of(Material.SWEET_BERRIES, berryCount))
            instance.setBlock(pos, block.withProperty("age", "1"))
        }

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val block = instance.getBlock(player.position)

            if (block.name() != "minecraft:sweet_berry_bush") return@addListener

            val age = block.getProperty("age")?.toIntOrNull() ?: 0
            if (age < 2) return@addListener

            val now = System.currentTimeMillis()
            val lastDamage = player.getTag(lastDamageTag) ?: 0L
            if (now - lastDamage < 500L) return@addListener

            player.damage(Damage(DamageType.SWEET_BERRY_BUSH, null, null, null, 1f))
            player.setTag(lastDamageTag, now)
        }
    }
}
