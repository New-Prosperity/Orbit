package me.nebula.orbit.utils.cinematic

import me.nebula.orbit.utils.commandbuilder.command
import net.kyori.adventure.text.Component
import net.minestom.server.command.builder.Command
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec

fun cinematicTestCommand(): Command = command("cinematic") {

    subCommand("play") {
        onPlayerExecute {
            val o = player.position

            player.playCinematic {
                node(0f, Pos(o.x(), o.y() + 10, o.z(), o.yaw(), -30f))
                node(3f, Pos(o.x() + 15, o.y() + 15, o.z() + 15, o.yaw() + 90f, -20f))
                node(6f, Pos(o.x() + 30, o.y() + 10, o.z(), o.yaw() + 180f, -10f))
                node(9f, Pos(o.x() + 15, o.y() + 5, o.z() - 15, o.yaw() + 270f, 0f))
                node(12f, Pos(o.x(), o.y() + 10, o.z(), o.yaw() + 360f, -30f))
                fade(20, 20)
                hideHud()
                onComplete { player.sendMessage(Component.text("Cinematic complete!")) }
            }
        }
    }

    subCommand("lookat") {
        onPlayerExecute {
            val o = player.position
            val target = o.asVec()

            player.playCinematic {
                node(0f, Pos(o.x(), o.y() + 15, o.z() + 20))
                node(4f, Pos(o.x() + 20, o.y() + 10, o.z()))
                node(8f, Pos(o.x(), o.y() + 15, o.z() - 20))
                node(12f, Pos(o.x() - 20, o.y() + 10, o.z()))
                node(16f, Pos(o.x(), o.y() + 15, o.z() + 20))
                lookAt(target)
                loop()
                hideHud()
                fade(20, 0)
            }
        }
    }

    subCommand("slow") {
        onPlayerExecute {
            val o = player.position

            player.playCinematic {
                node(0f, Pos(o.x(), o.y() + 5, o.z() + 10, o.yaw(), -15f))
                node(5f, Pos(o.x() + 10, o.y() + 8, o.z(), o.yaw() + 45f, -10f))
                node(10f, Pos(o.x(), o.y() + 5, o.z() - 10, o.yaw() + 90f, -15f))
                speed(0.5f)
                fade(30, 30)
                hideHud()
                onTick { p, progress ->
                    p.sendActionBar(Component.text("%.0f%%".format(progress * 100)))
                }
                onComplete { player.sendMessage(Component.text("Slow cinematic done!")) }
            }
        }
    }

    subCommand("fast") {
        onPlayerExecute {
            val o = player.position

            player.playCinematic {
                node(0f, Pos(o.x(), o.y() + 20, o.z() + 30, o.yaw(), -40f))
                node(2f, Pos(o.x() + 30, o.y() + 25, o.z(), o.yaw() + 120f, -30f))
                node(4f, Pos(o.x(), o.y() + 20, o.z() - 30, o.yaw() + 240f, -40f))
                node(6f, Pos(o.x() - 30, o.y() + 25, o.z(), o.yaw() + 360f, -30f))
                node(8f, Pos(o.x(), o.y() + 20, o.z() + 30, o.yaw() + 480f, -40f))
                speed(2f)
                fade(10, 10)
                hideHud()
                onComplete { player.sendMessage(Component.text("Fast cinematic done!")) }
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
