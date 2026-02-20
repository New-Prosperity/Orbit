package me.nebula.orbit.utils.customcontent.item

import java.util.concurrent.ConcurrentHashMap

object CustomItemRegistry {

    private val byId = ConcurrentHashMap<String, CustomItem>()
    private val byCustomModelData = ConcurrentHashMap<Int, CustomItem>()

    fun register(item: CustomItem) {
        require(!byId.containsKey(item.id)) { "Custom item already registered: ${item.id}" }
        byId[item.id] = item
        byCustomModelData[item.customModelDataId] = item
    }

    operator fun get(id: String): CustomItem? = byId[id]

    fun require(id: String): CustomItem =
        byId[id] ?: error("Unknown custom item: $id")

    fun byCustomModelData(id: Int): CustomItem? = byCustomModelData[id]

    fun all(): Collection<CustomItem> = byId.values

    fun isEmpty(): Boolean = byId.isEmpty()

    fun clear() {
        byId.clear()
        byCustomModelData.clear()
    }
}
