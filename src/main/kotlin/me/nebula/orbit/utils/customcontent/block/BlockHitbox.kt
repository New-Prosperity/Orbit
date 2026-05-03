package me.nebula.orbit.utils.customcontent.block

sealed class BlockHitbox(val name: String) {

    data object Full : BlockHitbox("full")
    data object Slab : BlockHitbox("slab")
    data object Stair : BlockHitbox("stair")
    data object Thin : BlockHitbox("thin")
    data object Transparent : BlockHitbox("transparent")
    data object Wall : BlockHitbox("wall")
    data object Fence : BlockHitbox("fence")
    data object Trapdoor : BlockHitbox("trapdoor")

    companion object {

        fun fromStringOrNull(value: String): BlockHitbox? = when (value.lowercase()) {
            "full" -> Full
            "slab" -> Slab
            "stair" -> Stair
            "thin" -> Thin
            "transparent" -> Transparent
            "wall" -> Wall
            "fence" -> Fence
            "trapdoor" -> Trapdoor
            else -> null
        }

        fun fromString(value: String): BlockHitbox =
            fromStringOrNull(value) ?: error("Unknown hitbox type: $value")
    }
}
