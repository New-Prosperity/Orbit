package me.nebula.orbit.utils.screen.widget

import me.nebula.orbit.utils.screen.canvas.MapCanvas

abstract class Widget(var x: Int, var y: Int, var width: Int, var height: Int) {

    var visible: Boolean = true
    var parent: Widget? = null
    private val children = mutableListOf<Widget>()

    fun addChild(child: Widget) {
        child.parent = this
        children += child
    }

    fun draw(canvas: MapCanvas) {
        if (!visible) return
        val absX = absoluteX()
        val absY = absoluteY()
        render(canvas, absX, absY)
        for (child in children) child.draw(canvas)
    }

    fun hitTest(px: Int, py: Int): Widget? {
        if (!visible) return null
        val absX = absoluteX()
        val absY = absoluteY()
        for (i in children.lastIndex downTo 0) {
            val hit = children[i].hitTest(px, py)
            if (hit != null) return hit
        }
        if (px in absX until absX + width && py in absY until absY + height) return this
        return null
    }

    open fun onHover(hovering: Boolean) {}
    open fun onClick() {}

    protected abstract fun render(canvas: MapCanvas, absX: Int, absY: Int)

    private fun absoluteX(): Int = x + (parent?.absoluteX() ?: 0)
    private fun absoluteY(): Int = y + (parent?.absoluteY() ?: 0)
}
