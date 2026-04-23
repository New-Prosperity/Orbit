package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.minestom.server.entity.Player
import net.minestom.server.sound.SoundEvent

object OpenCloseController {

    private val logger = logger("OpenCloseController")

    fun onClick(
        player: Player,
        furniture: FurnitureInstance,
        definition: FurnitureDefinition,
        config: FurnitureInteraction.OpenClose,
    ) {
        val currentlyOpen = FurnitureInstanceState.isOpen(furniture.uuid)
        val nextOpen = !currentlyOpen
        val targetItemId = if (nextOpen) config.openItemId else config.closedItemId
        val customItem = CustomItemRegistry[targetItemId]
        if (customItem == null) {
            logger.warn { "OpenClose target item not found: $targetItemId (furniture=${definition.id})" }
            return
        }
        FurnitureInstanceState.setOpen(furniture.uuid, nextOpen)
        FurnitureDisplaySpawner.setItem(furniture.instance, furniture.displayEntityId, customItem.createStack(1))
        FurnitureLightingController.onToggled(furniture.instance, furniture, definition)
        val soundEvent = if (nextOpen) SoundEvent.BLOCK_WOODEN_DOOR_OPEN else SoundEvent.BLOCK_WOODEN_DOOR_CLOSE
        furniture.instance.playSound(
            Sound.sound(Key.key(soundEvent.key().asString()), Sound.Source.BLOCK, 0.8f, 1f),
            furniture.anchorX + 0.5, furniture.anchorY + 0.5, furniture.anchorZ + 0.5,
        )
    }
}
