package me.nebula.orbit.user

import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.inventory.Inventory

interface Locatable {
    val instance: Instance?
    val position: Pos
    fun teleport(target: Pos)
}

interface GuiCapable {
    fun openInventory(inventory: Inventory)
    fun closeInventory()
}
