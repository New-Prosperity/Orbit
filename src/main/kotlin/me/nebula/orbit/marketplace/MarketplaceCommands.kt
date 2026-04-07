package me.nebula.orbit.marketplace

import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.commandbuilder.command
import net.minestom.server.command.builder.Command

fun marketplaceCommand(): Command = command("marketplace") {
    aliases("market", "mp")
    onPlayerExecute {
        if (Orbit.gameMode != null) {
            reply("orbit.marketplace.hub_only")
            return@onPlayerExecute
        }
        MarketplaceMenu.openMain(player)
    }
}

fun sellCommand(): Command = command("sell") {
    onPlayerExecute {
        if (Orbit.gameMode != null) {
            reply("orbit.marketplace.hub_only")
            return@onPlayerExecute
        }
        MarketplaceMenu.openSellSelect(player)
    }
}
