package me.nebula.orbit.utils.inventoryserializer

import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object InventorySerializer {

    private const val INVENTORY_SIZE = 46
    private const val FORMAT_VERSION: Int = 1

    fun serialize(player: Player): ByteArray {
        val buffer = ByteArrayOutputStream()
        val output = DataOutputStream(buffer)
        output.writeInt(FORMAT_VERSION)

        val nonAirSlots = mutableListOf<Pair<Int, ItemStack>>()
        for (slot in 0 until INVENTORY_SIZE) {
            val item = player.inventory.getItemStack(slot)
            if (!item.isAir) {
                nonAirSlots.add(slot to item)
            }
        }

        output.writeInt(nonAirSlots.size)
        for ((slot, item) in nonAirSlots) {
            output.writeInt(slot)
            output.writeUTF(item.material().key().asString())
            output.writeInt(item.amount())
        }

        output.flush()
        return buffer.toByteArray()
    }

    fun deserialize(player: Player, data: ByteArray) {
        player.inventory.clear()
        val input = DataInputStream(ByteArrayInputStream(data))
        val version = input.readInt()
        require(version == FORMAT_VERSION) { "Unsupported serialization format version: $version" }

        val count = input.readInt()
        repeat(count) {
            val slot = input.readInt()
            val materialName = input.readUTF()
            val amount = input.readInt()
            val material = Material.fromKey(materialName) ?: return@repeat
            player.inventory.setItemStack(slot, ItemStack.of(material, amount))
        }
    }
}

fun Player.serializeInventory(): ByteArray = InventorySerializer.serialize(this)

fun Player.deserializeInventory(data: ByteArray) = InventorySerializer.deserialize(this, data)
