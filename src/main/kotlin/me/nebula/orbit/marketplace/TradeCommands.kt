package me.nebula.orbit.marketplace

import me.nebula.orbit.acceptsTradeRequests
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.resolvePlayer
import me.nebula.orbit.utils.commandbuilder.suggestPlayers
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command

fun tradeCommand(): Command = command("trade") {
    stringArrayArgument("args")
    tabComplete { player, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last(), player) else emptyList()
    }
    onPlayerExecute {
        if (Orbit.gameMode != null) {
            reply("orbit.trade.hub_only")
            return@onPlayerExecute
        }

        val cmdArgs = args.get("args") as? Array<String>

        if (TradeManager.hasPendingRequest(player.uuid)) {
            if (cmdArgs != null && cmdArgs.firstOrNull()?.equals("deny", ignoreCase = true) == true) {
                TradeManager.denyRequest(player)
            } else {
                TradeManager.acceptRequest(player)
            }
            return@onPlayerExecute
        }

        if (TradeManager.isTrading(player.uuid)) {
            reply("orbit.trade.already_trading")
            return@onPlayerExecute
        }

        if (cmdArgs.isNullOrEmpty()) {
            reply("orbit.trade.usage")
            return@onPlayerExecute
        }

        val resolved = resolvePlayer(cmdArgs[0], player)
        if (resolved == null) {
            reply("orbit.command.player_not_found", "name" to cmdArgs[0])
            return@onPlayerExecute
        }

        val (targetUuid, _) = resolved
        val target = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(targetUuid)
        if (target == null) {
            reply("orbit.trade.player_offline")
            return@onPlayerExecute
        }

        if (!target.acceptsTradeRequests) {
            reply("orbit.trade.disabled", "player" to target.username)
            return@onPlayerExecute
        }

        TradeManager.sendRequest(player, target)
    }
}
