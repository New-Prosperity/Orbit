package me.nebula.orbit.utils.customcontent.armor

import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.commandbuilder.command
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import me.nebula.gravity.translation.Keys
import me.nebula.ether.utils.translation.asTranslationKey

private val ARMOR_ID_ARG = ArgumentType.String("armor_id")
private val SLOT_ARG = ArgumentType.String("slot")
private val ENCHANTED_ARG = ArgumentType.Boolean("enchanted")

fun armorTestCommand(): Command = command("armor") {
    subCommand("list") {
        onPlayerExecute {
            val armors = CustomArmorRegistry.all()
            if (armors.isEmpty()) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Armor.List.Empty))
                return@onPlayerExecute
            }
            for (armor in armors) {
                val parts = armor.parsed.pieces.joinToString(", ") { it.part.id }
                player.sendMessage(player.translate(Keys.Orbit.Command.Armor.List.Entry,
                    "id" to armor.id,
                    "colorId" to armor.colorId.toString(),
                    "parts" to parts,
                ))
            }
        }
    }

    subCommand("equip") {
        stringArgument("armor_id")
        booleanArgument("enchanted")
        onPlayerExecute {
            val armorId = args.get(ARMOR_ID_ARG)
            val enchanted = args.get(ENCHANTED_ARG)
            val armor = CustomArmorRegistry[armorId]
            if (armor == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Armor.Unknown, "id" to armorId))
                return@onPlayerExecute
            }
            armor.equipFullSet(player, enchanted)
            val key = if (enchanted) "orbit.command.armor.equip.success_enchanted" else "orbit.command.armor.equip.success"
            player.sendMessage(player.translate(key.asTranslationKey(), "id" to armor.id))
        }
    }

    subCommand("give") {
        stringArgument("armor_id")
        stringArgument("slot")
        booleanArgument("enchanted")
        onPlayerExecute {
            val armorId = args.get(ARMOR_ID_ARG)
            val slotName = args.get(SLOT_ARG)
            val enchanted = args.get(ENCHANTED_ARG)
            val armor = CustomArmorRegistry[armorId]
            if (armor == null) {
                player.sendMessage(player.translate(Keys.Orbit.Command.Armor.Unknown, "id" to armorId))
                return@onPlayerExecute
            }
            val part = ArmorPart.all.firstOrNull { it.id == slotName }
            if (part == null) {
                val validParts = ArmorPart.all.joinToString(", ") { it.id }
                player.sendMessage(player.translate(Keys.Orbit.Command.Armor.UnknownSlot,
                    "slot" to slotName, "valid" to validParts))
                return@onPlayerExecute
            }
            val item = armor.createItem(part, enchanted)
            player.inventory.addItemStack(item)
            val key = if (enchanted) "orbit.command.armor.give.success_enchanted" else "orbit.command.armor.give.success"
            player.sendMessage(player.translate(key.asTranslationKey(), "id" to armor.id, "part" to part.id))
        }
    }
}
