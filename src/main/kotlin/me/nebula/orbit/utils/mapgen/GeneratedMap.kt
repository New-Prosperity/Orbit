package me.nebula.orbit.utils.mapgen

import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.InstanceContainer

data class GeneratedMap(
    val instance: InstanceContainer,
    val center: Pos,
    val mapRadius: Int,
)
