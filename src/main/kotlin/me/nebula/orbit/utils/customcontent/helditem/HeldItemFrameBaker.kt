package me.nebula.orbit.utils.customcontent.helditem

import net.minestom.server.coordinate.Vec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object HeldItemFrameBaker {

    private const val DEG_TO_RAD: Double = PI / 180.0
    private const val RAD_TO_DEG: Double = 180.0 / PI
    private const val EPS: Double = 1.0e-6

    data class BakedFace(
        val direction: String,
        val uMin: Float,
        val vMin: Float,
        val uMax: Float,
        val vMax: Float,
        val rotation: Int,
        val textureIndex: Int,
    )

    data class BakedRotation(
        val originX: Float,
        val originY: Float,
        val originZ: Float,
        val eulerX: Float,
        val eulerY: Float,
        val eulerZ: Float,
    )

    data class BakedElement(
        val fromX: Float,
        val fromY: Float,
        val fromZ: Float,
        val toX: Float,
        val toY: Float,
        val toZ: Float,
        val rotation: BakedRotation?,
        val faces: List<BakedFace>,
    )

    data class BakedFrame(val elements: List<BakedElement>)

    fun bakeAnimation(item: ParsedHeldItem, anim: HeldItemAnimation, fps: Int): List<BakedFrame> {
        val totalFrames = max(1, (anim.length * fps).roundToInt())
        return List(totalFrames) { frame ->
            val t = if (totalFrames == 1) 0f else frame.toFloat() / fps.toFloat()
            bakeAtTime(item, anim, t.coerceIn(0f, anim.length))
        }
    }

    fun bakeRest(item: ParsedHeldItem): BakedFrame =
        bakeAtTime(item, null, 0f)

    private fun bakeAtTime(item: ParsedHeldItem, anim: HeldItemAnimation?, t: Float): BakedFrame {
        val boneTransforms = computeBoneTransforms(item, anim, t)
        val elements = mutableListOf<BakedElement>()
        for (bone in item.bones) {
            val transform = boneTransforms[bone.id] ?: M_IDENTITY
            for (cube in bone.cubes) {
                elements += bakeCube(cube, transform)
            }
        }
        return BakedFrame(elements)
    }

    private fun computeBoneTransforms(
        item: ParsedHeldItem,
        anim: HeldItemAnimation?,
        t: Float,
    ): Map<Int, Mat3x4> {
        val out = mutableMapOf<Int, Mat3x4>()
        for (bone in item.bones) {
            val parent = if (bone.parentId >= 0) out[bone.parentId] ?: M_IDENTITY else M_IDENTITY

            val track = anim?.tracks?.get(bone.id)
            val animPos = if (track != null) sample(track.position, t, Vec.ZERO) else Vec.ZERO
            val animRot = if (track != null) sample(track.rotation, t, Vec.ZERO) else Vec.ZERO
            val animScale = if (track != null) sample(track.scale, t, Vec(1.0, 1.0, 1.0)) else Vec(1.0, 1.0, 1.0)

            val rotMatrix = eulerXyzToMatrix(animRot.x() * DEG_TO_RAD, animRot.y() * DEG_TO_RAD, animRot.z() * DEG_TO_RAD)
            val scaledRot = scaleMatrix(rotMatrix, animScale.x(), animScale.y(), animScale.z())

            val pivotX = bone.pivot.x()
            val pivotY = bone.pivot.y()
            val pivotZ = bone.pivot.z()

            val tx = pivotX - (scaledRot[0][0] * pivotX + scaledRot[0][1] * pivotY + scaledRot[0][2] * pivotZ) + animPos.x()
            val ty = pivotY - (scaledRot[1][0] * pivotX + scaledRot[1][1] * pivotY + scaledRot[1][2] * pivotZ) + animPos.y()
            val tz = pivotZ - (scaledRot[2][0] * pivotX + scaledRot[2][1] * pivotY + scaledRot[2][2] * pivotZ) + animPos.z()

            val local = Mat3x4(scaledRot, doubleArrayOf(tx, ty, tz))
            out[bone.id] = parent multiply local
        }
        return out
    }

    private fun bakeCube(cube: HeldItemCube, boneTransform: Mat3x4): BakedElement {
        val cubeRotMatrix: Array<DoubleArray>
        val cubeRotOriginX: Double
        val cubeRotOriginY: Double
        val cubeRotOriginZ: Double
        val cubeHasRotation: Boolean

        val rx = cube.rotation.x() * DEG_TO_RAD
        val ry = cube.rotation.y() * DEG_TO_RAD
        val rz = cube.rotation.z() * DEG_TO_RAD
        if (abs(rx) > EPS || abs(ry) > EPS || abs(rz) > EPS) {
            cubeRotMatrix = eulerXyzToMatrix(rx, ry, rz)
            cubeRotOriginX = cube.origin.x()
            cubeRotOriginY = cube.origin.y()
            cubeRotOriginZ = cube.origin.z()
            cubeHasRotation = true
        } else {
            cubeRotMatrix = IDENTITY_3
            cubeRotOriginX = 0.0
            cubeRotOriginY = 0.0
            cubeRotOriginZ = 0.0
            cubeHasRotation = false
        }

        val combinedR = matrixMultiply(boneTransform.r, cubeRotMatrix)

        val tCorrection = doubleArrayOf(
            boneTransform.r[0][0] * (cubeRotOriginX - cubeRotMatrix[0][0] * cubeRotOriginX - cubeRotMatrix[0][1] * cubeRotOriginY - cubeRotMatrix[0][2] * cubeRotOriginZ) +
                boneTransform.r[0][1] * (cubeRotOriginY - cubeRotMatrix[1][0] * cubeRotOriginX - cubeRotMatrix[1][1] * cubeRotOriginY - cubeRotMatrix[1][2] * cubeRotOriginZ) +
                boneTransform.r[0][2] * (cubeRotOriginZ - cubeRotMatrix[2][0] * cubeRotOriginX - cubeRotMatrix[2][1] * cubeRotOriginY - cubeRotMatrix[2][2] * cubeRotOriginZ),
            boneTransform.r[1][0] * (cubeRotOriginX - cubeRotMatrix[0][0] * cubeRotOriginX - cubeRotMatrix[0][1] * cubeRotOriginY - cubeRotMatrix[0][2] * cubeRotOriginZ) +
                boneTransform.r[1][1] * (cubeRotOriginY - cubeRotMatrix[1][0] * cubeRotOriginX - cubeRotMatrix[1][1] * cubeRotOriginY - cubeRotMatrix[1][2] * cubeRotOriginZ) +
                boneTransform.r[1][2] * (cubeRotOriginZ - cubeRotMatrix[2][0] * cubeRotOriginX - cubeRotMatrix[2][1] * cubeRotOriginY - cubeRotMatrix[2][2] * cubeRotOriginZ),
            boneTransform.r[2][0] * (cubeRotOriginX - cubeRotMatrix[0][0] * cubeRotOriginX - cubeRotMatrix[0][1] * cubeRotOriginY - cubeRotMatrix[0][2] * cubeRotOriginZ) +
                boneTransform.r[2][1] * (cubeRotOriginY - cubeRotMatrix[1][0] * cubeRotOriginX - cubeRotMatrix[1][1] * cubeRotOriginY - cubeRotMatrix[1][2] * cubeRotOriginZ) +
                boneTransform.r[2][2] * (cubeRotOriginZ - cubeRotMatrix[2][0] * cubeRotOriginX - cubeRotMatrix[2][1] * cubeRotOriginY - cubeRotMatrix[2][2] * cubeRotOriginZ),
        )

        val combinedT = doubleArrayOf(
            tCorrection[0] + boneTransform.t[0],
            tCorrection[1] + boneTransform.t[1],
            tCorrection[2] + boneTransform.t[2],
        )

        val isCombinedIdentity = isIdentityMatrix(combinedR)

        val rotation: BakedRotation?
        val fromX: Float
        val fromY: Float
        val fromZ: Float
        val toX: Float
        val toY: Float
        val toZ: Float

        if (isCombinedIdentity) {
            fromX = (cube.from.x() + combinedT[0]).toFloat()
            fromY = (cube.from.y() + combinedT[1]).toFloat()
            fromZ = (cube.from.z() + combinedT[2]).toFloat()
            toX = (cube.to.x() + combinedT[0]).toFloat()
            toY = (cube.to.y() + combinedT[1]).toFloat()
            toZ = (cube.to.z() + combinedT[2]).toFloat()
            rotation = null
        } else {
            val origin = solveOrigin(combinedR, combinedT)
            fromX = cube.from.x().toFloat()
            fromY = cube.from.y().toFloat()
            fromZ = cube.from.z().toFloat()
            toX = cube.to.x().toFloat()
            toY = cube.to.y().toFloat()
            toZ = cube.to.z().toFloat()
            val euler = matrixToEulerXyz(combinedR)
            rotation = BakedRotation(
                originX = origin[0].toFloat(),
                originY = origin[1].toFloat(),
                originZ = origin[2].toFloat(),
                eulerX = (euler[0] * RAD_TO_DEG).toFloat(),
                eulerY = (euler[1] * RAD_TO_DEG).toFloat(),
                eulerZ = (euler[2] * RAD_TO_DEG).toFloat(),
            )
        }

        val faces = cube.faces.map {
            BakedFace(it.direction, it.uMin, it.vMin, it.uMax, it.vMax, it.rotation, it.textureIndex)
        }

        return BakedElement(fromX, fromY, fromZ, toX, toY, toZ, rotation, faces)
    }

    private fun sample(kfs: List<HeldItemKeyframe>, t: Float, default: Vec): Vec {
        if (kfs.isEmpty()) return default
        if (kfs.size == 1) return kfs[0].value
        if (t <= kfs.first().time) return kfs.first().value
        if (t >= kfs.last().time) return kfs.last().value

        var i = 0
        while (i < kfs.size - 1 && kfs[i + 1].time < t) i++
        val a = kfs[i]
        val b = kfs[i + 1]
        val span = b.time - a.time
        if (span <= 0f) return a.value
        val u = ((t - a.time) / span).toDouble().coerceIn(0.0, 1.0)
        return when (a.interpolation) {
            AnimationInterpolation.STEP -> a.value
            AnimationInterpolation.CATMULLROM -> {
                val p0 = if (i > 0) kfs[i - 1].value else a.value
                val p3 = if (i + 2 < kfs.size) kfs[i + 2].value else b.value
                catmullrom(p0, a.value, b.value, p3, u)
            }
            else -> Vec(
                a.value.x() + (b.value.x() - a.value.x()) * u,
                a.value.y() + (b.value.y() - a.value.y()) * u,
                a.value.z() + (b.value.z() - a.value.z()) * u,
            )
        }
    }

    private fun catmullrom(p0: Vec, p1: Vec, p2: Vec, p3: Vec, u: Double): Vec {
        val u2 = u * u
        val u3 = u2 * u
        fun h(a: Double, b: Double, c: Double, d: Double): Double =
            0.5 * ((2.0 * b) + (-a + c) * u + (2.0 * a - 5.0 * b + 4.0 * c - d) * u2 + (-a + 3.0 * b - 3.0 * c + d) * u3)
        return Vec(
            h(p0.x(), p1.x(), p2.x(), p3.x()),
            h(p0.y(), p1.y(), p2.y(), p3.y()),
            h(p0.z(), p1.z(), p2.z(), p3.z()),
        )
    }

    private data class Mat3x4(val r: Array<DoubleArray>, val t: DoubleArray) {
        infix fun multiply(other: Mat3x4): Mat3x4 {
            val rNew = matrixMultiply(r, other.r)
            val tNew = doubleArrayOf(
                r[0][0] * other.t[0] + r[0][1] * other.t[1] + r[0][2] * other.t[2] + t[0],
                r[1][0] * other.t[0] + r[1][1] * other.t[1] + r[1][2] * other.t[2] + t[1],
                r[2][0] * other.t[0] + r[2][1] * other.t[1] + r[2][2] * other.t[2] + t[2],
            )
            return Mat3x4(rNew, tNew)
        }
    }

    private val IDENTITY_3 = arrayOf(
        doubleArrayOf(1.0, 0.0, 0.0),
        doubleArrayOf(0.0, 1.0, 0.0),
        doubleArrayOf(0.0, 0.0, 1.0),
    )

    private val M_IDENTITY = Mat3x4(IDENTITY_3, doubleArrayOf(0.0, 0.0, 0.0))

    private fun eulerXyzToMatrix(rx: Double, ry: Double, rz: Double): Array<DoubleArray> {
        val cx = cos(rx); val sx = sin(rx)
        val cy = cos(ry); val sy = sin(ry)
        val cz = cos(rz); val sz = sin(rz)
        val rxM = arrayOf(
            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(0.0, cx, -sx),
            doubleArrayOf(0.0, sx, cx),
        )
        val ryM = arrayOf(
            doubleArrayOf(cy, 0.0, sy),
            doubleArrayOf(0.0, 1.0, 0.0),
            doubleArrayOf(-sy, 0.0, cy),
        )
        val rzM = arrayOf(
            doubleArrayOf(cz, -sz, 0.0),
            doubleArrayOf(sz, cz, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0),
        )
        return matrixMultiply(matrixMultiply(rzM, ryM), rxM)
    }

    private fun matrixToEulerXyz(m: Array<DoubleArray>): DoubleArray {
        val sy = m[0][2].coerceIn(-1.0, 1.0)
        val ry = asin(sy)
        val cy = cos(ry)
        val rx: Double
        val rz: Double
        if (abs(cy) > EPS) {
            rx = atan2(-m[1][2], m[2][2])
            rz = atan2(-m[0][1], m[0][0])
        } else {
            rx = atan2(m[2][1], m[1][1])
            rz = 0.0
        }
        return doubleArrayOf(rx, ry, rz)
    }

    private fun matrixMultiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val r = Array(3) { DoubleArray(3) }
        for (i in 0..2) for (j in 0..2) {
            var s = 0.0
            for (k in 0..2) s += a[i][k] * b[k][j]
            r[i][j] = s
        }
        return r
    }

    private fun scaleMatrix(m: Array<DoubleArray>, sx: Double, sy: Double, sz: Double): Array<DoubleArray> {
        val r = Array(3) { DoubleArray(3) }
        for (i in 0..2) {
            r[i][0] = m[i][0] * sx
            r[i][1] = m[i][1] * sy
            r[i][2] = m[i][2] * sz
        }
        return r
    }

    private fun isIdentityMatrix(m: Array<DoubleArray>): Boolean {
        for (i in 0..2) for (j in 0..2) {
            val target = if (i == j) 1.0 else 0.0
            if (abs(m[i][j] - target) > EPS) return false
        }
        return true
    }

    private fun solveOrigin(r: Array<DoubleArray>, t: DoubleArray): DoubleArray {
        val a = arrayOf(
            doubleArrayOf(1.0 - r[0][0], -r[0][1], -r[0][2]),
            doubleArrayOf(-r[1][0], 1.0 - r[1][1], -r[1][2]),
            doubleArrayOf(-r[2][0], -r[2][1], 1.0 - r[2][2]),
        )
        val det = determinant3(a)
        if (abs(det) < 1e-9) return doubleArrayOf(0.0, 0.0, 0.0)
        val inv = inverse3(a, det)
        return doubleArrayOf(
            inv[0][0] * t[0] + inv[0][1] * t[1] + inv[0][2] * t[2],
            inv[1][0] * t[0] + inv[1][1] * t[1] + inv[1][2] * t[2],
            inv[2][0] * t[0] + inv[2][1] * t[1] + inv[2][2] * t[2],
        )
    }

    private fun determinant3(m: Array<DoubleArray>): Double =
        m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) -
            m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
            m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])

    private fun inverse3(m: Array<DoubleArray>, det: Double): Array<DoubleArray> {
        val invDet = 1.0 / det
        return arrayOf(
            doubleArrayOf(
                (m[1][1] * m[2][2] - m[1][2] * m[2][1]) * invDet,
                (m[0][2] * m[2][1] - m[0][1] * m[2][2]) * invDet,
                (m[0][1] * m[1][2] - m[0][2] * m[1][1]) * invDet,
            ),
            doubleArrayOf(
                (m[1][2] * m[2][0] - m[1][0] * m[2][2]) * invDet,
                (m[0][0] * m[2][2] - m[0][2] * m[2][0]) * invDet,
                (m[0][2] * m[1][0] - m[0][0] * m[1][2]) * invDet,
            ),
            doubleArrayOf(
                (m[1][0] * m[2][1] - m[1][1] * m[2][0]) * invDet,
                (m[0][1] * m[2][0] - m[0][0] * m[2][1]) * invDet,
                (m[0][0] * m[1][1] - m[0][1] * m[1][0]) * invDet,
            ),
        )
    }

    private fun min(a: Float, b: Float): Float = if (a < b) a else b
    private fun max(a: Float, b: Float): Float = if (a > b) a else b
}
