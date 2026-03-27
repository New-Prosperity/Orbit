package me.nebula.orbit.commands

import me.nebula.orbit.utils.chat.sendMM
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.suggestPlayers
import me.nebula.orbit.utils.gui.gui
import me.nebula.orbit.utils.weathercontrol.WeatherController
import me.nebula.orbit.utils.weathercontrol.WeatherState
import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandManager
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

private val GOD_TAG = Tag.Boolean("nebula:god_mode")
private var eventNode: EventNode<*>? = null

fun installBasicCommands(commandManager: CommandManager) {
    listOf(
        gamemodeCommand(),
        flyCommand(),
        healCommand(),
        feedCommand(),
        teleportCommand(),
        speedCommand(),
        killCommand(),
        clearCommand(),
        pingCommand(),
        godCommand(),
        giveCommand(),
        timeCommand(),
        weatherCommand(),
        tphereCommand(),
        invseeCommand(),
    ).forEach(commandManager::register)

    val node = EventNode.all("basic-commands")
    node.addListener(EntityDamageEvent::class.java) { event ->
        val entity = event.entity
        if (entity is Player && entity.getTag(GOD_TAG) == true) {
            event.isCancelled = true
        }
    }
    MinecraftServer.getGlobalEventHandler().addChild(node)
    eventNode = node
}

fun uninstallBasicCommands() {
    eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
    eventNode = null
}

private fun resolveOnline(name: String): Player? =
    MinecraftServer.getConnectionManager().findOnlinePlayer(name)

private fun parseGameMode(input: String): GameMode? = when (input.lowercase()) {
    "survival", "s", "0" -> GameMode.SURVIVAL
    "creative", "c", "1" -> GameMode.CREATIVE
    "adventure", "a", "2" -> GameMode.ADVENTURE
    "spectator", "sp", "3" -> GameMode.SPECTATOR
    else -> null
}

private fun targetOrSelf(player: Player, cmdArgs: Array<String>?, index: Int): Player? {
    if (cmdArgs == null || cmdArgs.size <= index) return player
    return resolveOnline(cmdArgs[index])
}

private fun gamemodeCommand() = command("gamemode") {
    permission("orbit.command.gamemode")
    aliases("gm")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        when (tokens.size) {
            2 -> GameMode.entries.map { it.name.lowercase() }
                .filter { it.startsWith(tokens.last(), ignoreCase = true) }
            3 -> suggestPlayers(tokens.last())
            else -> emptyList()
        }
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMM("<red>Usage: /gamemode <mode> [player]")
            return@onPlayerExecute
        }
        val mode = parseGameMode(cmdArgs[0]) ?: run {
            player.sendMM("<red>Unknown gamemode: <white>${cmdArgs[0]}")
            return@onPlayerExecute
        }
        val target = targetOrSelf(player, cmdArgs, 1) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs[1]}")
            return@onPlayerExecute
        }
        target.gameMode = mode
        if (target === player) {
            player.sendMM("<green>Gamemode set to <white>${mode.name.lowercase()}")
        } else {
            player.sendMM("<green>Set <white>${target.username}<green>'s gamemode to <white>${mode.name.lowercase()}")
            target.sendMM("<green>Your gamemode was set to <white>${mode.name.lowercase()}")
        }
    }
}

private fun flyCommand() = command("fly") {
    permission("orbit.command.fly")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last()) else emptyList()
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        val target = targetOrSelf(player, cmdArgs, 0) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs!![0]}")
            return@onPlayerExecute
        }
        val enabled = !target.isAllowFlying
        target.isAllowFlying = enabled
        if (!enabled) target.isFlying = false
        val state = if (enabled) "<green>enabled" else "<red>disabled"
        if (target === player) {
            player.sendMM("<green>Flight $state")
        } else {
            player.sendMM("<green>Flight $state <green>for <white>${target.username}")
            target.sendMM("<green>Flight $state")
        }
    }
}

private fun healCommand() = command("heal") {
    permission("orbit.command.heal")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last()) else emptyList()
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        val target = targetOrSelf(player, cmdArgs, 0) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs!![0]}")
            return@onPlayerExecute
        }
        target.health = target.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
        if (target === player) {
            player.sendMM("<green>Healed")
        } else {
            player.sendMM("<green>Healed <white>${target.username}")
            target.sendMM("<green>You have been healed")
        }
    }
}

private fun feedCommand() = command("feed") {
    permission("orbit.command.feed")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last()) else emptyList()
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        val target = targetOrSelf(player, cmdArgs, 0) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs!![0]}")
            return@onPlayerExecute
        }
        target.food = 20
        target.foodSaturation = 5.0f
        if (target === player) {
            player.sendMM("<green>Fed")
        } else {
            player.sendMM("<green>Fed <white>${target.username}")
            target.sendMM("<green>You have been fed")
        }
    }
}

