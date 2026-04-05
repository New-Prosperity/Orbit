package me.nebula.orbit.utils.vanilla

import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player

val Player.isCreativeOrSpectator: Boolean
    get() = gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR
