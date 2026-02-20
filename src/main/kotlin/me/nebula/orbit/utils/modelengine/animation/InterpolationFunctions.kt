package me.nebula.orbit.utils.modelengine.animation

import net.minestom.server.coordinate.Vec

fun linearVec(a: Vec, b: Vec, t: Float): Vec = Vec(
    a.x() + (b.x() - a.x()) * t,
    a.y() + (b.y() - a.y()) * t,
    a.z() + (b.z() - a.z()) * t,
)

fun stepVec(a: Vec, @Suppress("UNUSED_PARAMETER") b: Vec, @Suppress("UNUSED_PARAMETER") t: Float): Vec = a

fun catmullromVec(p0: Vec, p1: Vec, p2: Vec, p3: Vec, t: Float): Vec {
    val t2 = t * t
    val t3 = t2 * t
    return Vec(
        catmullrom(p0.x(), p1.x(), p2.x(), p3.x(), t, t2, t3),
        catmullrom(p0.y(), p1.y(), p2.y(), p3.y(), t, t2, t3),
        catmullrom(p0.z(), p1.z(), p2.z(), p3.z(), t, t2, t3),
    )
}

fun bezierVec(p0: Vec, cp0: Vec, cp1: Vec, p1: Vec, t: Float): Vec {
    val u = 1f - t
    val u2 = u * u
    val u3 = u2 * u
    val t2 = t * t
    val t3 = t2 * t
    return Vec(
        u3 * p0.x() + 3 * u2 * t * cp0.x() + 3 * u * t2 * cp1.x() + t3 * p1.x(),
        u3 * p0.y() + 3 * u2 * t * cp0.y() + 3 * u * t2 * cp1.y() + t3 * p1.y(),
        u3 * p0.z() + 3 * u2 * t * cp0.z() + 3 * u * t2 * cp1.z() + t3 * p1.z(),
    )
}

private fun catmullrom(
    p0: Double, p1: Double, p2: Double, p3: Double,
    t: Float, t2: Float, t3: Float,
): Double {
    val a = -0.5 * p0 + 1.5 * p1 - 1.5 * p2 + 0.5 * p3
    val b = p0 - 2.5 * p1 + 2.0 * p2 - 0.5 * p3
    val c = -0.5 * p0 + 0.5 * p2
    val d = p1
    return a * t3 + b * t2 + c * t + d
}
