package me.nebula.orbit.utils.statue

import me.nebula.orbit.utils.modelengine.generator.BbAnimation
import me.nebula.orbit.utils.modelengine.generator.BbAnimator
import me.nebula.orbit.utils.modelengine.generator.BbKeyframe
import net.minestom.server.coordinate.Vec

internal fun buildAnimations(): List<BbAnimation> {
    val idleAnim = BbAnimation(
        name = "idle",
        length = 2.0f,
        loop = "loop",
        animators = mapOf(
            "bone_body" to BbAnimator(
                name = "body",
                keyframes = listOf(
                    rotKeyframe(0.0f, Vec.ZERO),
                    rotKeyframe(1.0f, Vec(2.0, 0.0, 0.0)),
                    rotKeyframe(2.0f, Vec.ZERO),
                ),
            ),
            "bone_head" to BbAnimator(
                name = "head",
                keyframes = listOf(
                    rotKeyframe(0.0f, Vec.ZERO),
                    rotKeyframe(0.5f, Vec(1.0, 0.0, 0.0)),
                    rotKeyframe(1.5f, Vec(-1.0, 0.0, 0.0)),
                    rotKeyframe(2.0f, Vec.ZERO),
                ),
            ),
        ),
    )

    val waveAnim = BbAnimation(
        name = "wave",
        length = 1.0f,
        loop = "once",
        animators = mapOf(
            "bone_right_arm" to BbAnimator(
                name = "right_arm",
                keyframes = listOf(
                    rotKeyframe(0.0f, Vec.ZERO),
                    rotKeyframe(0.25f, Vec(-120.0, 0.0, -30.0)),
                    rotKeyframe(0.5f, Vec(-120.0, 0.0, -30.0)),
                    rotKeyframe(1.0f, Vec.ZERO),
                ),
            ),
        ),
    )

    val crossedArmsAnim = BbAnimation(
        name = "crossed_arms",
        length = 0.05f,
        loop = "hold",
        animators = mapOf(
            "bone_right_arm" to BbAnimator(
                name = "right_arm",
                keyframes = listOf(
                    rotKeyframe(0.0f, Vec(-30.0, 20.0, 0.0)),
                ),
            ),
            "bone_left_arm" to BbAnimator(
                name = "left_arm",
                keyframes = listOf(
                    rotKeyframe(0.0f, Vec(-30.0, -20.0, 0.0)),
                ),
            ),
        ),
    )

    val celebrateAnim = BbAnimation(
        name = "celebrate",
        length = 1.5f,
        loop = "once",
        animators = mapOf(
            "bone_right_arm" to BbAnimator(
                name = "right_arm",
                keyframes = listOf(
                    rotKeyframe(0.0f, Vec.ZERO),
                    rotKeyframe(0.25f, Vec(-150.0, 0.0, -15.0)),
                    rotKeyframe(1.25f, Vec(-150.0, 0.0, -15.0)),
                    rotKeyframe(1.5f, Vec.ZERO),
                ),
            ),
            "bone_left_arm" to BbAnimator(
                name = "left_arm",
                keyframes = listOf(
                    rotKeyframe(0.0f, Vec.ZERO),
                    rotKeyframe(0.25f, Vec(-150.0, 0.0, 15.0)),
                    rotKeyframe(1.25f, Vec(-150.0, 0.0, 15.0)),
                    rotKeyframe(1.5f, Vec.ZERO),
                ),
            ),
            "bone_body" to BbAnimator(
                name = "body",
                keyframes = listOf(
                    posKeyframe(0.0f, Vec.ZERO),
                    posKeyframe(0.25f, Vec(0.0, 2.0, 0.0)),
                    posKeyframe(1.25f, Vec(0.0, 2.0, 0.0)),
                    posKeyframe(1.5f, Vec.ZERO),
                ),
            ),
        ),
    )

    val saluteAnim = BbAnimation(
        name = "salute",
        length = 1.5f,
        loop = "once",
        animators = mapOf(
            "bone_right_arm" to BbAnimator(
                name = "right_arm",
                keyframes = listOf(
                    rotKeyframe(0.0f, Vec.ZERO),
                    rotKeyframe(0.25f, Vec(-45.0, 0.0, 0.0)),
                    rotKeyframe(1.25f, Vec(-45.0, 0.0, 0.0)),
                    rotKeyframe(1.5f, Vec.ZERO),
                ),
            ),
        ),
    )

    val lookAroundAnim = BbAnimation(
        name = "look_around",
        length = 4.0f,
        loop = "loop",
        animators = mapOf(
            "bone_head" to BbAnimator(
                name = "head",
                keyframes = listOf(
                    rotKeyframe(0.0f, Vec.ZERO),
                    rotKeyframe(0.5f, Vec(0.0, -40.0, 0.0)),
                    rotKeyframe(1.5f, Vec(0.0, -40.0, 0.0)),
                    rotKeyframe(2.0f, Vec.ZERO),
                    rotKeyframe(2.5f, Vec(0.0, 40.0, 0.0)),
                    rotKeyframe(3.5f, Vec(0.0, 40.0, 0.0)),
                    rotKeyframe(4.0f, Vec.ZERO),
                ),
            ),
        ),
    )

    val sitAnim = BbAnimation(
        name = "sit",
        length = 0.05f,
        loop = "hold",
        animators = mapOf(
            "bone_right_leg" to BbAnimator(
                name = "right_leg",
                keyframes = listOf(
                    rotKeyframe(0.0f, Vec(-90.0, 0.0, 0.0)),
                ),
            ),
            "bone_left_leg" to BbAnimator(
                name = "left_leg",
                keyframes = listOf(
                    rotKeyframe(0.0f, Vec(-90.0, 0.0, 0.0)),
                ),
            ),
            "bone_body" to BbAnimator(
                name = "body",
                keyframes = listOf(
                    posKeyframe(0.0f, Vec(0.0, -6.0, 0.0)),
                ),
            ),
        ),
    )

    return listOf(idleAnim, waveAnim, crossedArmsAnim, celebrateAnim, saluteAnim, lookAroundAnim, sitAnim)
}

private fun rotKeyframe(time: Float, rotation: Vec): BbKeyframe = BbKeyframe(
    channel = "rotation",
    time = time,
    dataPoints = listOf(rotation),
    interpolation = "linear",
    bezierLeftTime = Vec.ZERO,
    bezierLeftValue = Vec.ZERO,
    bezierRightTime = Vec.ZERO,
    bezierRightValue = Vec.ZERO,
)

private fun posKeyframe(time: Float, position: Vec): BbKeyframe = BbKeyframe(
    channel = "position",
    time = time,
    dataPoints = listOf(position),
    interpolation = "linear",
    bezierLeftTime = Vec.ZERO,
    bezierLeftValue = Vec.ZERO,
    bezierRightTime = Vec.ZERO,
    bezierRightValue = Vec.ZERO,
)
