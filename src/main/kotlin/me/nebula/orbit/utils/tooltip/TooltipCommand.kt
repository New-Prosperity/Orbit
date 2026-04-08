package me.nebula.orbit.utils.tooltip

import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.commandbuilder.command
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.component.DataComponents

private val STYLE_ARG = ArgumentType.Word("style").from(*TooltipStyleRegistry.ids().toTypedArray())

fun tooltipCommand(): Command = command("tooltip") {
    permission("orbit.tooltip")

    subCommand("list") {
        onPlayerExecute {
            val styles = TooltipStyleRegistry.ids()
            if (styles.isEmpty()) {
                player.sendMessage(player.translate("orbit.command.tooltip.list.empty"))
                return@onPlayerExecute
            }
            player.sendMessage(player.translate("orbit.command.tooltip.list.styles", "styles" to styles.joinToString(", ")))
        }
    }

    subCommand("set") {
        argument(STYLE_ARG)
        onPlayerExecute {
            val styleId = args.get(STYLE_ARG)
            val style = TooltipStyleRegistry[styleId]
            if (style == null) {
                player.sendMessage(player.translate("orbit.command.tooltip.unknown_style", "id" to styleId))
                return@onPlayerExecute
            }
            val held = player.getItemInMainHand()
            if (held.isAir) {
                player.sendMessage(player.translate("orbit.command.tooltip.hold_item"))
                return@onPlayerExecute
            }
            player.setItemInMainHand(held.withTooltipStyle(styleId))
            player.sendMessage(player.translate("orbit.command.tooltip.set.applied", "id" to styleId))
        }
    }

    subCommand("clear") {
        onPlayerExecute {
            val held = player.getItemInMainHand()
            if (held.isAir) {
                player.sendMessage(player.translate("orbit.command.tooltip.hold_item"))
                return@onPlayerExecute
            }
            player.setItemInMainHand(held.without(DataComponents.TOOLTIP_STYLE))
            player.sendMessage(player.translate("orbit.command.tooltip.cleared"))
        }
    }
}
