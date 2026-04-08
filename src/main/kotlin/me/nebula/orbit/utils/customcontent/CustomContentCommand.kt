package me.nebula.orbit.utils.customcontent

import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.translation.translate
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
                player.sendMessage(player.translate("orbit.command.cc.items.empty"))
                return@onPlayerExecute
            }
            player.sendMessage(player.translate("orbit.command.cc.items.header", "count" to items.size.toString()))
            items.forEach { item ->
                player.sendMessage(player.translate("orbit.command.cc.items.entry",
                    "id" to item.id,
                    "material" to item.baseMaterial.key().value(),
                    "cmd" to item.customModelDataId.toString(),
                ))
            }
        }
    }

    subCommand("blocks") {
        onPlayerExecute {
            val blocks = CustomBlockRegistry.all()
            if (blocks.isEmpty()) {
                player.sendMessage(player.translate("orbit.command.cc.blocks.empty"))
                return@onPlayerExecute
            }
            player.sendMessage(player.translate("orbit.command.cc.blocks.header", "count" to blocks.size.toString()))
            blocks.forEach { block ->
                player.sendMessage(player.translate("orbit.command.cc.blocks.entry",
                    "id" to block.id,
                    "hitbox" to block.hitbox.name,
                    "state" to block.allocatedState.name(),
                    "cmd" to block.customModelDataId.toString(),
                ))
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
                player.sendMessage(player.translate("orbit.command.cc.give.usage"))
                return@onPlayerExecute
            }
            val item = CustomItemRegistry[itemId]
            if (item == null) {
                player.sendMessage(player.translate("orbit.command.cc.give.unknown", "id" to itemId))
                return@onPlayerExecute
            }
            val amount = cmdArgs.getOrNull(1)?.toIntOrNull() ?: 1
            player.inventory.addItemStack(item.createStack(amount))
            player.sendMessage(player.translate("orbit.command.cc.give.success",
                "amount" to amount.toString(), "id" to item.id))
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
                player.sendMessage(player.translate("orbit.command.cc.info.usage"))
                return@onPlayerExecute
            }
            val item = CustomItemRegistry[id]
            val block = CustomBlockRegistry[id]
            if (item == null && block == null) {
                player.sendMessage(player.translate("orbit.command.cc.info.unknown", "id" to id))
                return@onPlayerExecute
            }
            if (item != null) {
                player.sendMessage(player.translate("orbit.command.cc.info.item.header", "id" to item.id))
                player.sendMessage(player.translate("orbit.command.cc.info.item.material", "value" to item.baseMaterial.key().value()))
                player.sendMessage(player.translate("orbit.command.cc.info.item.cmd", "value" to item.customModelDataId.toString()))
                item.displayName?.let { player.sendMessage(player.translate("orbit.command.cc.info.item.display_name", "value" to it)) }
                if (item.lore.isNotEmpty()) player.sendMessage(player.translate("orbit.command.cc.info.item.lore", "value" to item.lore.joinToString(" | ")))
                player.sendMessage(player.translate("orbit.command.cc.info.item.unbreakable", "value" to item.unbreakable.toString()))
                player.sendMessage(player.translate("orbit.command.cc.info.item.glowing", "value" to item.glowing.toString()))
                player.sendMessage(player.translate("orbit.command.cc.info.item.max_stack", "value" to item.maxStackSize.toString()))
                player.sendMessage(player.translate("orbit.command.cc.info.item.model", "value" to item.modelPath))
            }
            if (block != null) {
                player.sendMessage(player.translate("orbit.command.cc.info.block.header", "id" to block.id))
                player.sendMessage(player.translate("orbit.command.cc.info.block.hitbox", "value" to block.hitbox.name))
                player.sendMessage(player.translate("orbit.command.cc.info.block.item_id", "value" to block.itemId))
                player.sendMessage(player.translate("orbit.command.cc.info.block.cmd", "value" to block.customModelDataId.toString()))
                player.sendMessage(player.translate("orbit.command.cc.info.block.hardness", "value" to block.hardness.toString()))
                player.sendMessage(player.translate("orbit.command.cc.info.block.state", "value" to block.allocatedState.name()))
                player.sendMessage(player.translate("orbit.command.cc.info.block.place_sound", "value" to block.placeSound))
                player.sendMessage(player.translate("orbit.command.cc.info.block.break_sound", "value" to block.breakSound))
                player.sendMessage(player.translate("orbit.command.cc.info.block.model", "value" to block.modelPath))
            }
        }
    }

    subCommand("reload") {
        onPlayerExecute {
            player.sendMessage(player.translate("orbit.command.cc.reload.starting"))
            try {
                CustomContentRegistry.reload()
                val items = CustomItemRegistry.all().size
                val blocks = CustomBlockRegistry.all().size
                val armors = CustomArmorRegistry.all().size
                val packSize = CustomContentRegistry.packBytes?.size?.let { it / 1024 } ?: 0
                player.sendMessage(player.translate("orbit.command.cc.reload.success",
                    "items" to items.toString(),
                    "blocks" to blocks.toString(),
                    "armors" to armors.toString(),
                    "size" to packSize.toString(),
                ))
            } catch (e: Exception) {
                player.sendMessage(player.translate("orbit.command.cc.reload.failed", "error" to (e.message ?: "")))
            }
        }
    }

    subCommand("pack") {
        onPlayerExecute {
            try {
                val result = CustomContentRegistry.mergePack()
                player.sendMessage(player.translate("orbit.command.cc.pack.merged",
                    "size" to (result.packBytes.size / 1024).toString(),
                    "sha" to result.sha1,
                ))
            } catch (e: Exception) {
                player.sendMessage(player.translate("orbit.command.cc.pack.failed", "error" to (e.message ?: "")))
            }
        }
    }

    subCommand("packdump") {
        onPlayerExecute {
            val bytes = CustomContentRegistry.packBytes
            if (bytes == null) {
                player.sendMessage(player.translate("orbit.command.cc.packdump.unavailable"))
                return@onPlayerExecute
            }
            player.sendMessage(player.translate("orbit.command.cc.packdump.header", "size" to (bytes.size / 1024).toString()))
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val data = zip.readAllBytes()
                    val isJson = entry.name.endsWith(".json")
                    player.sendMessage(player.translate("orbit.command.cc.packdump.entry",
                        "name" to entry.name, "bytes" to data.size.toString()))
                    if (isJson && data.size < 500) {
                        player.sendMessage(player.translate("orbit.command.cc.packdump.preview",
                            "content" to String(data, Charsets.UTF_8).replace("\n", " ").take(200)))
                    }
                    entry = zip.nextEntry
                }
            }
        }
    }

    subCommand("allocations") {
        onPlayerExecute {
            player.sendMessage(player.translate("orbit.command.cc.allocations.header"))
            val hitboxTypes = listOf(
                BlockHitbox.Full, BlockHitbox.Slab, BlockHitbox.Stair, BlockHitbox.Thin,
                BlockHitbox.Transparent, BlockHitbox.Wall, BlockHitbox.Fence, BlockHitbox.Trapdoor,
            )
            hitboxTypes.forEach { hitbox ->
                val used = BlockStateAllocator.poolUsed(hitbox)
                val total = BlockStateAllocator.poolSize(hitbox)
                val key = when {
                    used == 0 -> "orbit.command.cc.allocations.entry.empty"
                    total > 0 && used.toFloat() / total > 0.8f -> "orbit.command.cc.allocations.entry.high"
                    total > 0 && used.toFloat() / total > 0.5f -> "orbit.command.cc.allocations.entry.medium"
                    else -> "orbit.command.cc.allocations.entry.low"
                }
                player.sendMessage(player.translate(key,
                    "name" to hitbox.name,
                    "used" to used.toString(),
                    "total" to total.toString(),
                ))
            }
        }
    }
}
