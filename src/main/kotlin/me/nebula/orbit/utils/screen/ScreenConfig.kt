package me.nebula.orbit.utils.screen

import me.nebula.orbit.utils.screen.encoder.TILE_PIXELS
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

private const val VILLAGER_EYE_HEIGHT = 1.62

data class ScreenConfig(
    val eyePos: Pos,
    val canvasWidth: Int = 640,
    val canvasHeight: Int = 384,
    val fov: Double = 70.0,
    val coverage: Double = 0.85,
    val sensitivity: Double = 1.0,
) {
    init {
        require(canvasWidth % TILE_PIXELS == 0) { "canvasWidth must be multiple of $TILE_PIXELS" }
        require(canvasHeight % TILE_PIXELS == 0) { "canvasHeight must be multiple of $TILE_PIXELS" }
        require(canvasWidth > 0 && canvasHeight > 0) { "Canvas dimensions must be positive" }
        require(fov in 30.0..120.0) { "FOV must be between 30 and 120" }
        require(coverage in 0.1..1.0) { "Coverage must be between 0.1 and 1.0" }
    }

    val tilesX: Int get() = canvasWidth / TILE_PIXELS
    val tilesY: Int get() = canvasHeight / TILE_PIXELS
    val depth: Double get() = (tilesY / 2.0) / tan(Math.toRadians(fov * coverage / 2.0))
    val feetPos: Pos get() = Pos(eyePos.x(), eyePos.y() - VILLAGER_EYE_HEIGHT, eyePos.z(), eyePos.yaw(), eyePos.pitch())
}

data class ScreenBasis(
    val center: Vec,
    val right: Vec,
    val up: Vec,
    val forward: Vec,
    val guiWidth: Double,
    val guiHeight: Double,
    val horizontalFov: Double,
    val verticalFov: Double,
    val horizontalDegree: Double,
    val verticalDegree: Double,
    val pixelToWorldRatio: Double,
    val screenWidth: Int,
    val screenHeight: Int,
    val facingRotation: FloatArray,
    val depth: Double,
    val tilesX: Int,
    val tilesY: Int,
    val facing: Int,
)

fun computeScreenBasis(config: ScreenConfig): ScreenBasis {
    val depth = config.depth
    val tilesX = config.tilesX
    val tilesY = config.tilesY
    val guiHeight = tilesY.toDouble()
    val guiWidth = tilesX.toDouble()

    val snappedYaw = snapYaw(config.eyePos.yaw())
    val yawRad = Math.toRadians(-snappedYaw.toDouble())

    val forward = Vec(sin(yawRad), 0.0, cos(yawRad)).normalize()
    val worldUp = Vec(0.0, 1.0, 0.0)
    val right = forward.cross(worldUp).normalize()
    val up = right.cross(forward).normalize()

    val eye = Vec(config.eyePos.x(), config.eyePos.y(), config.eyePos.z())
    val center = eye.add(forward.mul(depth))

    val horizontalFov = Math.toDegrees(2.0 * atan(guiWidth / 2.0 / depth))
    val verticalFov = Math.toDegrees(2.0 * atan(guiHeight / 2.0 / depth))
    val horizontalDegree = horizontalFov / guiWidth
    val verticalDegree = verticalFov / guiHeight
    val pixelToWorldRatio = guiWidth / config.canvasWidth

    val facingRotation = quaternionFacingCamera(forward)
    val facing = yawToFacing(snappedYaw)

    return ScreenBasis(
        center = center,
        right = right,
        up = up,
        forward = forward,
        guiWidth = guiWidth,
        guiHeight = guiHeight,
        horizontalFov = horizontalFov,
        verticalFov = verticalFov,
        horizontalDegree = horizontalDegree,
        verticalDegree = verticalDegree,
        pixelToWorldRatio = pixelToWorldRatio,
        screenWidth = config.canvasWidth,
        screenHeight = config.canvasHeight,
        facingRotation = facingRotation,
        depth = depth,
        tilesX = tilesX,
        tilesY = tilesY,
        facing = facing,
    )
}

fun pixelToWorld(basis: ScreenBasis, pixelX: Double, pixelY: Double): Vec {
    val halfW = basis.screenWidth / 2.0
    val halfH = basis.screenHeight / 2.0
    val worldX = (pixelX - halfW) * basis.pixelToWorldRatio
    val worldY = (halfH - pixelY) * basis.pixelToWorldRatio
    return basis.center
        .add(basis.right.mul(worldX))
        .add(basis.up.mul(worldY))
}

fun wrapDegrees(degrees: Double): Double {
    var d = degrees % 360.0
    if (d >= 180.0) d -= 360.0
    if (d < -180.0) d += 360.0
    return d
}

private fun snapYaw(yaw: Float): Float {
    val normalized = ((yaw % 360f) + 360f) % 360f
    return when {
        normalized < 45f || normalized >= 315f -> 0f
        normalized < 135f -> 90f
        normalized < 225f -> 180f
        else -> 270f
    }
}

private fun yawToFacing(snappedYaw: Float): Int = when (snappedYaw.roundToInt()) {
    0 -> 2
    90 -> 5
    180 -> 3
    270 -> 4
    else -> 2
}

private fun quaternionFacingCamera(forward: Vec): FloatArray {
    val dx = -forward.x()
    val dy = -forward.y()
    val dz = -forward.z()

    val dot = dz
    if (dot < -0.9999) return floatArrayOf(0f, 1f, 0f, 0f)

    val w = 1.0 + dot
    val qx = -dy
    val qy = dx
    val invLen = 1.0 / sqrt(w * w + qx * qx + qy * qy)
    return floatArrayOf(
        (qx * invLen).toFloat(),
        (qy * invLen).toFloat(),
        0f,
        (w * invLen).toFloat(),
    )
}
