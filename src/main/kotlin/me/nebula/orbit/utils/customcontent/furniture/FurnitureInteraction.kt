package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.translation.TranslationKey

sealed interface FurnitureInteraction {

    data class Seat(
        val offsetY: Double = 0.4,
        val yawOffsetDegrees: Float = 0f,
        val dismount: DismountTrigger = DismountTrigger.Sneak,
    ) : FurnitureInteraction

    data class OpenClose(
        val openItemId: String,
        val closedItemId: String,
    ) : FurnitureInteraction

    data class LootContainer(
        val rows: Int = 3,
        val titleKey: TranslationKey? = null,
    ) : FurnitureInteraction {
        init {
            require(rows in 1..6) { "LootContainer rows must be 1..6; got $rows" }
        }
    }

    data class Custom(val handlerId: String) : FurnitureInteraction {
        init { require(handlerId.isNotBlank()) { "Custom interaction handlerId must not be blank" } }
    }
}

enum class DismountTrigger { Sneak, Attack }
