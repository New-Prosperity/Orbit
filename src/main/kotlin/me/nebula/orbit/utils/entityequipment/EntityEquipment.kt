package me.nebula.orbit.utils.entityequipment

import net.minestom.server.entity.Entity
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.LivingEntity
import net.minestom.server.item.ItemStack

data class EquipmentSnapshot(
    val helmet: ItemStack,
    val chestplate: ItemStack,
    val leggings: ItemStack,
    val boots: ItemStack,
    val mainHand: ItemStack,
    val offHand: ItemStack,
) {
    fun apply(entity: LivingEntity) {
        entity.setEquipment(EquipmentSlot.HELMET, helmet)
        entity.setEquipment(EquipmentSlot.CHESTPLATE, chestplate)
        entity.setEquipment(EquipmentSlot.LEGGINGS, leggings)
        entity.setEquipment(EquipmentSlot.BOOTS, boots)
        entity.setEquipment(EquipmentSlot.MAIN_HAND, mainHand)
        entity.setEquipment(EquipmentSlot.OFF_HAND, offHand)
    }
}

class EquipmentBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var helmet: ItemStack = ItemStack.AIR
    @PublishedApi internal var chestplate: ItemStack = ItemStack.AIR
    @PublishedApi internal var leggings: ItemStack = ItemStack.AIR
    @PublishedApi internal var boots: ItemStack = ItemStack.AIR
    @PublishedApi internal var mainHand: ItemStack = ItemStack.AIR
    @PublishedApi internal var offHand: ItemStack = ItemStack.AIR

    fun helmet(item: ItemStack) { helmet = item }
    fun chestplate(item: ItemStack) { chestplate = item }
    fun leggings(item: ItemStack) { leggings = item }
    fun boots(item: ItemStack) { boots = item }
    fun mainHand(item: ItemStack) { mainHand = item }
    fun offHand(item: ItemStack) { offHand = item }

    @PublishedApi internal fun toSnapshot(): EquipmentSnapshot =
        EquipmentSnapshot(helmet, chestplate, leggings, boots, mainHand, offHand)
}

inline fun Entity.equip(block: EquipmentBuilder.() -> Unit) {
    require(this is LivingEntity) { "Entity must be a LivingEntity to equip" }
    val builder = EquipmentBuilder().apply(block)
    if (builder.helmet != ItemStack.AIR) setEquipment(EquipmentSlot.HELMET, builder.helmet)
    if (builder.chestplate != ItemStack.AIR) setEquipment(EquipmentSlot.CHESTPLATE, builder.chestplate)
    if (builder.leggings != ItemStack.AIR) setEquipment(EquipmentSlot.LEGGINGS, builder.leggings)
    if (builder.boots != ItemStack.AIR) setEquipment(EquipmentSlot.BOOTS, builder.boots)
    if (builder.mainHand != ItemStack.AIR) setEquipment(EquipmentSlot.MAIN_HAND, builder.mainHand)
    if (builder.offHand != ItemStack.AIR) setEquipment(EquipmentSlot.OFF_HAND, builder.offHand)
}

fun Entity.clearEquipment() {
    require(this is LivingEntity) { "Entity must be a LivingEntity to clear equipment" }
    EquipmentSlot.entries.forEach { slot ->
        setEquipment(slot, ItemStack.AIR)
    }
}

fun Entity.getEquipmentSnapshot(): EquipmentSnapshot {
    require(this is LivingEntity) { "Entity must be a LivingEntity to snapshot equipment" }
    return EquipmentSnapshot(
        helmet = getEquipment(EquipmentSlot.HELMET),
        chestplate = getEquipment(EquipmentSlot.CHESTPLATE),
        leggings = getEquipment(EquipmentSlot.LEGGINGS),
        boots = getEquipment(EquipmentSlot.BOOTS),
        mainHand = getEquipment(EquipmentSlot.MAIN_HAND),
        offHand = getEquipment(EquipmentSlot.OFF_HAND),
    )
}