private fun teleportCommand() = command("tp") {
    permission("orbit.command.teleport")
    aliases("teleport")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        when (tokens.size) {
            2 -> suggestPlayers(tokens.last())
            else -> emptyList()
        }
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMM("<red>Usage: /tp <player> | /tp <x> <y> <z>")
            return@onPlayerExecute
        }
        when (cmdArgs.size) {
            1 -> {
                val target = resolveOnline(cmdArgs[0]) ?: run {
                    player.sendMM("<red>Player not found: <white>${cmdArgs[0]}")
                    return@onPlayerExecute
                }
                player.teleport(target.position)
                player.sendMM("<green>Teleported to <white>${target.username}")
            }
            3 -> {
                val x = cmdArgs[0].toDoubleOrNull()
                val y = cmdArgs[1].toDoubleOrNull()
                val z = cmdArgs[2].toDoubleOrNull()
                if (x == null || y == null || z == null) {
                    player.sendMM("<red>Invalid coordinates")
                    return@onPlayerExecute
                }
                player.teleport(Pos(x, y, z, player.position.yaw(), player.position.pitch()))
                player.sendMM("<green>Teleported to <white>${"%.1f".format(x)}, ${"%.1f".format(y)}, ${"%.1f".format(z)}")
            }
            else -> player.sendMM("<red>Usage: /tp <player> | /tp <x> <y> <z>")
        }
    }
}

private fun speedCommand() = command("speed") {
    permission("orbit.command.speed")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        when (tokens.size) {
            2 -> (1..10).map(Int::toString).filter { it.startsWith(tokens.last()) }
            3 -> suggestPlayers(tokens.last())
            else -> emptyList()
        }
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMM("<red>Usage: /speed <1-10> [player]")
            return@onPlayerExecute
        }
        val value = cmdArgs[0].toIntOrNull()?.coerceIn(1, 10) ?: run {
            player.sendMM("<red>Speed must be 1-10")
            return@onPlayerExecute
        }
        val target = targetOrSelf(player, cmdArgs, 1) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs[1]}")
            return@onPlayerExecute
        }
        target.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1 * value
        target.flyingSpeed = 0.05f * value
        if (target === player) {
            player.sendMM("<green>Speed set to <white>$value")
        } else {
            player.sendMM("<green>Set <white>${target.username}<green>'s speed to <white>$value")
            target.sendMM("<green>Your speed was set to <white>$value")
        }
    }
}

private fun killCommand() = command("kill") {
    permission("orbit.command.kill")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last()) else emptyList()
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        val target = targetOrSelf(player, cmdArgs, 0) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs!![0]}")
            return@onPlayerExecute
        }
        target.kill()
        if (target !== player) {
            player.sendMM("<green>Killed <white>${target.username}")
        }
    }
}

private fun clearCommand() = command("clear") {
    permission("orbit.command.clear")
    aliases("clearinventory", "ci")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last()) else emptyList()
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        val target = targetOrSelf(player, cmdArgs, 0) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs!![0]}")
            return@onPlayerExecute
        }
        target.inventory.clear()
        if (target === player) {
            player.sendMM("<green>Inventory cleared")
        } else {
            player.sendMM("<green>Cleared <white>${target.username}<green>'s inventory")
            target.sendMM("<green>Your inventory was cleared")
        }
    }
}

private fun pingCommand() = command("ping") {
    permission("orbit.command.ping")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last()) else emptyList()
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        val target = targetOrSelf(player, cmdArgs, 0) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs!![0]}")
            return@onPlayerExecute
        }
        if (target === player) {
            player.sendMM("<green>Your ping: <white>${target.latency}ms")
        } else {
            player.sendMM("<green>${target.username}'s ping: <white>${target.latency}ms")
        }
    }
}

private fun godCommand() = command("god") {
    permission("orbit.command.god")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last()) else emptyList()
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        val target = targetOrSelf(player, cmdArgs, 0) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs!![0]}")
            return@onPlayerExecute
        }
        val enabled = if (target.getTag(GOD_TAG) == true) {
            target.removeTag(GOD_TAG)
            false
        } else {
            target.setTag(GOD_TAG, true)
            true
        }
        val state = if (enabled) "<green>enabled" else "<red>disabled"
        if (target === player) {
            player.sendMM("<green>God mode $state")
        } else {
            player.sendMM("<green>God mode $state <green>for <white>${target.username}")
            target.sendMM("<green>God mode $state")
        }
    }
}

