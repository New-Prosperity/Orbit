package me.nebula.orbit.utils.customcontent.furniture

import net.minestom.server.inventory.Inventory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object FurnitureInstanceState {

    private data class Entry(
        @Volatile var openCloseOpen: Boolean = false,
        @Volatile var inventory: Inventory? = null,
        @Volatile var pendingInventoryBase64: String? = null,
    )

    private val entries = ConcurrentHashMap<UUID, Entry>()

    private fun entry(uuid: UUID): Entry = entries.computeIfAbsent(uuid) { Entry() }

    fun isOpen(uuid: UUID): Boolean = entries[uuid]?.openCloseOpen ?: false

    fun setOpen(uuid: UUID, open: Boolean) { entry(uuid).openCloseOpen = open }

    fun inventoryOf(uuid: UUID): Inventory? = entries[uuid]?.inventory

    fun setInventory(uuid: UUID, inventory: Inventory) {
        val entry = entry(uuid)
        entry.inventory = inventory
        val pending = entry.pendingInventoryBase64
        if (pending != null) {
            entry.pendingInventoryBase64 = null
            FurniturePersistence.hydrateInventory(inventory, pending)
        }
    }

    fun setPendingInventory(uuid: UUID, base64: String) {
        entry(uuid).pendingInventoryBase64 = base64
    }

    fun remove(uuid: UUID) {
        val removed = entries.remove(uuid) ?: return
        removed.inventory?.let { inv -> inv.viewers.toList().forEach { it.closeInventory() } }
    }

    fun clear() {
        entries.values.forEach { it.inventory?.viewers?.toList()?.forEach { v -> v.closeInventory() } }
        entries.clear()
    }
}
