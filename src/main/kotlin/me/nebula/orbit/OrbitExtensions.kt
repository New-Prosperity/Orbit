package me.nebula.orbit

import me.nebula.gravity.cache.CachedPlayer
import me.nebula.gravity.cache.CacheSlots
import me.nebula.gravity.cache.PlayerCache
import me.nebula.gravity.guild.GuildData
import me.nebula.gravity.guild.GuildLevelFormula
import me.nebula.gravity.guild.GuildStore
import me.nebula.gravity.cosmetic.CosmeticPlayerData
import me.nebula.gravity.economy.EconomyData
import me.nebula.gravity.leveling.LevelData
import me.nebula.gravity.nick.NickData
import me.nebula.gravity.player.PlayerData
import me.nebula.gravity.player.PreferenceData
import me.nebula.gravity.rank.RankData
import me.nebula.gravity.session.SessionStore
import me.nebula.gravity.stats.StatsData
import me.nebula.orbit.nick.NickManager
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import java.util.UUID

val Player.cached: CachedPlayer
    get() = PlayerCache.get(uuid) ?: PlayerCache.getOrLoad(uuid)

val Player.localeCode: String
    get() = Orbit.localeOf(uuid)

val UUID.localeCode: String
    get() = Orbit.localeOf(this)

val Player.rank: RankData?
    get() = cached[CacheSlots.RANK]

val Player.rankName: String
    get() = rank?.name ?: "Member"

val Player.rankDisplayName: String
    get() = rank?.let { Orbit.translations.get("rank.${it.name}.display_name", localeCode) } ?: ""

val Player.rankPrefix: String
    get() = rank?.let { Orbit.translations.get("rank.${it.name}.prefix", localeCode) } ?: ""

val Player.rankSuffix: String
    get() = rank?.let { Orbit.translations.get("rank.${it.name}.suffix", localeCode) } ?: ""

val Player.rankBadge: String
    get() = rank?.let { Orbit.translations.get("rank.${it.name}.badge", localeCode) } ?: ""

val Player.rankColor: String
    get() = rank?.color ?: "white"

val Player.rankWeight: Int
    get() = rank?.weight ?: 100

val Player.playerData: PlayerData?
    get() = cached[CacheSlots.PLAYER]

val Player.levelData: LevelData
    get() = cached[CacheSlots.LEVEL] ?: LevelData()

val Player.level: Int
    get() = levelData.level

val Player.prestige: Int
    get() = levelData.prestige

val Player.economyData: EconomyData
    get() = cached[CacheSlots.ECONOMY] ?: EconomyData()

val Player.statsData: StatsData
    get() = cached[CacheSlots.STATS] ?: StatsData()

val Player.preferencesData: PreferenceData
    get() = cached[CacheSlots.PREFERENCES] ?: PreferenceData()

val Player.acceptsFriendRequests: Boolean get() = preferencesData.friendRequests
val Player.acceptsPrivateMessages: Boolean get() = preferencesData.privateMessages
val Player.acceptsPartyInvites: Boolean get() = preferencesData.partyInvites
val Player.acceptsDuelRequests: Boolean get() = preferencesData.duelRequests
val Player.acceptsTradeRequests: Boolean get() = preferencesData.tradeRequests
val Player.profileVisibility: String get() = preferencesData.profileVisibility
val Player.appearsOffline: Boolean get() = preferencesData.appearOffline
val Player.lastSeenVisible: Boolean get() = preferencesData.lastSeenVisible
val Player.statsVisible: Boolean get() = preferencesData.statsVisible
val Player.streamerMode: Boolean get() = preferencesData.streamerMode
val Player.staffAutoVanish: Boolean get() = preferencesData.staffAutoVanish
val Player.cosmeticDisplay: String get() = preferencesData.cosmeticDisplay

val Player.cosmeticsData: CosmeticPlayerData
    get() = cached[CacheSlots.COSMETICS] ?: CosmeticPlayerData()

val Player.nickData: NickData?
    get() = cached[CacheSlots.NICK]

fun Player.balance(currency: String): Double =
    economyData.balances[currency] ?: 0.0

val Player.coins: Double
    get() = balance("coins")

fun Player.hasCachedPermission(permission: String): Boolean {
    val perms = PlayerCache.get(uuid)?.get(CacheSlots.PERMISSIONS) ?: return false
    return "*" in perms || permission in perms
}

val Player.guildId: Long?
    get() = cached[CacheSlots.GUILD]

val Player.guildData: GuildData?
    get() = guildId?.let { GuildStore.load(it) }

val Player.guildTag: String
    get() = guildData?.let { "[${it.tag}] " } ?: ""

val Player.guildTagColored: String
    get() {
        val data = guildData ?: return ""
        val color = GuildLevelFormula.tagColor(data.level)
        return "<$color>[${data.tag}]</$color> "
    }

val Player.isNicked: Boolean
    get() = NickManager.isNicked(this)

val Player.displayUsername: String
    get() = NickManager.displayName(this)

val Player.isOnline: Boolean
    get() = SessionStore.exists(uuid)

fun Player.deserializeTranslation(key: String): Component =
    Orbit.deserialize(key, localeCode)

fun UUID.deserializeTranslation(key: String): Component =
    Orbit.deserialize(key, localeCode)