private fun giveCommand() = command("give") {
    permission("orbit.command.give")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        when (tokens.size) {
            2 -> Material.values().asSequence()
                .map { it.key().value() }
                .filter { it.startsWith(tokens.last(), ignoreCase = true) }
                .take(50)
                .toList()
            3 -> emptyList()
            4 -> suggestPlayers(tokens.last())
            else -> emptyList()
        }
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMM("<red>Usage: /give <item> [amount] [player]")
            return@onPlayerExecute
        }
        val input = cmdArgs[0].lowercase()
        val key = if (input.contains(":")) input else "minecraft:$input"
        val material = Material.fromKey(key)
            ?: Material.values().firstOrNull { it.key().value().equals(input, ignoreCase = true) }
        if (material == null) {
            player.sendMM("<red>Unknown item: <white>${cmdArgs[0]}")
            return@onPlayerExecute
        }
        val amount = (cmdArgs.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, 6400)
        val target = targetOrSelf(player, cmdArgs, 2) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs[2]}")
            return@onPlayerExecute
        }
        target.inventory.addItemStack(ItemStack.of(material, amount))
        if (target === player) {
            player.sendMM("<green>Gave <white>${amount}x ${material.key().value()}")
        } else {
            player.sendMM("<green>Gave <white>${amount}x ${material.key().value()} <green>to <white>${target.username}")
            target.sendMM("<green>Received <white>${amount}x ${material.key().value()}")
        }
    }
}

private fun timeCommand() = command("time") {
    permission("orbit.command.time")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) listOf("day", "noon", "night", "midnight")
            .filter { it.startsWith(tokens.last(), ignoreCase = true) }
        else emptyList()
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMM("<red>Usage: /time <day|noon|night|midnight|ticks>")
            return@onPlayerExecute
        }
        val ticks = when (cmdArgs[0].lowercase()) {
            "day" -> 1000L
            "noon" -> 6000L
            "night" -> 13000L
            "midnight" -> 18000L
            else -> cmdArgs[0].toLongOrNull()
        }
        if (ticks == null) {
            player.sendMM("<red>Invalid time: <white>${cmdArgs[0]}")
            return@onPlayerExecute
        }
        val instance = player.instance ?: return@onPlayerExecute
        instance.time = ticks
        player.sendMM("<green>Time set to <white>$ticks")
    }
}

private fun weatherCommand() = command("weather") {
    permission("orbit.command.weather")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) listOf("clear", "rain", "thunder")
            .filter { it.startsWith(tokens.last(), ignoreCase = true) }
        else emptyList()
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMM("<red>Usage: /weather <clear|rain|thunder>")
            return@onPlayerExecute
        }
        val state = when (cmdArgs[0].lowercase()) {
            "clear", "sunny" -> WeatherState.SUNNY
            "rain", "rainy" -> WeatherState.RAINY
            "thunder", "thundering" -> WeatherState.THUNDERING
            else -> null
        }
        if (state == null) {
            player.sendMM("<red>Unknown weather: <white>${cmdArgs[0]}")
            return@onPlayerExecute
        }
        val instance = player.instance ?: return@onPlayerExecute
        WeatherController.setWeather(instance, state)
        player.sendMM("<green>Weather set to <white>${state.name.lowercase()}")
    }
}

private fun tphereCommand() = command("tphere") {
    permission("orbit.command.tphere")
    aliases("s2l")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last()) else emptyList()
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMM("<red>Usage: /tphere <player>")
            return@onPlayerExecute
        }
        val target = resolveOnline(cmdArgs[0]) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs[0]}")
            return@onPlayerExecute
        }
        target.teleport(player.position)
        player.sendMM("<green>Teleported <white>${target.username} <green>to you")
        target.sendMM("<green>You were teleported to <white>${player.username}")
    }
}

private fun invseeCommand() = command("invsee") {
    permission("orbit.command.invsee")
    stringArrayArgument("args")
    tabComplete { _, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last()) else emptyList()
    }
    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            player.sendMM("<red>Usage: /invsee <player>")
            return@onPlayerExecute
        }
        val target = resolveOnline(cmdArgs[0]) ?: run {
            player.sendMM("<red>Player not found: <white>${cmdArgs[0]}")
            return@onPlayerExecute
        }
        val inv = target.inventory
        val g = gui("<gray>${target.username}'s Inventory", rows = 5) {
            for (i in 9 until 36) {
                val item = inv.getItemStack(i)
                if (!item.isAir) slot(i - 9, item)
            }
            for (i in 0 until 9) {
                val item = inv.getItemStack(i)
                if (!item.isAir) slot(i + 36, item)
            }
            border(Material.GRAY_STAINED_GLASS_PANE)
        }
        g.open(player)
    }
}
