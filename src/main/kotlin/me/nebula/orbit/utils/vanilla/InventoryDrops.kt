package me.nebula.orbit.utils.vanilla

import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.ItemEntity
import net.minestom.server.instance.Instance
import net.minestom.server.inventory.Inventory
import java.time.Duration
import kotlin.random.Random

fun dropInventoryContents(instance: Instance, inventory: Inventory, x: Int, y: Int, z: Int) {
    val center = Pos(x + 0.5, y + 0.5, z + 0.5)
    for (i in 0 until inventory.size) {
        val stack = inventory.getItemStack(i)
        if (stack.isAir) continue
        val entity = ItemEntity(stack)
        entity.setPickupDelay(Duration.ofMillis(500))
        entity.velocity = Vec(
            (Random.nextDouble() - 0.5) * 2,
            Random.nextDouble() * 3 + 1,
            (Random.nextDouble() - 0.5) * 2,
        )
        entity.setInstance(instance, center)
    }
}
