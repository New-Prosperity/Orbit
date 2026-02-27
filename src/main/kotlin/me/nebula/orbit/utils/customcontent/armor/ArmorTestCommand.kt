package me.nebula.orbit.utils.customcontent.armor

import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.suggestPlayers
import net.kyori.adventure.text.Component
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType

private val ARMOR_ID_ARG = ArgumentType.String("armor_id")
private val SLOT_ARG = ArgumentType.String("slot")

fun armorTestCommand(): Command = command("armor") {
    subCommand("list") {
        onPlayerExecute {
            val armors = CustomArmorRegistry.all()
            if (armors.isEmpty()) {
                player.sendMessage(Component.text("No custom armors registered."))
                return@onPlayerExecute
            }
            for (armor in armors) {
                val parts = armor.parsed.pieces.joinToString(", ") { it.part.id }
                player.sendMessage(Component.text("${armor.id} (colorId=${armor.colorId}, parts=[$parts])"))
            }
        }
    }

    subCommand("equip") {
        stringArgument("armor_id")
        onPlayerExecute {
            val armorId = args.get(ARMOR_ID_ARG)
            val armor = CustomArmorRegistry[armorId]
            if (armor == null) {
                player.sendMessage(Component.text("Unknown armor: $armorId"))
                return@onPlayerExecute
            }
            armor.equipFullSet(player)
            player.sendMessage(Component.text("Equipped armor: ${armor.id}"))
        }
    }

    subCommand("give") {
        stringArgument("armor_id")
        stringArgument("slot")
        onPlayerExecute {
            val armorId = args.get(ARMOR_ID_ARG)
            val slotName = args.get(SLOT_ARG)
            val armor = CustomArmorRegistry[armorId]
            if (armor == null) {
                player.sendMessage(Component.text("Unknown armor: $armorId"))
                return@onPlayerExecute
            }
            val part = ArmorPart.all.firstOrNull { it.id == slotName }
            if (part == null) {
                val validParts = ArmorPart.all.joinToString(", ") { it.id }
                player.sendMessage(Component.text("Unknown slot: $slotName. Valid: $validParts"))
                return@onPlayerExecute
            }
            val item = armor.createItem(part)
            player.inventory.addItemStack(item)
            player.sendMessage(Component.text("Gave ${armor.id} ${part.id}"))
        }
    }
}
