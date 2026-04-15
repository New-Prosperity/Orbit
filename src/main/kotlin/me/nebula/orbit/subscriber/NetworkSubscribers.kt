package me.nebula.orbit.subscriber

import me.nebula.gravity.cache.CacheSlots
import me.nebula.gravity.cache.PlayerCache
import me.nebula.gravity.messaging.FriendOnlineMessage
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.PartyInviteMessage
import me.nebula.gravity.messaging.PlayerReportMessage
import me.nebula.gravity.messaging.PropertyUpdateMessage
import me.nebula.orbit.Orbit
import me.nebula.orbit.translation.translate
import me.nebula.orbit.translation.translateRaw
import me.nebula.orbit.utils.toast.ToastFrame
import me.nebula.orbit.utils.toast.showToast
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

fun installNetworkSubscribers(gameMode: String?) {
    NetworkMessenger.subscribe<PlayerReportMessage> { msg ->
        for (p in MinecraftServer.getConnectionManager().onlinePlayers) {
            val perms = PlayerCache.get(p.uuid)?.get(CacheSlots.PERMISSIONS) ?: continue
            if ("*" in perms || "staff.reports" in perms) {
                p.sendMessage(p.translate("orbit.report.staff_alert",
                    "reporter" to msg.reporterName,
                    "reported" to msg.reportedName,
                    "reason" to msg.reason,
                ))
            }
        }
    }

    NetworkMessenger.subscribe<PartyInviteMessage> { msg ->
        val invitee = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(msg.inviteeId) ?: return@subscribe
        invitee.showToast {
            title(invitee.translateRaw("orbit.party.invite_subtitle", "player" to msg.inviterName))
            icon(Material.PLAYER_HEAD)
            frame(ToastFrame.GOAL)
            sound(SoundEvent.UI_TOAST_CHALLENGE_COMPLETE, volume = 0.8f, pitch = 1.2f)
        }
    }

    NetworkMessenger.subscribe<FriendOnlineMessage> { msg ->
        val friendOnlineSound = Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP.key(), Sound.Source.MASTER, 0.6f, 1.5f)
        val connectionManager = MinecraftServer.getConnectionManager()
        for (friendId in msg.friendIds) {
            val p = connectionManager.getOnlinePlayerByUuid(friendId) ?: continue
            p.sendMessage(p.translate("orbit.friend.online", "player" to msg.playerName))
            p.playSound(friendOnlineSound)
        }
    }

    if (gameMode != null) {
        NetworkMessenger.subscribe<PropertyUpdateMessage> { msg ->
            if (msg.key.startsWith("MAINTENANCE_") && msg.value == "true") {
                val disabledMode = msg.key.removePrefix("MAINTENANCE_").lowercase()
                if (disabledMode == gameMode) {
                    for (p in MinecraftServer.getConnectionManager().onlinePlayers) {
                        p.sendMessage(Orbit.deserialize("orbit.mode.disabled_notice", Orbit.localeOf(p.uuid)))
                    }
                }
            }
        }
    }
}
