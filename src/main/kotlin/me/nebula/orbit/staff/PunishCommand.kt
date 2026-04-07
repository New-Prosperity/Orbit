package me.nebula.orbit.staff

import me.nebula.gravity.anticheat.unflag
import me.nebula.gravity.audit.AuditAction
import me.nebula.gravity.audit.AuditStore
import me.nebula.gravity.messaging.KickPlayerMessage
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.sanction.AddSanctionProcessor
import me.nebula.gravity.sanction.Sanction
import me.nebula.gravity.sanction.SanctionStore
import me.nebula.gravity.sanction.SanctionType
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.resolvePlayer
import me.nebula.orbit.utils.commandbuilder.suggestPlayers
import net.minestom.server.command.builder.Command

fun punishCommand(): Command = command("punish") {
    permission("staff.punish")
    stringArrayArgument("args")

    tabComplete { player, input ->
        val tokens = input.trimEnd().split(" ")
        if (tokens.size == 2) suggestPlayers(tokens.last(), player) else emptyList()
    }

    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            reply("orbit.punish.usage")
            return@onPlayerExecute
        }

        val resolved = resolvePlayer(cmdArgs[0], player) ?: resolvePlayer(cmdArgs[0])
        if (resolved == null) {
            reply("orbit.command.player_not_found", "name" to cmdArgs[0])
            return@onPlayerExecute
        }

        val (targetId, targetName) = resolved
        val reason = if (cmdArgs.size > 1) cmdArgs.drop(1).joinToString(" ") else "Anti-cheat violation"

        val sanction = Sanction(
            type = SanctionType.BAN,
            reason = reason,
            issuer = player.uuid,
            issuedAt = System.currentTimeMillis(),
            duration = 0L,
        )
        SanctionStore.executeOnKey(targetId, AddSanctionProcessor(sanction))

        unflag(targetId)

        AuditStore.log(
            actorId = player.uuid,
            actorName = player.username,
            action = AuditAction.ANTICHEAT_PUNISH,
            targetId = targetId,
            targetName = targetName,
            details = reason,
            source = "orbit",
        )

        NetworkMessenger.publish(KickPlayerMessage(targetId, reason))
        reply("orbit.punish.success", "player" to targetName, "reason" to reason)
    }
}
