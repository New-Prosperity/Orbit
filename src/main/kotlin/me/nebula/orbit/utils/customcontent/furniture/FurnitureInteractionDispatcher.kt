package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.logging.logger
import net.minestom.server.entity.Player

object FurnitureInteractionDispatcher {

    private val logger = logger("FurnitureInteractionDispatcher")

    fun dispatch(
        player: Player,
        furniture: FurnitureInstance,
        definition: FurnitureDefinition,
        interaction: FurnitureInteraction,
    ) {
        when (interaction) {
            is FurnitureInteraction.Seat -> SeatController.onClick(player, furniture, interaction)
            is FurnitureInteraction.OpenClose -> OpenCloseController.onClick(player, furniture, definition, interaction)
            is FurnitureInteraction.LootContainer -> LootContainerController.onClick(player, furniture, interaction)
            is FurnitureInteraction.Custom -> {
                val handler = FurnitureInteractionRegistry[interaction.handlerId]
                if (handler == null) {
                    logger.warn { "Unknown furniture custom handler: ${interaction.handlerId} (furniture=${furniture.definitionId})" }
                    return
                }
                handler.handle(player, furniture)
            }
        }
    }
}
