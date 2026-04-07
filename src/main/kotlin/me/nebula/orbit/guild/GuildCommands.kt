package me.nebula.orbit.guild

import me.nebula.gravity.audit.AuditAction
import me.nebula.gravity.audit.AuditStore
import me.nebula.gravity.cache.CacheSlots
import me.nebula.gravity.cache.PlayerCache
import me.nebula.gravity.economy.PurchaseCosmeticProcessor
import me.nebula.gravity.economy.EconomyStore
import me.nebula.gravity.guild.AddGuildMemberProcessor
import me.nebula.gravity.guild.GuildInvite
import me.nebula.gravity.guild.GuildInviteStore
import me.nebula.gravity.guild.GuildLookupStore
import me.nebula.gravity.guild.GuildRole
import me.nebula.gravity.guild.GuildStore
import me.nebula.gravity.guild.PromoteGuildMemberProcessor
import me.nebula.gravity.guild.RemoveGuildMemberProcessor
import me.nebula.gravity.guild.TransferGuildOwnerProcessor
import me.nebula.gravity.guild.guildByNamePredicate
import me.nebula.gravity.guild.guildByTagPredicate
import me.nebula.gravity.property.PropertyStore
import me.nebula.gravity.property.intProperty
import me.nebula.orbit.Orbit
import me.nebula.orbit.guildId
import me.nebula.orbit.level
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.commandbuilder.CommandExecutionContext
import me.nebula.orbit.utils.commandbuilder.command
import me.nebula.orbit.utils.commandbuilder.resolvePlayer
import me.nebula.orbit.utils.commandbuilder.suggestPlayers
import me.nebula.orbit.utils.gui.confirmGui
import me.nebula.orbit.utils.gui.openGui
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command

private val GUILD_CREATE_COST = intProperty("GUILD_CREATE_COST", default = 1000)
private val GUILD_MIN_LEVEL = intProperty("GUILD_MIN_LEVEL", default = 10)

private val NAME_REGEX = Regex("^[a-zA-Z0-9 ]{3,16}$")
private val TAG_REGEX = Regex("^[A-Z]{3,5}$")

