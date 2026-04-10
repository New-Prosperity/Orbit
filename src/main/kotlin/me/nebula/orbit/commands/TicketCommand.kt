package me.nebula.orbit.commands

import me.nebula.gravity.messaging.InGameTicketMessage
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.player.PlayerStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.utils.commandbuilder.command
import net.minestom.server.command.builder.Command

fun ticketCommand(): Command = command("ticket") {
    aliases("support", "helpme")
    usage("orbit.ticket.usage")
    stringArgument("subject")

    onPlayerExecute {
        val subject = argOrNull("subject")
        if (subject == null || subject.length < 5) {
            reply("orbit.ticket.subject_too_short")
            return@onPlayerExecute
        }

        val playerData = PlayerStore.load(player.uuid)
        val discordId = playerData?.discordId ?: 0L

        NetworkMessenger.publish(InGameTicketMessage(
            playerId = player.uuid,
            playerName = player.username,
            discordId = discordId,
            subject = subject.take(100),
            serverName = Orbit.serverName,
            gameMode = Orbit.gameMode,
        ))

        reply("orbit.ticket.created")
    }
}
