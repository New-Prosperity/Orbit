package me.nebula.orbit.utils.screen

import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.screen.animation.DoubleInterpolator
import me.nebula.orbit.utils.screen.animation.Easing
import me.nebula.orbit.utils.screen.animation.Tween
import me.nebula.orbit.utils.screen.canvas.filledCircle
import me.nebula.orbit.utils.screen.canvas.linearGradient
import me.nebula.orbit.utils.screen.canvas.roundedRect
import me.nebula.orbit.utils.screen.canvas.stroke
import me.nebula.orbit.utils.screen.font.DEFAULT_FONT
import me.nebula.orbit.utils.screen.font.drawText
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.coordinate.Pos
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.timer.TaskSchedule

private const val EYE_HEIGHT = 1.62

fun screenTestCommand(): Command = command("screen") {
    subCommand("test") {
        onPlayerExecute {
            val origin = player.position
            val eyePos = Pos(origin.x(), origin.y() + EYE_HEIGHT, origin.z(), origin.yaw(), 0f)

            screen(player, eyePos) {
                cursor {
                    item(ItemStack.of(Material.ARROW))
                    scale(0.06f)
                }

                background(0xFF0D1117.toInt())

                onDraw { canvas ->
                    canvas.linearGradient(0, 0, 640, 40, 0xFF1A1A2E.toInt(), 0xFF16213E.toInt())

                    canvas.drawText(DEFAULT_FONT, 260, 16, "SCREEN DEMO", 0xFFFFFFFF.toInt())

                    canvas.roundedRect(20, 60, 280, 140, 8, 0xFF16213E.toInt())
                    canvas.drawText(DEFAULT_FONT, 30, 70, "Drawing Primitives", 0xFFE0E0E0.toInt())
                    canvas.filledCircle(80, 140, 30, 0xFF4CAF50.toInt())
                    canvas.filledCircle(160, 140, 30, 0xFF2196F3.toInt())
                    canvas.filledCircle(240, 140, 30, 0xFFF44336.toInt())
                    canvas.stroke(25, 65, 270, 130, 0xFF333333.toInt(), 1)

                    canvas.roundedRect(320, 60, 300, 140, 8, 0xFF16213E.toInt())
                    canvas.drawText(DEFAULT_FONT, 330, 70, "Gradient Fills", 0xFFE0E0E0.toInt())
                    canvas.linearGradient(340, 100, 260, 30, 0xFFFF5722.toInt(), 0xFFFFEB3B.toInt())
                    canvas.linearGradient(340, 140, 260, 30, 0xFF9C27B0.toInt(), 0xFF03A9F4.toInt())

                    canvas.roundedRect(370, 265, 180, 50, 6, 0xFF533483.toInt())
                    canvas.drawText(DEFAULT_FONT, 427, 283, "Settings", 0xFFFFFFFF.toInt())
                }

                panel(20, 220, 280, 140, 0xFF16213E.toInt()) {
                    cornerRadius(8)
                    label(10, 10, "Widget Panel", DEFAULT_FONT, 0xFFE0E0E0.toInt())
                    button(40, 40, 200, 40, "Play", DEFAULT_FONT) {
                        bgColor(0xFF0F3460.toInt())
                        hoverColor(0xFF1A5276.toInt())
                        cornerRadius(4)
                        onClick { player.sendMessage(Component.text("Play clicked!")) }
                    }
                    progressBar(40, 100, 200, 16) {
                        progress(0.7f)
                        fgColor(0xFF4CAF50.toInt())
                        bgColor(0xFF333333.toInt())
                        cornerRadius(4)
                    }
                }

                button("settings", 460, 290, 180, 50) {
                    onClick { player.sendMessage(Component.text("Settings clicked!")) }
                    onHover { hovering ->
                        Screen.update(player) { canvas ->
                            val color = if (hovering) 0xFF6C3483.toInt() else 0xFF533483.toInt()
                            canvas.roundedRect(370, 265, 180, 50, 6, color)
                            canvas.drawText(DEFAULT_FONT, 427, 283, "Settings", 0xFFFFFFFF.toInt())
                        }
                    }
                }

                sensitivity(1.0)
                escToClose()
                onClose { player.sendMessage(Component.text("Screen closed.")) }
            }

            var progress = 0.0
            val animCtrl = Screen.animations(player) ?: return@onPlayerExecute
            animCtrl.animate(Tween(
                from = 0.0,
                to = 1.0,
                durationMs = 2000,
                easing = Easing.EASE_IN_OUT,
                interpolator = DoubleInterpolator,
                onUpdate = { progress = it },
            ))

            var tickTask: net.minestom.server.timer.Task? = null
            tickTask = MinecraftServer.getSchedulerManager()
                .buildTask {
                    if (!Screen.isOpen(player)) {
                        tickTask?.cancel()
                        return@buildTask
                    }
                    Screen.update(player) { canvas ->
                        canvas.roundedRect(60, 320, 200, 16, 4, 0xFF333333.toInt())
                        val fillW = (200 * progress).toInt()
                        if (fillW > 0) {
                            canvas.roundedRect(60, 320, fillW, 16, 4, 0xFF4CAF50.toInt())
                        }
                    }
                    if (!animCtrl.hasActive()) tickTask?.cancel()
                }
                .repeat(TaskSchedule.tick(1))
                .schedule()
        }
    }

    subCommand("close") {
        onPlayerExecute {
            if (player.hasScreenOpen) {
                player.closeScreen()
            } else {
                player.sendMessage(Component.text("No screen open."))
            }
        }
    }
}
