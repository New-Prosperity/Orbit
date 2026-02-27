package me.nebula.orbit.utils.customcontent

import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.chat.sendMM
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.customcontent.armor.CustomArmorRegistry
import me.nebula.orbit.utils.customcontent.block.BlockHitbox
import me.nebula.orbit.utils.customcontent.block.BlockStateAllocator
import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import net.minestom.server.command.builder.Command
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

fun customContentCommand(resources: ResourceManager): Command = command("cc") {
    permission("orbit.customcontent")

    subCommand("items") {
        onPlayerExecute {
            val items = CustomItemRegistry.all()
            if (items.isEmpty()) {
                player.sendMM("<gray>No custom items registered.")
                return@onPlayerExecute
            }
            player.sendMM("<gold><bold>Custom Items</bold> <gray>(${items.size})")
            items.forEach { item ->
                player.sendMM("<yellow>${item.id} <gray>— ${item.baseMaterial.key().value()} <dark_gray>CMD=${item.customModelDataId}")
            }
        }
    }

    subCommand("blocks") {
        onPlayerExecute {
            val blocks = CustomBlockRegistry.all()
            if (blocks.isEmpty()) {
                player.sendMM("<gray>No custom blocks registered.")
                return@onPlayerExecute
            }
            player.sendMM("<gold><bold>Custom Blocks</bold> <gray>(${blocks.size})")
            blocks.forEach { block ->
                player.sendMM("<yellow>${block.id} <gray>— ${block.hitbox.name} <dark_gray>state=${block.allocatedState.name()} CMD=${block.customModelDataId}")
            }
        }
    }

    subCommand("give") {
        stringArrayArgument("args")
        tabComplete { _, input ->
            val tokens = input.split(" ")
            if (tokens.size <= 1) {
                CustomItemRegistry.all().map { it.id }.filter { it.startsWith(tokens[0], ignoreCase = true) }
            } else emptyList()
        }
        onPlayerExecute {
            @Suppress("UNCHECKED_CAST")
            val cmdArgs = args.get("args") as? Array<String>
            val itemId = cmdArgs?.getOrNull(0)
            if (itemId.isNullOrEmpty()) {
                player.sendMM("<red>Usage: /cc give <item> [amount]")
                return@onPlayerExecute
            }
            val item = CustomItemRegistry[itemId]
            if (item == null) {
                player.sendMM("<red>Unknown custom item: $itemId")
                return@onPlayerExecute
            }
            val amount = cmdArgs.getOrNull(1)?.toIntOrNull() ?: 1
            player.inventory.addItemStack(item.createStack(amount))
            player.sendMM("<green>Gave <white>$amount<green>x <white>${item.id}")
        }
    }

    subCommand("info") {
        wordArgument("id")
        tabComplete { _, input ->
            val itemIds = CustomItemRegistry.all().map { it.id }
            val blockIds = CustomBlockRegistry.all().map { it.id }
            (itemIds + blockIds).filter { it.startsWith(input, ignoreCase = true) }
        }
        onPlayerExecute {
            val id: String? = args.get("id")
            if (id == null) {
                player.sendMM("<red>Usage: /cc info <id>")
                return@onPlayerExecute
            }
            val item = CustomItemRegistry[id]
            val block = CustomBlockRegistry[id]
            if (item == null && block == null) {
                player.sendMM("<red>Unknown item/block: $id")
                return@onPlayerExecute
            }
            if (item != null) {
                player.sendMM("<gold><bold>Item: ${item.id}</bold>")
                player.sendMM("<gray>Material: <white>${item.baseMaterial.key().value()}")
                player.sendMM("<gray>CMD ID: <white>${item.customModelDataId}")
                item.displayName?.let { player.sendMM("<gray>Display name: <white>$it") }
                if (item.lore.isNotEmpty()) player.sendMM("<gray>Lore: <white>${item.lore.joinToString(" | ")}")
                player.sendMM("<gray>Unbreakable: <white>${item.unbreakable}")
                player.sendMM("<gray>Glowing: <white>${item.glowing}")
                player.sendMM("<gray>Max stack: <white>${item.maxStackSize}")
                player.sendMM("<gray>Model: <white>${item.modelPath}")
            }
            if (block != null) {
                player.sendMM("<gold><bold>Block: ${block.id}</bold>")
                player.sendMM("<gray>Hitbox: <white>${block.hitbox.name}")
                player.sendMM("<gray>Item ID: <white>${block.itemId}")
                player.sendMM("<gray>CMD ID: <white>${block.customModelDataId}")
                player.sendMM("<gray>Hardness: <white>${block.hardness}")
                player.sendMM("<gray>State: <white>${block.allocatedState.name()}")
                player.sendMM("<gray>Place sound: <white>${block.placeSound}")
                player.sendMM("<gray>Break sound: <white>${block.breakSound}")
                player.sendMM("<gray>Model: <white>${block.modelPath}")
            }
        }
    }

    subCommand("reload") {
        onPlayerExecute {
            player.sendMM("<gray>Reloading custom content...")
            try {
                CustomContentRegistry.reload()
                val items = CustomItemRegistry.all().size
                val blocks = CustomBlockRegistry.all().size
                val armors = CustomArmorRegistry.all().size
                val packSize = CustomContentRegistry.packBytes?.size?.let { it / 1024 } ?: 0
                player.sendMM("<green>Reloaded: <white>$items<green> items, <white>$blocks<green> blocks, <white>$armors<green> armors <gray>(${packSize}KB)")
            } catch (e: Exception) {
                player.sendMM("<red>Reload failed: ${e.message}")
            }
        }
    }

    subCommand("pack") {
        onPlayerExecute {
            try {
                val result = CustomContentRegistry.mergePack()
                player.sendMM("<green>Pack merged: <white>${result.packBytes.size / 1024}KB <gray>SHA-1=<white>${result.sha1}")
            } catch (e: Exception) {
                player.sendMM("<red>Pack merge failed: ${e.message}")
            }
        }
    }

    subCommand("packdump") {
        onPlayerExecute {
            val bytes = CustomContentRegistry.packBytes
            if (bytes == null) {
                player.sendMM("<red>No pack available.")
                return@onPlayerExecute
            }
            player.sendMM("<gold><bold>Pack Contents</bold> <gray>(${bytes.size / 1024}KB)")
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val data = zip.readAllBytes()
                    val isJson = entry.name.endsWith(".json")
                    if (isJson && data.size < 500) {
                        player.sendMM("<yellow>${entry.name} <gray>(${data.size}B)")
                        player.sendMM("<dark_gray>${String(data, Charsets.UTF_8).replace("\n", " ").take(200)}")
                    } else {
                        player.sendMM("<yellow>${entry.name} <gray>(${data.size}B)")
                    }
                    entry = zip.nextEntry
                }
            }
        }
    }

    subCommand("allocations") {
        onPlayerExecute {
            player.sendMM("<gold><bold>Block State Allocations</bold>")
            val hitboxTypes = listOf(
                BlockHitbox.Full, BlockHitbox.Slab, BlockHitbox.Stair, BlockHitbox.Thin,
                BlockHitbox.Transparent, BlockHitbox.Wall, BlockHitbox.Fence, BlockHitbox.Trapdoor,
            )
            hitboxTypes.forEach { hitbox ->
                val used = BlockStateAllocator.poolUsed(hitbox)
                val total = BlockStateAllocator.poolSize(hitbox)
                val color = when {
                    used == 0 -> "<gray>"
                    total > 0 && used.toFloat() / total > 0.8f -> "<red>"
                    total > 0 && used.toFloat() / total > 0.5f -> "<yellow>"
                    else -> "<green>"
                }
                player.sendMM("$color${hitbox.name} <gray>— <white>$used<gray>/<white>$total")
            }
        }
    }
}
