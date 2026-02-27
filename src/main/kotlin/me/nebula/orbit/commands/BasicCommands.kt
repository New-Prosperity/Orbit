package me.nebula.orbit.commands

import me.nebula.orbit.utils.chat.sendMM
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.suggestPlayers
import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandManager
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val godPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

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
    ).forEach(commandManager::register)

    val global = MinecraftServer.getGlobalEventHandler()
    global.addListener(EntityDamageEvent::class.java) { event ->
        val entity = event.entity
        if (entity is Player && entity.uuid in godPlayers) {
            event.isCancelled = true
        }
    }
    global.addListener(PlayerDisconnectEvent::class.java) { event ->
        godPlayers.remove(event.player.uuid)
    }
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
        val enabled = if (target.uuid in godPlayers) {
            godPlayers.remove(target.uuid)
            false
        } else {
            godPlayers.add(target.uuid)
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
