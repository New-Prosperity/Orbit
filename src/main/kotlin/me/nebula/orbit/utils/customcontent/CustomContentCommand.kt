package me.nebula.orbit.utils.customcontent

import com.sun.net.httpserver.HttpServer
import me.nebula.ether.utils.resource.ResourceManager
import me.nebula.orbit.utils.chat.sendMM
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.customcontent.block.BlockHitbox
import me.nebula.orbit.utils.customcontent.block.BlockStateAllocator
import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.minestom.server.command.builder.Command
import java.net.InetSocketAddress
import java.net.URI

private object PackServer {

    @Volatile private var server: HttpServer? = null

    @Synchronized
    fun ensureRunning(): Int {
        server?.let { return it.address.port }
        val port = (9100..9200).random()
        val srv = HttpServer.create(InetSocketAddress(port), 0)
        srv.createContext("/pack.zip") { exchange ->
            val bytes = CustomContentRegistry.packBytes
            if (bytes == null) {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
                return@createContext
            }
            exchange.responseHeaders["Content-Type"] = listOf("application/zip")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        srv.start()
        server = srv
        return port
    }
}

fun customContentCommand(resources: ResourceManager, serverHost: String?): Command = command("cc") {
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

    subCommand("send") {
        onPlayerExecute {
            val bytes = CustomContentRegistry.packBytes
            val sha1 = CustomContentRegistry.packSha1
            if (bytes == null || sha1 == null) {
                player.sendMM("<red>No pack available. Run <white>/cc pack <red>first.")
                return@onPlayerExecute
            }
            try {
                val port = PackServer.ensureRunning()
                val host = serverHost ?: "127.0.0.1"
                val url = "http://$host:$port/pack.zip"
                val info = ResourcePackInfo.resourcePackInfo()
                    .uri(URI.create(url))
                    .hash(sha1)
                    .build()
                val request = ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .required(false)
                    .build()
                player.sendResourcePacks(request)
                player.sendMM("<green>Sending pack from <white>$url")
            } catch (e: Exception) {
                player.sendMM("<red>Failed to send pack: ${e.message}")
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
