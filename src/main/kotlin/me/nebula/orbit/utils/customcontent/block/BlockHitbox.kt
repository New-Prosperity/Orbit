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

        private val byName = mapOf(
            "full" to Full, "slab" to Slab, "stair" to Stair, "thin" to Thin,
            "transparent" to Transparent, "wall" to Wall, "fence" to Fence, "trapdoor" to Trapdoor,
        )

        fun fromString(value: String): BlockHitbox =
            byName[value.lowercase()] ?: error("Unknown hitbox type: $value")
    }
}
