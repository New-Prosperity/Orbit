package me.nebula.orbit.commands

import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
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
            player.sendMessage(player.translate("orbit.command.gamemode.usage"))
            return@onPlayerExecute
        }
        val mode = parseGameMode(cmdArgs[0]) ?: run {
            player.sendMessage(player.translate("orbit.command.gamemode.unknown", "mode" to cmdArgs[0]))
            return@onPlayerExecute
        }
        val target = targetOrSelf(player, cmdArgs, 1) ?: run {
            player.sendMessage(player.translate("orbit.command.player_not_found", "name" to cmdArgs[1]))
            return@onPlayerExecute
        }
        target.gameMode = mode
        val modeName = mode.name.lowercase()
        if (target === player) {
            player.sendMessage(player.translate("orbit.command.gamemode.set_self", "mode" to modeName))
        } else {
            player.sendMessage(player.translate("orbit.command.gamemode.set_other", "name" to target.username, "mode" to modeName))
            target.sendMessage(target.translate("orbit.command.gamemode.set_by_other", "mode" to modeName))
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
            player.sendMessage(player.translate("orbit.command.player_not_found", "name" to cmdArgs?.getOrNull(0).orEmpty()))
            return@onPlayerExecute
        }
        val enabled = !target.isAllowFlying
        target.isAllowFlying = enabled
        if (!enabled) target.isFlying = false
        val stateKey = if (enabled) "orbit.command.fly.enabled" else "orbit.command.fly.disabled"
        if (target === player) {
            player.sendMessage(player.translate(stateKey + ".self"))
        } else {
            player.sendMessage(player.translate(stateKey + ".other", "name" to target.username))
            target.sendMessage(target.translate(stateKey + ".self"))
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
            player.sendMessage(player.translate("orbit.command.player_not_found", "name" to cmdArgs?.getOrNull(0).orEmpty()))
            return@onPlayerExecute
        }
        target.health = target.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
        if (target === player) {
            player.sendMessage(player.translate("orbit.command.heal.self"))
        } else {
            player.sendMessage(player.translate("orbit.command.heal.other", "name" to target.username))
            target.sendMessage(target.translate("orbit.command.heal.by_other"))
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
            player.sendMessage(player.translate("orbit.command.player_not_found", "name" to cmdArgs?.getOrNull(0).orEmpty()))
            return@onPlayerExecute
        }
        target.food = 20
        target.foodSaturation = 5.0f
        if (target === player) {
            player.sendMessage(player.translate("orbit.command.feed.self"))
        } else {
            player.sendMessage(player.translate("orbit.command.feed.other", "name" to target.username))
            target.sendMessage(target.translate("orbit.command.feed.by_other"))
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
            player.sendMessage(player.translate("orbit.command.tp.usage"))
            return@onPlayerExecute
        }
        when (cmdArgs.size) {
            1 -> {
                val target = resolveOnline(cmdArgs[0]) ?: run {
                    player.sendMessage(player.translate("orbit.command.player_not_found", "name" to cmdArgs[0]))
                    return@onPlayerExecute
                }
                player.teleport(target.position)
                player.sendMessage(player.translate("orbit.command.tp.to_player", "name" to target.username))
            }
            3 -> {
                val x = cmdArgs[0].toDoubleOrNull()
                val y = cmdArgs[1].toDoubleOrNull()
                val z = cmdArgs[2].toDoubleOrNull()
                if (x == null || y == null || z == null) {
                    player.sendMessage(player.translate("orbit.command.tp.invalid_coords"))
                    return@onPlayerExecute
                }
                player.teleport(Pos(x, y, z, player.position.yaw(), player.position.pitch()))
                player.sendMessage(player.translate("orbit.command.tp.to_coords",
                    "x" to "%.1f".format(x), "y" to "%.1f".format(y), "z" to "%.1f".format(z)))
            }
            else -> player.sendMessage(player.translate("orbit.command.tp.usage"))
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
            player.sendMessage(player.translate("orbit.command.speed.usage"))
            return@onPlayerExecute
        }
        val value = cmdArgs[0].toIntOrNull()?.coerceIn(1, 10) ?: run {
            player.sendMessage(player.translate("orbit.command.speed.range"))
            return@onPlayerExecute
        }
        val target = targetOrSelf(player, cmdArgs, 1) ?: run {
            player.sendMessage(player.translate("orbit.command.player_not_found", "name" to cmdArgs[1]))
            return@onPlayerExecute
        }
        target.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 0.1 * value
        target.flyingSpeed = 0.05f * value
        if (target === player) {
            player.sendMessage(player.translate("orbit.command.speed.set_self", "value" to value.toString()))
        } else {
            player.sendMessage(player.translate("orbit.command.speed.set_other", "name" to target.username, "value" to value.toString()))
            target.sendMessage(target.translate("orbit.command.speed.set_by_other", "value" to value.toString()))
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
            player.sendMessage(player.translate("orbit.command.player_not_found", "name" to cmdArgs?.getOrNull(0).orEmpty()))
            return@onPlayerExecute
        }
        target.kill()
        if (target !== player) {
            player.sendMessage(player.translate("orbit.command.kill.other", "name" to target.username))
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
            player.sendMessage(player.translate("orbit.command.player_not_found", "name" to cmdArgs?.getOrNull(0).orEmpty()))
            return@onPlayerExecute
        }
        target.inventory.clear()
        if (target === player) {
            player.sendMessage(player.translate("orbit.command.clear.self"))
        } else {
            player.sendMessage(player.translate("orbit.command.clear.other", "name" to target.username))
            target.sendMessage(target.translate("orbit.command.clear.by_other"))
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
            player.sendMessage(player.translate("orbit.command.player_not_found", "name" to cmdArgs?.getOrNull(0).orEmpty()))
            return@onPlayerExecute
        }
        val ms = target.latency.toString()
        if (target === player) {
            player.sendMessage(player.translate("orbit.command.ping.self", "ms" to ms))
        } else {
            player.sendMessage(player.translate("orbit.command.ping.other", "name" to target.username, "ms" to ms))
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
            player.sendMessage(player.translate("orbit.command.player_not_found", "name" to cmdArgs?.getOrNull(0).orEmpty()))
            return@onPlayerExecute
        }
        val enabled = if (target.getTag(GOD_TAG) == true) {
            target.removeTag(GOD_TAG)
            false
        } else {
            target.setTag(GOD_TAG, true)
            true
        }
        val stateKey = if (enabled) "orbit.command.god.enabled" else "orbit.command.god.disabled"
        if (target === player) {
            player.sendMessage(player.translate(stateKey + ".self"))
        } else {
            player.sendMessage(player.translate(stateKey + ".other", "name" to target.username))
            target.sendMessage(target.translate(stateKey + ".self"))
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
            player.sendMessage(player.translate("orbit.command.give.usage"))
            return@onPlayerExecute
        }
        val input = cmdArgs[0].lowercase()
        val key = if (input.contains(":")) input else "minecraft:$input"
        val material = Material.fromKey(key)
            ?: Material.values().firstOrNull { it.key().value().equals(input, ignoreCase = true) }
        if (material == null) {
            player.sendMessage(player.translate("orbit.command.give.unknown", "item" to cmdArgs[0]))
            return@onPlayerExecute
        }
        val amount = (cmdArgs.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, 6400)
        val target = targetOrSelf(player, cmdArgs, 2) ?: run {
            player.sendMessage(player.translate("orbit.command.player_not_found", "name" to cmdArgs[2]))
            return@onPlayerExecute
        }
        target.inventory.addItemStack(ItemStack.of(material, amount))
        val itemKey = material.key().value()
        val amountStr = amount.toString()
        if (target === player) {
            player.sendMessage(player.translate("orbit.command.give.self", "amount" to amountStr, "item" to itemKey))
        } else {
            player.sendMessage(player.translate("orbit.command.give.other", "amount" to amountStr, "item" to itemKey, "name" to target.username))
            target.sendMessage(target.translate("orbit.command.give.received", "amount" to amountStr, "item" to itemKey))
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
            player.sendMessage(player.translate("orbit.command.time.usage"))
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
            player.sendMessage(player.translate("orbit.command.time.invalid", "input" to cmdArgs[0]))
            return@onPlayerExecute
        }
        val instance = player.instance ?: return@onPlayerExecute
        instance.time = ticks
        player.sendMessage(player.translate("orbit.command.time.set", "ticks" to ticks.toString()))
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
            player.sendMessage(player.translate("orbit.command.weather.usage"))
            return@onPlayerExecute
        }
        val state = when (cmdArgs[0].lowercase()) {
            "clear", "sunny" -> WeatherState.SUNNY
            "rain", "rainy" -> WeatherState.RAINY
            "thunder", "thundering" -> WeatherState.THUNDERING
            else -> null
        }
        if (state == null) {
            player.sendMessage(player.translate("orbit.command.weather.unknown", "input" to cmdArgs[0]))
            return@onPlayerExecute
        }
        val instance = player.instance ?: return@onPlayerExecute
        WeatherController.setWeather(instance, state)
        player.sendMessage(player.translate("orbit.command.weather.set", "state" to state.name.lowercase()))
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
            player.sendMessage(player.translate("orbit.command.tphere.usage"))
            return@onPlayerExecute
        }
        val target = resolveOnline(cmdArgs[0]) ?: run {
            player.sendMessage(player.translate("orbit.command.player_not_found", "name" to cmdArgs[0]))
            return@onPlayerExecute
        }
        target.teleport(player.position)
        player.sendMessage(player.translate("orbit.command.tphere.summoned", "name" to target.username))
        target.sendMessage(target.translate("orbit.command.tphere.received", "name" to player.username))
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
            player.sendMessage(player.translate("orbit.command.invsee.usage"))
            return@onPlayerExecute
        }
        val target = resolveOnline(cmdArgs[0]) ?: run {
            player.sendMessage(player.translate("orbit.command.player_not_found", "name" to cmdArgs[0]))
            return@onPlayerExecute
        }
        val inv = target.inventory
        val g = gui(player.translateRaw("orbit.command.invsee.title", "name" to target.username), rows = 5) {
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
