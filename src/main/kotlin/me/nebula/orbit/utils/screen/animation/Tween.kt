package me.nebula.orbit.utils.screen.animation

import kotlin.math.PI
import kotlin.math.cos

enum class Easing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT;

    fun apply(t: Double): Double = when (this) {
        LINEAR -> t
        EASE_IN -> t * t
        EASE_OUT -> 1.0 - (1.0 - t) * (1.0 - t)
        EASE_IN_OUT -> (1.0 - cos(PI * t)) / 2.0
    }
}

class Tween<T : Any>(
    val from: T,
    val to: T,
    val durationMs: Long,
    val easing: Easing = Easing.LINEAR,
    val interpolator: TweenInterpolator<T>,
    val onUpdate: (T) -> Unit,
    val onComplete: (() -> Unit)? = null,
) {
    var startTime: Long = 0L
    var finished: Boolean = false
}

fun interface TweenInterpolator<T> {
    fun interpolate(from: T, to: T, t: Double): T
}

val IntInterpolator = TweenInterpolator<Int> { from, to, t -> (from + (to - from) * t).toInt() }
val DoubleInterpolator = TweenInterpolator<Double> { from, to, t -> from + (to - from) * t }
val ColorInterpolator = TweenInterpolator<Int> { from, to, t ->
    val fa = (from ushr 24) and 0xFF
    val fr = (from shr 16) and 0xFF
    val fg = (from shr 8) and 0xFF
    val fb = from and 0xFF
    val ta = (to ushr 24) and 0xFF
    val tr = (to shr 16) and 0xFF
    val tg = (to shr 8) and 0xFF
    val tb = to and 0xFF
    val a = (fa + (ta - fa) * t).toInt()
    val r = (fr + (tr - fr) * t).toInt()
    val g = (fg + (tg - fg) * t).toInt()
    val b = (fb + (tb - fb) * t).toInt()
    (a shl 24) or (r shl 16) or (g shl 8) or b
}

class AnimationController {

    private val active = mutableListOf<Tween<*>>()

    fun <T : Any> animate(tween: Tween<T>) {
        tween.startTime = System.currentTimeMillis()
        tween.finished = false
        active += tween
    }

    fun tick(): Boolean {
        if (active.isEmpty()) return false
        val now = System.currentTimeMillis()
        val iter = active.iterator()
        while (iter.hasNext()) {
            val tween = iter.next()
            tickTween(tween, now)
            if (tween.finished) iter.remove()
        }
        return active.isNotEmpty()
    }

    fun hasActive(): Boolean = active.isNotEmpty()

    fun clear() {
        active.clear()
    }

    private fun <T : Any> tickTween(tween: Tween<T>, now: Long) {
        val elapsed = now - tween.startTime
        val rawT = (elapsed.toDouble() / tween.durationMs).coerceIn(0.0, 1.0)
        val t = tween.easing.apply(rawT)
        val value = tween.interpolator.interpolate(tween.from, tween.to, t)
        tween.onUpdate(value)
        if (rawT >= 1.0) {
            tween.finished = true
            tween.onComplete?.invoke()
        }
    }
}
