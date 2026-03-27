package me.nebula.orbit

import me.nebula.gravity.cache.CachedPlayer
import me.nebula.gravity.cache.PlayerCache
import me.nebula.gravity.cosmetic.CosmeticPlayerData
import me.nebula.gravity.economy.EconomyData
import me.nebula.gravity.leveling.LevelData
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
    get() = cached.rank

val Player.rankName: String
    get() = rank?.name ?: "Member"

val Player.rankPrefix: String
    get() = rank?.prefix ?: ""

val Player.rankColor: String
    get() = rank?.color ?: "white"

val Player.rankWeight: Int
    get() = rank?.weight ?: 100

val Player.playerData: PlayerData?
    get() = cached.player

val Player.levelData: LevelData
    get() = cached.level

val Player.level: Int
    get() = cached.level.level

val Player.prestige: Int
    get() = cached.level.prestige

val Player.economyData: EconomyData
    get() = cached.economy

val Player.statsData: StatsData
    get() = cached.stats

val Player.preferencesData: PreferenceData
    get() = cached.preferences

val Player.acceptsFriendRequests: Boolean get() = cached.preferences.friendRequests
val Player.acceptsPrivateMessages: Boolean get() = cached.preferences.privateMessages
val Player.acceptsPartyInvites: Boolean get() = cached.preferences.partyInvites
val Player.acceptsDuelRequests: Boolean get() = cached.preferences.duelRequests
val Player.acceptsTradeRequests: Boolean get() = cached.preferences.tradeRequests
val Player.profileVisibility: String get() = cached.preferences.profileVisibility
val Player.appearsOffline: Boolean get() = cached.preferences.appearOffline
val Player.lastSeenVisible: Boolean get() = cached.preferences.lastSeenVisible
val Player.statsVisible: Boolean get() = cached.preferences.statsVisible
val Player.streamerMode: Boolean get() = cached.preferences.streamerMode
val Player.staffAutoVanish: Boolean get() = cached.preferences.staffAutoVanish
val Player.cosmeticDisplay: String get() = cached.preferences.cosmeticDisplay

val Player.cosmeticsData: CosmeticPlayerData
    get() = cached.cosmetics

fun Player.balance(currency: String): Double =
    cached.economy.balances[currency] ?: 0.0

val Player.coins: Double
    get() = balance("coins")

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
