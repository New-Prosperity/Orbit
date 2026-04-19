package me.nebula.orbit.guild

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.cache.CacheSlots
import me.nebula.gravity.cache.PlayerCache
import me.nebula.gravity.guild.AddGuildXpProcessor
import me.nebula.gravity.guild.GuildStore
import me.nebula.gravity.messaging.GuildLevelUpMessage
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.orbit.translation.translate
import net.minestom.server.MinecraftServer
import java.util.UUID
import me.nebula.gravity.translation.Keys

object GuildXpIntegration {

    private val logger = logger("GuildXp")
    private const val GAME_END_XP = 50L
    private const val GAME_BONUS_THRESHOLD = 3
    private const val GAME_BONUS_MULTIPLIER = 1.25
    private const val MISSION_COMPLETE_XP = 20L

    fun onGameEnd(playerIds: Collection<UUID>) {
        val guildGroups = mutableMapOf<Long, MutableList<UUID>>()
        for (playerId in playerIds) {
            val guildId = PlayerCache.get(playerId)?.get(CacheSlots.GUILD) ?: continue
            guildGroups.computeIfAbsent(guildId) { mutableListOf() }.add(playerId)
        }

        for ((guildId, members) in guildGroups) {
            val baseXp = GAME_END_XP * members.size
            val bonus = if (members.size >= GAME_BONUS_THRESHOLD) GAME_BONUS_MULTIPLIER else 1.0
            val totalXp = (baseXp * bonus).toLong()
            awardXp(guildId, totalXp)
        }
    }

    fun onMissionCompleted(playerId: UUID) {
        val guildId = PlayerCache.get(playerId)?.get(CacheSlots.GUILD) ?: return
        awardXp(guildId, MISSION_COMPLETE_XP)
    }

    fun onAchievementUnlocked(playerId: UUID, points: Int) {
        val guildId = PlayerCache.get(playerId)?.get(CacheSlots.GUILD) ?: return
        awardXp(guildId, points.toLong())
    }

    private fun awardXp(guildId: Long, xp: Long) {
        val result = GuildStore.executeOnKey(guildId, AddGuildXpProcessor(xp)) ?: return
        val guild = GuildStore.load(guildId) ?: return

        logger.info { "Guild '${guild.name}' leveled up: ${result.oldLevel} → ${result.newLevel}" }

        NetworkMessenger.publish(GuildLevelUpMessage(
            guildId = guildId,
            guildName = guild.name,
            newLevel = result.newLevel,
            memberIds = guild.members.keys.toList(),
        ))

        for (memberId in guild.members.keys) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(memberId) ?: continue
            player.sendMessage(player.translate(Keys.Orbit.Guild.LevelUp,
                "guild" to guild.name,
                "level" to result.newLevel.toString(),
            ))
        }
    }
}
