package me.nebula.orbit.utils.statue

import me.nebula.orbit.utils.modelengine.generator.BbElement
import me.nebula.orbit.utils.modelengine.generator.BbFace
import me.nebula.orbit.utils.modelengine.generator.BbGroup
import me.nebula.orbit.utils.modelengine.generator.BbGroupChild
import me.nebula.orbit.utils.modelengine.generator.BbMeta
import me.nebula.orbit.utils.modelengine.generator.BbResolution
import me.nebula.orbit.utils.modelengine.generator.BbTexture
import me.nebula.orbit.utils.modelengine.generator.BlockbenchModel
import me.nebula.orbit.utils.statue.PlayerModelGenerator.BLUEPRINT_NAME
import net.minestom.server.coordinate.Vec

private const val TEXTURE_WIDTH = 64
private const val TEXTURE_HEIGHT = 64

internal fun buildPlayerModel(): BlockbenchModel {
    var elementIdx = 0
    fun nextUuid(): String = "elem_${elementIdx++}"

    val elements = mutableListOf<BbElement>()
    val groups = mutableListOf<BbGroup>()

    val texture = BbTexture(
        id = 0,
        name = "skin",
        width = TEXTURE_WIDTH,
        height = TEXTURE_HEIGHT,
        source = "",
    )

    fun face(u1: Int, v1: Int, u2: Int, v2: Int): BbFace = BbFace(
        uv = floatArrayOf(u1.toFloat(), v1.toFloat(), u2.toFloat(), v2.toFloat()),
        texture = 0,
    )

    fun cube(
        name: String,
        from: Vec,
        to: Vec,
        uvMap: Map<String, BbFace>,
    ): BbElement {
        nextUuid()
        val elem = BbElement(
            uuid = "elem_${elementIdx - 1}",
            name = name,
            from = from,
            to = to,
            origin = Vec.ZERO,
            rotation = Vec.ZERO,
            inflate = 0f,
            faces = uvMap,
            visibility = true,
        )
        elements += elem
        return elem
    }

    fun overlayCube(
        name: String,
        from: Vec,
        to: Vec,
        uvMap: Map<String, BbFace>,
    ): BbElement {
        nextUuid()
        val elem = BbElement(
            uuid = "elem_${elementIdx - 1}",
            name = name,
            from = from,
            to = to,
            origin = Vec.ZERO,
            rotation = Vec.ZERO,
            inflate = 0f,
            faces = uvMap,
            visibility = true,
        )
        elements += elem
        return elem
    }

    val headCube = cube(
        "head_cube",
        Vec(-4.0, 24.0, -4.0), Vec(4.0, 32.0, 4.0),
        mapOf(
            "north" to face(8, 8, 16, 16),
            "south" to face(24, 8, 32, 16),
            "east" to face(0, 8, 8, 16),
            "west" to face(16, 8, 24, 16),
            "up" to face(8, 0, 16, 8),
            "down" to face(16, 0, 24, 8),
        ),
    )

    val hatCube = overlayCube(
        "hat_cube",
        Vec(-4.5, 23.5, -4.5), Vec(4.5, 32.5, 4.5),
        mapOf(
            "north" to face(40, 8, 48, 16),
            "south" to face(56, 8, 64, 16),
            "east" to face(32, 8, 40, 16),
            "west" to face(48, 8, 56, 16),
            "up" to face(40, 0, 48, 8),
            "down" to face(48, 0, 56, 8),
        ),
    )

    val bodyCube = cube(
        "body_cube",
        Vec(-4.0, 12.0, -2.0), Vec(4.0, 24.0, 2.0),
        mapOf(
            "north" to face(20, 20, 28, 32),
            "south" to face(32, 20, 40, 32),
            "east" to face(16, 20, 20, 32),
            "west" to face(28, 20, 32, 32),
            "up" to face(20, 16, 28, 20),
            "down" to face(28, 16, 36, 20),
        ),
    )

    val jacketCube = overlayCube(
        "jacket_cube",
        Vec(-4.5, 11.5, -2.5), Vec(4.5, 24.5, 2.5),
        mapOf(
            "north" to face(20, 36, 28, 48),
            "south" to face(32, 36, 40, 48),
            "east" to face(16, 36, 20, 48),
            "west" to face(28, 36, 32, 48),
            "up" to face(20, 32, 28, 36),
            "down" to face(28, 32, 36, 36),
        ),
    )

    val rightArmCube = cube(
        "right_arm_cube",
        Vec(-8.0, 12.0, -2.0), Vec(-4.0, 24.0, 2.0),
        mapOf(
            "north" to face(44, 20, 48, 32),
            "south" to face(52, 20, 56, 32),
            "east" to face(40, 20, 44, 32),
            "west" to face(48, 20, 52, 32),
            "up" to face(44, 16, 48, 20),
            "down" to face(48, 16, 52, 20),
        ),
    )

    val rightSleeveCube = overlayCube(
        "right_sleeve_cube",
        Vec(-8.5, 11.5, -2.5), Vec(-3.5, 24.5, 2.5),
        mapOf(
            "north" to face(44, 36, 48, 48),
            "south" to face(52, 36, 56, 48),
            "east" to face(40, 36, 44, 48),
            "west" to face(48, 36, 52, 48),
            "up" to face(44, 32, 48, 36),
            "down" to face(48, 32, 52, 36),
        ),
    )

    val leftArmCube = cube(
        "left_arm_cube",
        Vec(4.0, 12.0, -2.0), Vec(8.0, 24.0, 2.0),
        mapOf(
            "north" to face(36, 52, 40, 64),
            "south" to face(44, 52, 48, 64),
            "east" to face(32, 52, 36, 64),
            "west" to face(40, 52, 44, 64),
            "up" to face(36, 48, 40, 52),
            "down" to face(40, 48, 44, 52),
        ),
    )

    val leftSleeveCube = overlayCube(
        "left_sleeve_cube",
        Vec(3.5, 11.5, -2.5), Vec(8.5, 24.5, 2.5),
        mapOf(
            "north" to face(52, 52, 56, 64),
            "south" to face(60, 52, 64, 64),
            "east" to face(48, 52, 52, 64),
            "west" to face(56, 52, 60, 64),
            "up" to face(52, 48, 56, 52),
            "down" to face(56, 48, 60, 52),
        ),
    )

    val rightLegCube = cube(
        "right_leg_cube",
        Vec(-4.0, 0.0, -2.0), Vec(0.0, 12.0, 2.0),
        mapOf(
            "north" to face(4, 20, 8, 32),
            "south" to face(12, 20, 16, 32),
            "east" to face(0, 20, 4, 32),
            "west" to face(8, 20, 12, 32),
            "up" to face(4, 16, 8, 20),
            "down" to face(8, 16, 12, 20),
        ),
    )

    val rightPantsCube = overlayCube(
        "right_pants_cube",
        Vec(-4.5, -0.5, -2.5), Vec(0.5, 12.5, 2.5),
        mapOf(
            "north" to face(4, 36, 8, 48),
            "south" to face(12, 36, 16, 48),
            "east" to face(0, 36, 4, 48),
            "west" to face(8, 36, 12, 48),
            "up" to face(4, 32, 8, 36),
            "down" to face(8, 32, 12, 36),
        ),
    )

    val leftLegCube = cube(
        "left_leg_cube",
        Vec(0.0, 0.0, -2.0), Vec(4.0, 12.0, 2.0),
        mapOf(
            "north" to face(20, 52, 24, 64),
            "south" to face(28, 52, 32, 64),
            "east" to face(16, 52, 20, 64),
            "west" to face(24, 52, 28, 64),
            "up" to face(20, 48, 24, 52),
            "down" to face(24, 48, 28, 52),
        ),
    )

    val leftPantsCube = overlayCube(
        "left_pants_cube",
        Vec(-0.5, -0.5, -2.5), Vec(4.5, 12.5, 2.5),
        mapOf(
            "north" to face(4, 52, 8, 64),
            "south" to face(12, 52, 16, 64),
            "east" to face(0, 52, 4, 64),
            "west" to face(8, 52, 12, 64),
            "up" to face(4, 48, 8, 52),
            "down" to face(8, 48, 12, 52),
        ),
    )

    val headGroup = BbGroup(
        uuid = "bone_head",
        name = "head",
        origin = Vec(0.0, 24.0, 0.0),
        rotation = Vec.ZERO,
        children = listOf(
            BbGroupChild.ElementRef(headCube.uuid),
            BbGroupChild.ElementRef(hatCube.uuid),
        ),
        visibility = true,
    )

    val bodyGroup = BbGroup(
        uuid = "bone_body",
        name = "body",
        origin = Vec(0.0, 24.0, 0.0),
        rotation = Vec.ZERO,
        children = listOf(
            BbGroupChild.ElementRef(bodyCube.uuid),
            BbGroupChild.ElementRef(jacketCube.uuid),
        ),
        visibility = true,
    )

    val rightArmGroup = BbGroup(
        uuid = "bone_right_arm",
        name = "right_arm",
        origin = Vec(-5.0, 22.0, 0.0),
        rotation = Vec.ZERO,
        children = listOf(
            BbGroupChild.ElementRef(rightArmCube.uuid),
            BbGroupChild.ElementRef(rightSleeveCube.uuid),
        ),
        visibility = true,
    )

    val leftArmGroup = BbGroup(
        uuid = "bone_left_arm",
        name = "left_arm",
        origin = Vec(5.0, 22.0, 0.0),
        rotation = Vec.ZERO,
        children = listOf(
            BbGroupChild.ElementRef(leftArmCube.uuid),
            BbGroupChild.ElementRef(leftSleeveCube.uuid),
        ),
        visibility = true,
    )

    val rightLegGroup = BbGroup(
        uuid = "bone_right_leg",
        name = "right_leg",
        origin = Vec(-2.0, 12.0, 0.0),
        rotation = Vec.ZERO,
        children = listOf(
            BbGroupChild.ElementRef(rightLegCube.uuid),
            BbGroupChild.ElementRef(rightPantsCube.uuid),
        ),
        visibility = true,
    )

    val leftLegGroup = BbGroup(
        uuid = "bone_left_leg",
        name = "left_leg",
        origin = Vec(2.0, 12.0, 0.0),
        rotation = Vec.ZERO,
        children = listOf(
            BbGroupChild.ElementRef(leftLegCube.uuid),
            BbGroupChild.ElementRef(leftPantsCube.uuid),
        ),
        visibility = true,
    )

    val rootGroup = BbGroup(
        uuid = "bone_root",
        name = "root",
        origin = Vec.ZERO,
        rotation = Vec.ZERO,
        children = listOf(
            BbGroupChild.SubGroup(headGroup),
            BbGroupChild.SubGroup(bodyGroup),
            BbGroupChild.SubGroup(rightArmGroup),
            BbGroupChild.SubGroup(leftArmGroup),
            BbGroupChild.SubGroup(rightLegGroup),
            BbGroupChild.SubGroup(leftLegGroup),
        ),
        visibility = true,
    )

    groups += rootGroup

    val animations = buildAnimations()

    return BlockbenchModel(
        name = BLUEPRINT_NAME,
        meta = BbMeta(formatVersion = "4.10", modelFormat = "modded_entity", boxUv = false),
        resolution = BbResolution(TEXTURE_WIDTH, TEXTURE_HEIGHT),
        elements = elements,
        groups = groups,
        textures = listOf(texture),
        animations = animations,
    )
}
