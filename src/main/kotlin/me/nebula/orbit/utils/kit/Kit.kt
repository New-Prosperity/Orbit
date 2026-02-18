package me.nebula.orbit.utils.kit

import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.concurrent.ConcurrentHashMap

data class Kit(
    val name: String,
    val items: Map<Int, ItemStack>,
    val armor: Map<EquipmentSlot, ItemStack>,
    val offhand: ItemStack?,
) {

    fun apply(player: Player) {
        player.inventory.clear()
        items.forEach { (slot, item) -> player.inventory.setItemStack(slot, item) }
        armor.forEach { (slot, item) -> player.setEquipment(slot, item) }
        offhand?.let { player.inventory.setItemStack(40, it) }
    }

    fun applyKeepExisting(player: Player) {
        items.forEach { (slot, item) ->
            if (player.inventory.getItemStack(slot).isAir) {
                player.inventory.setItemStack(slot, item)
            }
        }
        armor.forEach { (slot, item) ->
            if (player.getEquipment(slot).isAir) {
                player.setEquipment(slot, item)
            }
        }
    }
}

class KitBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal val items = mutableMapOf<Int, ItemStack>()
    @PublishedApi internal val armor = mutableMapOf<EquipmentSlot, ItemStack>()
    @PublishedApi internal var offhand: ItemStack? = null

    fun item(slot: Int, item: ItemStack) { items[slot] = item }
    fun item(slot: Int, material: Material, amount: Int = 1) { items[slot] = ItemStack.of(material, amount) }

    fun helmet(item: ItemStack) { armor[EquipmentSlot.HELMET] = item }
    fun helmet(material: Material) { armor[EquipmentSlot.HELMET] = ItemStack.of(material) }
    fun chestplate(item: ItemStack) { armor[EquipmentSlot.CHESTPLATE] = item }
    fun chestplate(material: Material) { armor[EquipmentSlot.CHESTPLATE] = ItemStack.of(material) }
    fun leggings(item: ItemStack) { armor[EquipmentSlot.LEGGINGS] = item }
    fun leggings(material: Material) { armor[EquipmentSlot.LEGGINGS] = ItemStack.of(material) }
    fun boots(item: ItemStack) { armor[EquipmentSlot.BOOTS] = item }
    fun boots(material: Material) { armor[EquipmentSlot.BOOTS] = ItemStack.of(material) }

    fun offhand(item: ItemStack) { offhand = item }
    fun offhand(material: Material) { offhand = ItemStack.of(material) }

    @PublishedApi internal fun build(): Kit = Kit(
        name = name,
        items = items.toMap(),
        armor = armor.toMap(),
        offhand = offhand,
    )
}

inline fun kit(name: String, block: KitBuilder.() -> Unit): Kit =
    KitBuilder(name).apply(block).build()

fun Player.applyKit(kit: Kit) = kit.apply(this)

object KitRegistry {

    private val kits = ConcurrentHashMap<String, Kit>()

    fun register(kit: Kit) {
        require(!kits.containsKey(kit.name)) { "Kit '${kit.name}' already registered" }
        kits[kit.name] = kit
    }

    fun unregister(name: String): Kit? = kits.remove(name)

    operator fun get(name: String): Kit? = kits[name]
    fun require(name: String): Kit = requireNotNull(kits[name]) { "Kit '$name' not found" }
    fun all(): Map<String, Kit> = kits.toMap()
    fun names(): Set<String> = kits.keys.toSet()
    fun clear() = kits.clear()
}