fun guildCommand(): Command = command("guild") {
    stringArrayArgument("args")

    tabComplete { player, input ->
        val tokens = input.trimEnd().split(" ")
        when (tokens.size) {
            2 -> listOf("create", "invite", "kick", "leave", "promote", "demote", "transfer", "disband", "info", "list", "settings", "accept", "deny")
                .filter { it.startsWith(tokens[1], ignoreCase = true) }
            3 -> when (tokens[1].lowercase()) {
                "invite", "kick", "promote", "demote", "transfer" -> suggestPlayers(tokens[2], player)
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    onPlayerExecute {
        val cmdArgs = args.get("args") as? Array<String>
        if (cmdArgs.isNullOrEmpty()) {
            reply("orbit.guild.usage")
            return@onPlayerExecute
        }

        when (cmdArgs[0].lowercase()) {
            "create" -> handleCreate(cmdArgs)
            "invite" -> handleInvite(cmdArgs)
            "kick" -> handleKick(cmdArgs)
            "leave" -> handleLeave()
            "promote" -> handlePromote(cmdArgs)
            "demote" -> handleDemote(cmdArgs)
            "transfer" -> handleTransfer(cmdArgs)
            "disband" -> handleDisband()
            "info" -> handleInfo(cmdArgs)
            "list" -> GuildMenu.openGuildList(player)
            "settings" -> handleSettings()
            "accept" -> handleAccept()
            "deny" -> handleDeny()
            else -> reply("orbit.guild.usage")
        }
    }
}

private fun CommandExecutionContext.handleCreate(args: Array<String>) {
    if (Orbit.gameMode != null) { reply("orbit.guild.hub_only"); return }
    if (args.size < 3) { reply("orbit.guild.create.usage"); return }

    if (player.guildId != null) { reply("orbit.guild.already_in_guild"); return }

    val tag = args.last().uppercase()
    val name = args.drop(1).dropLast(1).joinToString(" ")

    if (name.isBlank()) { reply("orbit.guild.create.usage"); return }
    if (!NAME_REGEX.matches(name)) { reply("orbit.guild.create.invalid_name"); return }
    if (!TAG_REGEX.matches(tag)) { reply("orbit.guild.create.invalid_tag"); return }

    val minLevel = PropertyStore[GUILD_MIN_LEVEL]
    if (player.level < minLevel) {
        reply("orbit.guild.create.level_required", "level" to minLevel.toString())
        return
    }

    val existingByName = GuildStore.query(guildByNamePredicate(name))
    if (existingByName.any { it.name.equals(name, ignoreCase = true) }) {
        reply("orbit.guild.create.name_taken")
        return
    }
    if (GuildStore.query(guildByTagPredicate(tag)).isNotEmpty()) {
        reply("orbit.guild.create.tag_taken")
        return
    }

    val cost = PropertyStore[GUILD_CREATE_COST].toDouble()
    if (cost > 0) {
        val paid = EconomyStore.executeOnKey(player.uuid, PurchaseCosmeticProcessor("coins", cost))
        if (!paid) { reply("orbit.guild.create.insufficient_funds", "cost" to cost.toInt().toString()); return }
    }

    val (guildId, _) = GuildStore.record(name, tag, player.uuid)
    GuildLookupStore.save(player.uuid, guildId)
    PlayerCache.refresh(player.uuid, CacheSlots.GUILD)

    AuditStore.log(
        actorId = player.uuid, actorName = player.username,
        action = AuditAction.GUILD_CREATE,
        details = "Created guild '$name' [$tag]", source = "orbit",
    )

    reply("orbit.guild.create.success", "name" to name, "tag" to tag)
}

private fun CommandExecutionContext.handleInvite(args: Array<String>) {
    if (Orbit.gameMode != null) { reply("orbit.guild.hub_only"); return }
    if (args.size < 2) { reply("orbit.guild.invite.usage"); return }

    val guildId = player.guildId ?: run { reply("orbit.guild.not_in_guild"); return }
    val guild = GuildStore.load(guildId) ?: run { reply("orbit.guild.not_in_guild"); return }
    val role = guild.members[player.uuid] ?: run { reply("orbit.guild.not_in_guild"); return }

    if (role == GuildRole.MEMBER) { reply("orbit.guild.no_permission"); return }

    val resolved = resolvePlayer(args[1], player)
    if (resolved == null) { reply("orbit.command.player_not_found", "name" to args[1]); return }

    val (targetId, targetName) = resolved
    if (targetId == player.uuid) { reply("orbit.guild.invite.self"); return }

    val target = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(targetId)
    if (target == null) { reply("orbit.guild.invite.player_offline"); return }

    if (GuildLookupStore.exists(targetId)) { reply("orbit.guild.invite.already_in_guild", "player" to targetName); return }
    if (GuildInviteStore.exists(targetId)) { reply("orbit.guild.invite.already_invited", "player" to targetName); return }

    GuildInviteStore.save(targetId, GuildInvite(guildId, player.username, System.currentTimeMillis()))

    AuditStore.log(
        actorId = player.uuid, actorName = player.username,
        action = AuditAction.GUILD_INVITE, targetId = targetId, targetName = targetName,
        details = "Invited to guild '${guild.name}'", source = "orbit",
    )

    reply("orbit.guild.invite.sent", "player" to targetName)
    target.sendMessage(target.translate("orbit.guild.invite.received", "guild" to guild.name, "player" to player.username))
}

private fun CommandExecutionContext.handleKick(args: Array<String>) {
    if (Orbit.gameMode != null) { reply("orbit.guild.hub_only"); return }
    if (args.size < 2) { reply("orbit.guild.kick.usage"); return }

    val guildId = player.guildId ?: run { reply("orbit.guild.not_in_guild"); return }
    val guild = GuildStore.load(guildId) ?: run { reply("orbit.guild.not_in_guild"); return }
    val myRole = guild.members[player.uuid] ?: run { reply("orbit.guild.not_in_guild"); return }
    if (myRole == GuildRole.MEMBER) { reply("orbit.guild.no_permission"); return }

    val resolved = resolvePlayer(args[1], player) ?: resolvePlayer(args[1])
    if (resolved == null) { reply("orbit.command.player_not_found", "name" to args[1]); return }
    val (targetId, targetName) = resolved

    if (targetId == player.uuid) { reply("orbit.guild.kick.self"); return }
    val targetRole = guild.members[targetId] ?: run { reply("orbit.guild.kick.not_member", "player" to targetName); return }
    if (myRole == GuildRole.OFFICER && targetRole != GuildRole.MEMBER) { reply("orbit.guild.no_permission"); return }

    GuildStore.executeOnKey(guildId, RemoveGuildMemberProcessor(targetId))
    GuildLookupStore.delete(targetId)
    PlayerCache.refresh(targetId, CacheSlots.GUILD)

    val targetPlayer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(targetId)
    targetPlayer?.sendMessage(targetPlayer.translate("orbit.guild.kick.notification", "guild" to guild.name))

    AuditStore.log(
        actorId = player.uuid, actorName = player.username,
        action = AuditAction.GUILD_KICK, targetId = targetId, targetName = targetName,
        details = "Kicked from guild '${guild.name}'", source = "orbit",
    )

    reply("orbit.guild.kick.success", "player" to targetName)
}

private fun CommandExecutionContext.handleLeave() {
    val guildId = player.guildId ?: run { reply("orbit.guild.not_in_guild"); return }
    val guild = GuildStore.load(guildId) ?: run { reply("orbit.guild.not_in_guild"); return }

    if (guild.ownerId == player.uuid) {
        reply("orbit.guild.leave.owner")
        return
    }

    GuildStore.executeOnKey(guildId, RemoveGuildMemberProcessor(player.uuid))
    GuildLookupStore.delete(player.uuid)
    PlayerCache.refresh(player.uuid, CacheSlots.GUILD)

    reply("orbit.guild.leave.success", "guild" to guild.name)
}

private fun CommandExecutionContext.handlePromote(args: Array<String>) {
    if (args.size < 2) { reply("orbit.guild.promote.usage"); return }

    val guildId = player.guildId ?: run { reply("orbit.guild.not_in_guild"); return }
    val guild = GuildStore.load(guildId) ?: run { reply("orbit.guild.not_in_guild"); return }
    if (guild.ownerId != player.uuid) { reply("orbit.guild.no_permission"); return }

    val resolved = resolvePlayer(args[1], player) ?: resolvePlayer(args[1])
    if (resolved == null) { reply("orbit.command.player_not_found", "name" to args[1]); return }
    val (targetId, targetName) = resolved

    val targetRole = guild.members[targetId] ?: run { reply("orbit.guild.not_member", "player" to targetName); return }
    if (targetRole != GuildRole.MEMBER) { reply("orbit.guild.promote.already_officer", "player" to targetName); return }

    GuildStore.executeOnKey(guildId, PromoteGuildMemberProcessor(targetId, GuildRole.OFFICER))

    AuditStore.log(
        actorId = player.uuid, actorName = player.username,
        action = AuditAction.GUILD_PROMOTE, targetId = targetId, targetName = targetName,
        details = "Promoted to Officer in '${guild.name}'", source = "orbit",
    )

    reply("orbit.guild.promote.success", "player" to targetName)
}

private fun CommandExecutionContext.handleDemote(args: Array<String>) {
    if (args.size < 2) { reply("orbit.guild.demote.usage"); return }

    val guildId = player.guildId ?: run { reply("orbit.guild.not_in_guild"); return }
    val guild = GuildStore.load(guildId) ?: run { reply("orbit.guild.not_in_guild"); return }
    if (guild.ownerId != player.uuid) { reply("orbit.guild.no_permission"); return }

    val resolved = resolvePlayer(args[1], player) ?: resolvePlayer(args[1])
    if (resolved == null) { reply("orbit.command.player_not_found", "name" to args[1]); return }
    val (targetId, targetName) = resolved

    val targetRole = guild.members[targetId] ?: run { reply("orbit.guild.not_member", "player" to targetName); return }
    if (targetRole != GuildRole.OFFICER) { reply("orbit.guild.demote.not_officer", "player" to targetName); return }

    GuildStore.executeOnKey(guildId, PromoteGuildMemberProcessor(targetId, GuildRole.MEMBER))
    reply("orbit.guild.demote.success", "player" to targetName)
}

private fun CommandExecutionContext.handleTransfer(args: Array<String>) {
    if (args.size < 2) { reply("orbit.guild.transfer.usage"); return }

    val guildId = player.guildId ?: run { reply("orbit.guild.not_in_guild"); return }
    val guild = GuildStore.load(guildId) ?: run { reply("orbit.guild.not_in_guild"); return }
    if (guild.ownerId != player.uuid) { reply("orbit.guild.no_permission"); return }

    val resolved = resolvePlayer(args[1], player) ?: resolvePlayer(args[1])
    if (resolved == null) { reply("orbit.command.player_not_found", "name" to args[1]); return }
    val (targetId, targetName) = resolved

    if (targetId == player.uuid) { reply("orbit.guild.transfer.self"); return }
    if (targetId !in guild.members) { reply("orbit.guild.not_member", "player" to targetName); return }

    GuildStore.executeOnKey(guildId, TransferGuildOwnerProcessor(targetId))

    AuditStore.log(
        actorId = player.uuid, actorName = player.username,
        action = AuditAction.GUILD_TRANSFER, targetId = targetId, targetName = targetName,
        details = "Transferred ownership of '${guild.name}'", source = "orbit",
    )

    reply("orbit.guild.transfer.success", "player" to targetName)
}

private fun CommandExecutionContext.handleDisband() {
    if (Orbit.gameMode != null) { reply("orbit.guild.hub_only"); return }

    val guildId = player.guildId ?: run { reply("orbit.guild.not_in_guild"); return }
    val guild = GuildStore.load(guildId) ?: run { reply("orbit.guild.not_in_guild"); return }
    if (guild.ownerId != player.uuid) { reply("orbit.guild.no_permission"); return }

    val confirm = confirmGui(
        title = player.translateRaw("orbit.guild.disband.confirm_title"),
        message = player.translateRaw("orbit.guild.disband.confirm_message", "guild" to guild.name),
        onConfirm = { p ->
            val freshGuild = GuildStore.load(guildId)
            if (freshGuild == null) { p.closeInventory(); return@confirmGui }

            for (memberId in freshGuild.members.keys) {
                GuildLookupStore.delete(memberId)
                PlayerCache.refresh(memberId, CacheSlots.GUILD)
            }
            GuildStore.delete(guildId)

            AuditStore.log(
                actorId = p.uuid, actorName = p.username,
                action = AuditAction.GUILD_DISBAND,
                details = "Disbanded guild '${freshGuild.name}' [${freshGuild.tag}] (${freshGuild.members.size} members)",
                source = "orbit",
            )

            p.closeInventory()
            p.sendMessage(p.translate("orbit.guild.disband.success", "guild" to freshGuild.name))
        },
        onCancel = { it.closeInventory() },
    )
    player.openGui(confirm)
}

private fun CommandExecutionContext.handleInfo(args: Array<String>) {
    val guildData = if (args.size >= 2) {
        val name = args.drop(1).joinToString(" ")
        GuildStore.query(guildByNamePredicate(name)).firstOrNull()
    } else {
        val guildId = player.guildId ?: run { reply("orbit.guild.not_in_guild"); return }
        GuildStore.load(guildId)
    }

    if (guildData == null) { reply("orbit.guild.not_found"); return }
    GuildMenu.openInfo(player, guildData)
}

private fun CommandExecutionContext.handleSettings() {
    if (Orbit.gameMode != null) { reply("orbit.guild.hub_only"); return }

    val guildId = player.guildId ?: run { reply("orbit.guild.not_in_guild"); return }
    val guild = GuildStore.load(guildId) ?: run { reply("orbit.guild.not_in_guild"); return }
    val role = guild.members[player.uuid] ?: run { reply("orbit.guild.not_in_guild"); return }
    if (role == GuildRole.MEMBER) { reply("orbit.guild.no_permission"); return }

    GuildMenu.openSettings(player, guildId, guild)
}

private fun CommandExecutionContext.handleAccept() {
    val invite = GuildInviteStore.load(player.uuid)
    if (invite == null) { reply("orbit.guild.invite.none"); return }
    if (player.guildId != null) { reply("orbit.guild.already_in_guild"); return }

    val guild = GuildStore.load(invite.guildId)
    if (guild == null) {
        GuildInviteStore.delete(player.uuid)
        reply("orbit.guild.invite.guild_disbanded")
        return
    }

    GuildInviteStore.delete(player.uuid)

    val added = GuildStore.executeOnKey(invite.guildId, AddGuildMemberProcessor(player.uuid))
    if (!added) { reply("orbit.guild.invite.guild_full"); return }

    GuildLookupStore.save(player.uuid, invite.guildId)
    PlayerCache.refresh(player.uuid, CacheSlots.GUILD)

    reply("orbit.guild.invite.accepted", "guild" to guild.name)
}

private fun CommandExecutionContext.handleDeny() {
    val invite = GuildInviteStore.load(player.uuid)
    if (invite == null) { reply("orbit.guild.invite.none"); return }

    GuildInviteStore.delete(player.uuid)
    reply("orbit.guild.invite.denied")
}
