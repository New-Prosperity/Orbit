package me.nebula.orbit.mechanic.netheritedamage

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.item.Material

private val NETHERITE_ARMOR = setOf(
    Material.NETHERITE_HELMET,
    Material.NETHERITE_CHESTPLATE,
    Material.NETHERITE_LEGGINGS,
    Material.NETHERITE_BOOTS,
)

class NetheriteDamageModule : OrbitModule("netherite-damage") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            if (event.damage !is EntityDamage) return@addListener

            val pieces = countNetheritePieces(player)
            if (pieces <= 0) return@addListener

            val reduction = pieces * 0.10
            val currentVelocity = player.velocity
            val reduced = Vec(
                currentVelocity.x() * (1.0 - reduction),
                currentVelocity.y(),
                currentVelocity.z() * (1.0 - reduction),
            )
            player.velocity = reduced
        }
    }

    private fun countNetheritePieces(player: Player): Int {
        var count = 0
        if (player.helmet.material() in NETHERITE_ARMOR) count++
        if (player.chestplate.material() in NETHERITE_ARMOR) count++
        if (player.leggings.material() in NETHERITE_ARMOR) count++
        if (player.boots.material() in NETHERITE_ARMOR) count++
        return count
    }
}
