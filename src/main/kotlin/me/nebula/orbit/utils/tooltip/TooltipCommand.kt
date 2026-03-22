package me.nebula.orbit.utils.tooltip

import me.nebula.orbit.utils.commandbuilder.command
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack

private val STYLE_ARG = ArgumentType.Word("style").from(*TooltipStyleRegistry.ids().toTypedArray())

fun tooltipCommand(): Command = command("tooltip") {
    permission("orbit.tooltip")

    subCommand("list") {
        onPlayerExecute {
            val styles = TooltipStyleRegistry.ids()
            if (styles.isEmpty()) {
                player.sendMessage(Component.text("No tooltip styles registered.", NamedTextColor.RED))
                return@onPlayerExecute
            }
            player.sendMessage(Component.text("Tooltip styles: ${styles.joinToString(", ")}", NamedTextColor.GRAY))
        }
    }

    subCommand("set") {
        argument(STYLE_ARG)
        onPlayerExecute {
            val styleId = args.get(STYLE_ARG)
            val style = TooltipStyleRegistry[styleId]
            if (style == null) {
                player.sendMessage(Component.text("Unknown style: $styleId", NamedTextColor.RED))
                return@onPlayerExecute
            }
            val held = player.getItemInMainHand()
            if (held.isAir) {
                player.sendMessage(Component.text("Hold an item first.", NamedTextColor.RED))
                return@onPlayerExecute
            }
            player.setItemInMainHand(held.withTooltipStyle(styleId))
            player.sendMessage(Component.text("Applied tooltip style: $styleId", NamedTextColor.GREEN))
        }
    }

    subCommand("clear") {
        onPlayerExecute {
            val held = player.getItemInMainHand()
            if (held.isAir) {
                player.sendMessage(Component.text("Hold an item first.", NamedTextColor.RED))
                return@onPlayerExecute
            }
            player.setItemInMainHand(held.without(DataComponents.TOOLTIP_STYLE))
            player.sendMessage(Component.text("Cleared tooltip style.", NamedTextColor.GREEN))
        }
    }
}
