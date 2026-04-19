package me.nebula.orbit.utils.tooltip

import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.commandbuilder.command
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.component.DataComponents
import me.nebula.gravity.translation.Keys

private val STYLE_ARG = ArgumentType.Word("style").from(*TooltipStyleRegistry.ids().toTypedArray())

fun tooltipCommand(): Command = command("tooltip") {
    permission("orbit.tooltip")

    subCommand("list") {
        onPlayerExecute {
            val styles = TooltipStyleRegistry.ids()
            if (styles.isEmpty()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Tooltip.List.Empty))
                return@onPlayerExecute
            }
            player.sendMessage(player.translate(Keys.Orbit.Command.Tooltip.List.Styles, "styles" to styles.joinToString(", ")))
        }
    }

    subCommand("set") {
        argument(STYLE_ARG)
        onPlayerExecute {
            val styleId = args.get(STYLE_ARG)
            val style = TooltipStyleRegistry[styleId]
            if (style == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Tooltip.UnknownStyle, "id" to styleId))
                return@onPlayerExecute
            }
            val held = player.getItemInMainHand()
            if (held.isAir) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Tooltip.HoldItem))
                return@onPlayerExecute
            }
            player.setItemInMainHand(held.withTooltipStyle(styleId))
            player.sendMessage(player.translate(Keys.Orbit.Command.Tooltip.Set.Applied, "id" to styleId))
        }
    }

    subCommand("clear") {
        onPlayerExecute {
            val held = player.getItemInMainHand()
            if (held.isAir) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Tooltip.HoldItem))
                return@onPlayerExecute
            }
            player.setItemInMainHand(held.without(DataComponents.TOOLTIP_STYLE))
            player.sendMessage(player.translate(Keys.Orbit.Command.Tooltip.Cleared))
        }
    }
}
