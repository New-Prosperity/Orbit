package me.nebula.orbit.utils.hud.shader

import me.nebula.orbit.utils.screen.shader.MapShaderPack

object HudShaderPack {

    fun generate(): Map<String, ByteArray> {
        val all = MapShaderPack.generate()
        return all.filterKeys { it.contains("map_decode") }
    }
}
