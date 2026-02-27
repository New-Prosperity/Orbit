package me.nebula.orbit.utils.cinematic

import me.nebula.orbit.utils.commandbuilder.command
import net.minestom.server.command.builder.Command
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.kyori.adventure.text.Component

fun cinematicTestCommand(): Command = command("cinematic") {
    subCommand("play") {
        onPlayerExecute {
            val origin = player.position
            val yaw = origin.yaw()
            val pitch = origin.pitch()

            cinematic(player) {
                node(0f, Pos(origin.x(), origin.y() + 10, origin.z(), yaw, -30f))
                node(3f, Pos(origin.x() + 15, origin.y() + 15, origin.z() + 15, yaw + 90f, -20f))
                node(6f, Pos(origin.x() + 30, origin.y() + 10, origin.z(), yaw + 180f, -10f))
                node(9f, Pos(origin.x() + 15, origin.y() + 5, origin.z() - 15, yaw + 270f, 0f))
                node(12f, Pos(origin.x(), origin.y() + 10, origin.z(), yaw + 360f, -30f))
                onComplete { player.sendMessage(Component.text("Cinematic complete!")) }
            }
        }
    }

    subCommand("lookat") {
        onPlayerExecute {
            val origin = player.position
            val target = origin.asVec()

            cinematic(player) {
                node(0f, Pos(origin.x(), origin.y() + 15, origin.z() + 20))
                node(4f, Pos(origin.x() + 20, origin.y() + 10, origin.z()))
                node(8f, Pos(origin.x(), origin.y() + 15, origin.z() - 20))
                node(12f, Pos(origin.x() - 20, origin.y() + 10, origin.z()))
                node(16f, Pos(origin.x(), origin.y() + 15, origin.z() + 20))
                lookAt(target)
                loop()
            }
        }
    }

    subCommand("stop") {
        onPlayerExecute {
            if (player.isInCinematic) {
                player.stopCinematic()
                player.sendMessage(Component.text("Cinematic stopped."))
            } else {
                player.sendMessage(Component.text("No active cinematic."))
            }
        }
    }
}
