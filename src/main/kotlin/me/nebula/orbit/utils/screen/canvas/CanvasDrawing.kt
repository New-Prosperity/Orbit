package me.nebula.orbit.utils.screen.canvas

import kotlin.math.abs
import kotlin.math.sqrt

fun MapCanvas.blendPixel(x: Int, y: Int, color: Int) {
    if (x !in 0 until width || y !in 0 until height) return
    val srcA = (color ushr 24) and 0xFF
    if (srcA == 0) return
    if (srcA == 255) {
        pixel(x, y, color)
        return
    }
    val dst = get(x, y)
    val dstA = (dst ushr 24) and 0xFF
    val dstR = (dst shr 16) and 0xFF
    val dstG = (dst shr 8) and 0xFF
    val dstB = dst and 0xFF
    val srcR = (color shr 16) and 0xFF
    val srcG = (color shr 8) and 0xFF
    val srcB = color and 0xFF
    val outR = (srcR * srcA + dstR * (255 - srcA)) / 255
    val outG = (srcG * srcA + dstG * (255 - srcA)) / 255
    val outB = (srcB * srcA + dstB * (255 - srcA)) / 255
    val outA = srcA + (dstA * (255 - srcA)) / 255
    pixel(x, y, (outA shl 24) or (outR shl 16) or (outG shl 8) or outB)
}

fun MapCanvas.line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
    var cx = x0
    var cy = y0
    val dx = abs(x1 - x0)
    val dy = -abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy
    while (true) {
        pixel(cx, cy, color)
        if (cx == x1 && cy == y1) break
        val e2 = 2 * err
        if (e2 >= dy) { err += dy; cx += sx }
        if (e2 <= dx) { err += dx; cy += sy }
    }
}

fun MapCanvas.circle(cx: Int, cy: Int, radius: Int, color: Int) {
    var x = radius
    var y = 0
    var d = 1 - radius
    while (x >= y) {
        pixel(cx + x, cy + y, color)
        pixel(cx - x, cy + y, color)
        pixel(cx + x, cy - y, color)
        pixel(cx - x, cy - y, color)
        pixel(cx + y, cy + x, color)
        pixel(cx - y, cy + x, color)
        pixel(cx + y, cy - x, color)
        pixel(cx - y, cy - x, color)
        y++
        if (d <= 0) {
            d += 2 * y + 1
        } else {
            x--
            d += 2 * (y - x) + 1
        }
    }
}

fun MapCanvas.filledCircle(cx: Int, cy: Int, radius: Int, color: Int) {
    var x = radius
    var y = 0
    var d = 1 - radius
    while (x >= y) {
        horizontalLine(cx - x, cx + x, cy + y, color)
        horizontalLine(cx - x, cx + x, cy - y, color)
        horizontalLine(cx - y, cx + y, cy + x, color)
        horizontalLine(cx - y, cx + y, cy - x, color)
        y++
        if (d <= 0) {
            d += 2 * y + 1
        } else {
            x--
            d += 2 * (y - x) + 1
        }
    }
}

fun MapCanvas.stroke(x: Int, y: Int, w: Int, h: Int, color: Int, thickness: Int = 1) {
    for (t in 0 until thickness) {
        fill(x + t, y + t, w - 2 * t, 1, color)
        fill(x + t, y + h - 1 - t, w - 2 * t, 1, color)
        fill(x + t, y + t + 1, 1, h - 2 * t - 2, color)
        fill(x + w - 1 - t, y + t + 1, 1, h - 2 * t - 2, color)
    }
}

fun MapCanvas.roundedRect(x: Int, y: Int, w: Int, h: Int, radius: Int, color: Int) {
    val r = radius.coerceAtMost(w / 2).coerceAtMost(h / 2)
    fill(x + r, y, w - 2 * r, h, color)
    fill(x, y + r, r, h - 2 * r, color)
    fill(x + w - r, y + r, r, h - 2 * r, color)
    filledCircleQuadrants(x + r, y + r, x + w - r - 1, y + h - r - 1, r, color)
}

