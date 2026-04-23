package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import java.util.concurrent.ConcurrentHashMap

object FurnitureRegistry {

    private val byId = ConcurrentHashMap<String, FurnitureDefinition>()
    private val byItemId = ConcurrentHashMap<String, FurnitureDefinition>()

    fun register(definition: FurnitureDefinition) {
        require(!byId.containsKey(definition.id)) {
            "Furniture already registered: ${definition.id}"
        }
        require(!byItemId.containsKey(definition.itemId)) {
            "Item '${definition.itemId}' is already bound to furniture '${byItemId[definition.itemId]?.id}'"
        }
        byId[definition.id] = definition
        byItemId[definition.itemId] = definition
    }

    operator fun get(id: String): FurnitureDefinition? = byId[id]

    fun require(id: String): FurnitureDefinition =
        byId[id] ?: error("Unknown furniture: $id")

    fun fromItemId(itemId: String): FurnitureDefinition? = byItemId[itemId]

    fun all(): Collection<FurnitureDefinition> = byId.values

    fun isEmpty(): Boolean = byId.isEmpty()

    fun clear() {
        byId.clear()
        byItemId.clear()
    }

    fun validateAgainstItemRegistry() {
        val missing = byItemId.keys.filter { CustomItemRegistry[it] == null }
        require(missing.isEmpty()) {
            "Furniture references unknown custom items: $missing"
        }
    }
}
