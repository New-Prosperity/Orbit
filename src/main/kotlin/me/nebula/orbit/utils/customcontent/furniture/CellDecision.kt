package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.orbit.utils.customcontent.block.BlockHitbox

enum class CellCollisionMode { Solid, Soft }

sealed interface CellDecision {

    data object Barrier : CellDecision

    data class Shaped(val hitbox: BlockHitbox) : CellDecision
}