fun MapCanvas.linearGradient(
    x: Int, y: Int, w: Int, h: Int,
    fromColor: Int, toColor: Int,
    horizontal: Boolean = true,
) {
    val x0 = x.coerceIn(0, width)
    val y0 = y.coerceIn(0, height)
    val x1 = (x + w).coerceIn(0, width)
    val y1 = (y + h).coerceIn(0, height)
    if (x0 >= x1 || y0 >= y1) return

    val fr = (fromColor shr 16) and 0xFF
    val fg = (fromColor shr 8) and 0xFF
    val fb = fromColor and 0xFF
    val fa = (fromColor ushr 24) and 0xFF
    val tr = (toColor shr 16) and 0xFF
    val tg = (toColor shr 8) and 0xFF
    val tb = toColor and 0xFF
    val ta = (toColor ushr 24) and 0xFF

    if (horizontal) {
        for (cx in x0 until x1) {
            val t = if (w <= 1) 0.0 else (cx - x).toDouble() / (w - 1)
            val color = lerpColor(fa, fr, fg, fb, ta, tr, tg, tb, t)
            for (py in y0 until y1) {
                pixels[py * width + cx] = color
            }
        }
    } else {
        for (cy in y0 until y1) {
            val t = if (h <= 1) 0.0 else (cy - y).toDouble() / (h - 1)
            val color = lerpColor(fa, fr, fg, fb, ta, tr, tg, tb, t)
            java.util.Arrays.fill(pixels, cy * width + x0, cy * width + x1, color)
        }
    }
    markDirtyRegion(x0, y0, x1, y1)
}

fun MapCanvas.radialGradient(cx: Int, cy: Int, radius: Int, innerColor: Int, outerColor: Int) {
    val ir = (innerColor shr 16) and 0xFF
    val ig = (innerColor shr 8) and 0xFF
    val ib = innerColor and 0xFF
    val ia = (innerColor ushr 24) and 0xFF
    val or2 = (outerColor shr 16) and 0xFF
    val og = (outerColor shr 8) and 0xFF
    val ob = outerColor and 0xFF
    val oa = (outerColor ushr 24) and 0xFF
    val rSq = radius.toDouble() * radius

    for (py in (cy - radius)..(cy + radius)) {
        for (px in (cx - radius)..(cx + radius)) {
            val dx = px - cx
            val dy = py - cy
            val distSq = dx * dx + dy * dy
            if (distSq > rSq) continue
            val t = (sqrt(distSq.toDouble()) / radius).coerceIn(0.0, 1.0)
            val color = lerpColor(ia, ir, ig, ib, oa, or2, og, ob, t)
            pixel(px, py, color)
        }
    }
}

private fun MapCanvas.horizontalLine(x0: Int, x1: Int, y: Int, color: Int) {
    val start = x0.coerceAtLeast(0)
    val end = x1.coerceAtMost(width - 1)
    if (start > end || y < 0 || y >= height) return
    fill(start, y, end - start + 1, 1, color)
}

private fun MapCanvas.filledCircleQuadrants(
    leftCx: Int, topCy: Int,
    rightCx: Int, bottomCy: Int,
    radius: Int, color: Int,
) {
    var x = radius
    var y = 0
    var d = 1 - radius
    while (x >= y) {
        horizontalLine(leftCx - x, leftCx, topCy - y, color)
        horizontalLine(rightCx, rightCx + x, topCy - y, color)
        horizontalLine(leftCx - x, leftCx, bottomCy + y, color)
        horizontalLine(rightCx, rightCx + x, bottomCy + y, color)
        horizontalLine(leftCx - y, leftCx, topCy - x, color)
        horizontalLine(rightCx, rightCx + y, topCy - x, color)
        horizontalLine(leftCx - y, leftCx, bottomCy + x, color)
        horizontalLine(rightCx, rightCx + y, bottomCy + x, color)
        y++
        if (d <= 0) {
            d += 2 * y + 1
        } else {
            x--
            d += 2 * (y - x) + 1
        }
    }
}

private fun lerpColor(
    fa: Int, fr: Int, fg: Int, fb: Int,
    ta: Int, tr: Int, tg: Int, tb: Int,
    t: Double,
): Int {
    val a = (fa + (ta - fa) * t).toInt()
    val r = (fr + (tr - fr) * t).toInt()
    val g = (fg + (tg - fg) * t).toInt()
    val b = (fb + (tb - fb) * t).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
