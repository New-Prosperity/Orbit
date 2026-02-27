package me.nebula.orbit.utils.modelengine.blueprint

import me.nebula.orbit.utils.modelengine.math.Quat
import me.nebula.orbit.utils.modelengine.math.QUAT_IDENTITY
import net.minestom.server.coordinate.Vec
import net.minestom.server.item.ItemStack

data class BlueprintBone(
    val name: String,
    val parentName: String?,
    val childNames: List<String>,
    val offset: Vec,
    val rotation: Quat,
    val rotationEuler: Vec,
    val scale: Vec,
    val modelItem: ItemStack?,
    val behaviors: Map<BoneBehaviorType, Map<String, Any>>,
    val visible: Boolean = true,
    val modelScale: Float = 1f,
)

enum class BoneBehaviorType {
    HEAD,
    MOUNT,
    NAMETAG,
    HELD_ITEM,
    GHOST,
    SEGMENT,
    SUB_HITBOX,
    LEASH,
    PLAYER_LIMB,
}
