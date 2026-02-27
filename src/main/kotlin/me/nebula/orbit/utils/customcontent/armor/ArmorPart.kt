package me.nebula.orbit.utils.customcontent.armor

sealed class ArmorPart(
    val id: String,
    val bonePrefix: String,
    val stasis: Int,
    val layer: Int,
) {
    data object Helmet : ArmorPart("helmet", "h_", 199, 1)
    data object Chestplate : ArmorPart("chestplate", "c_", 299, 1)
    data object RightArm : ArmorPart("right_arm", "ra_", 399, 1)
    data object LeftArm : ArmorPart("left_arm", "la_", 499, 1)
    data object InnerArmor : ArmorPart("inner_armor", "ia_", 999, 2)
    data object RightLeg : ArmorPart("right_leg", "rl_", 599, 2)
    data object LeftLeg : ArmorPart("left_leg", "ll_", 699, 2)
    data object RightBoot : ArmorPart("right_boot", "rb_", 799, 1)
    data object LeftBoot : ArmorPart("left_boot", "lb_", 899, 1)

    val isLeft: Boolean get() = this is LeftArm || this is LeftLeg || this is LeftBoot

    fun alternateLayerPart(): ArmorPart? = when (this) {
        Chestplate -> InnerArmor
        InnerArmor -> Chestplate
        RightLeg -> RightBoot
        LeftLeg -> LeftBoot
        RightBoot -> RightLeg
        LeftBoot -> LeftLeg
        Helmet, RightArm, LeftArm -> null
    }

    companion object {
        val all: List<ArmorPart> = listOf(
            Helmet, Chestplate, RightArm, LeftArm,
            InnerArmor, RightLeg, LeftLeg,
            RightBoot, LeftBoot,
        )

        private val byPrefix: Map<String, ArmorPart> =
            all.associateBy { it.bonePrefix.lowercase() }

        fun fromBoneName(boneName: String): ArmorPart? {
            val lower = boneName.lowercase()
            return byPrefix.entries.firstOrNull { lower.startsWith(it.key) }?.value
        }
    }
}
