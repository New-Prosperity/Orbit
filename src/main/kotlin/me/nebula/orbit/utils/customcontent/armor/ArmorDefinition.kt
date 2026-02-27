package me.nebula.orbit.utils.customcontent.armor

import net.minestom.server.coordinate.Vec

data class ArmorRotationComponent(
    val radians: Double,
    val axis: Int,
) {
    companion object {
        const val AXIS_X = 0
        const val AXIS_Y = 1
        const val AXIS_Z = 2
    }
}

data class ArmorRotationLevel(
    val components: List<ArmorRotationComponent>,
    val pivot: Vec,
)

data class ArmorCube(
    val center: Vec,
    val halfSize: Vec,
    val rotationLevels: List<ArmorRotationLevel>,
    val uvFaces: Map<String, ArmorCubeUv>,
    val textureIndex: Int = 0,
) {
    val hasRotation: Boolean get() = rotationLevels.isNotEmpty()
}

data class ArmorCubeUv(
    val u: Float,
    val v: Float,
    val width: Float,
    val height: Float,
    val rotation: Int = 0,
)

data class ParsedArmorPiece(
    val part: ArmorPart,
    val cubes: List<ArmorCube>,
)

data class ArmorTexture(
    val width: Int,
    val height: Int,
    val source: String,
)

data class ParsedArmor(
    val id: String,
    val pieces: List<ParsedArmorPiece>,
    val textures: List<ArmorTexture>,
)

data class RegisteredArmor(
    val id: String,
    val colorId: Int,
    val colorR: Int,
    val colorG: Int,
    val colorB: Int,
    val parsed: ParsedArmor,
) {
    val dyeColor: Int get() = (colorR shl 16) or (colorG shl 8) or colorB
}
