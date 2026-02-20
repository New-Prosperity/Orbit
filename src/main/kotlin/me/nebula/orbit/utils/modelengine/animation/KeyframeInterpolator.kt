package me.nebula.orbit.utils.modelengine.animation

import me.nebula.orbit.utils.modelengine.blueprint.InterpolationType
import me.nebula.orbit.utils.modelengine.blueprint.Keyframe
import net.minestom.server.coordinate.Vec

class KeyframeInterpolator(private val keyframes: List<Keyframe>) {

    val isEmpty: Boolean get() = keyframes.isEmpty()
    val duration: Float get() = keyframes.lastOrNull()?.time ?: 0f

    fun evaluate(time: Float): Vec? {
        if (keyframes.isEmpty()) return null
        if (keyframes.size == 1) return keyframes[0].value
        if (time <= keyframes.first().time) return keyframes.first().value
        if (time >= keyframes.last().time) return keyframes.last().value

        val index = findKeyframeIndex(time)
        val kf0 = keyframes[index]
        val kf1 = keyframes[index + 1]
        val dt = kf1.time - kf0.time
        val t = if (dt > 0f) ((time - kf0.time) / dt).coerceIn(0f, 1f) else 0f

        return when (kf0.interpolation) {
            InterpolationType.LINEAR -> linearVec(kf0.value, kf1.value, t)

            InterpolationType.STEP -> stepVec(kf0.value, kf1.value, t)

            InterpolationType.CATMULLROM -> {
                val p0 = if (index > 0) keyframes[index - 1].value else kf0.value
                val p3 = if (index + 2 < keyframes.size) keyframes[index + 2].value else kf1.value
                catmullromVec(p0, kf0.value, kf1.value, p3, t)
            }

            InterpolationType.BEZIER -> {
                val cp0 = kf0.value.add(kf0.bezierRightValue)
                val cp1 = kf1.value.add(kf1.bezierLeftValue)
                bezierVec(kf0.value, cp0, cp1, kf1.value, t)
            }
        }
    }

    private fun findKeyframeIndex(time: Float): Int {
        var low = 0
        var high = keyframes.size - 2
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (keyframes[mid].time <= time) low = mid else high = mid - 1
        }
        return low
    }
}
