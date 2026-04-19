package me.nebula.orbit.user

import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.TransferPlayerMessage
import me.nebula.gravity.notification.Notification
import me.nebula.gravity.notification.SoundCapable
import me.nebula.gravity.rank.RankManager
import me.nebula.gravity.user.Kickable
import me.nebula.gravity.user.NebulaUser
import me.nebula.gravity.user.ServerSwitchable
import me.nebula.gravity.user.buildIdentityPointers
import me.nebula.orbit.displayUsername
import me.nebula.orbit.localeCode
import me.nebula.orbit.notification.ActionBarCapable
import me.nebula.orbit.notification.OrbitNotification
import me.nebula.orbit.notification.TitleCapable
import me.nebula.orbit.notification.ToastCapable
import me.nebula.orbit.notification.ToastFrame
import net.kyori.adventure.key.Key
import net.kyori.adventure.pointer.Pointers
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.Title.Times
import net.minestom.server.advancements.FrameType
import net.minestom.server.advancements.Notification as MinestomNotification
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.inventory.Inventory
import java.time.Duration
import java.util.Locale
import java.util.UUID

class OrbitOnlineUser(val player: Player) :
    NebulaUser.Online,
    Kickable,
    ServerSwitchable,
    Locatable,
    GuiCapable,
    TitleCapable,
    ActionBarCapable,
    ToastCapable,
    SoundCapable {

    override val uuid: UUID get() = player.uuid
    override val username: String get() = player.username
    override val displayName: Component get() = Component.text(player.displayUsername)

    override val locale: Locale
        get() = Locale.forLanguageTag(player.localeCode)

    override fun pointers(): Pointers = buildIdentityPointers(
        uuid = uuid,
        name = username,
        displayName = displayName,
        locale = locale,
    )

    override fun sendMessage(message: Component) {
        player.sendMessage(message)
    }

    override fun hasPermission(permission: String): Boolean =
        RankManager.hasPermission(uuid, permission)

    override fun kick(reason: Component) {
        player.kick(reason)
    }

    override fun switchServer(serverName: String) {
        NetworkMessenger.publish(TransferPlayerMessage(uuid, serverName))
    }

    override val instance: Instance? get() = player.instance
    override val position: Pos get() = player.position
    override fun teleport(target: Pos) { player.teleport(target) }

    override fun openInventory(inventory: Inventory) { player.openInventory(inventory) }
    override fun closeInventory() { player.closeInventory() }

    override fun showTitle(title: OrbitNotification.Title) {
        val times = Times.times(
            Duration.ofMillis(title.fadeInTicks * 50L),
            Duration.ofMillis(title.stayTicks * 50L),
            Duration.ofMillis(title.fadeOutTicks * 50L),
        )
        player.showTitle(Title.title(title.title, title.subtitle ?: Component.empty(), times))
    }

    override fun showActionBar(actionBar: OrbitNotification.ActionBar) {
        player.sendActionBar(actionBar.message)
    }

    override fun showToast(toast: OrbitNotification.Toast) {
        player.sendNotification(MinestomNotification(toast.title, toast.frame.toMinestom(), toast.icon))
    }

    override fun playSound(sound: Notification.Sound) {
        player.playSound(Sound.sound(Key.key(sound.soundId), Sound.Source.MASTER, sound.volume, sound.pitch))
    }

    override fun equals(other: Any?): Boolean = other is NebulaUser.Online && other.uuid == uuid
    override fun hashCode(): Int = uuid.hashCode()
    override fun toString(): String = "OrbitOnlineUser(uuid=$uuid, name=$username)"
}

private fun ToastFrame.toMinestom(): FrameType = when (this) {
    ToastFrame.TASK -> FrameType.TASK
    ToastFrame.CHALLENGE -> FrameType.CHALLENGE
    ToastFrame.GOAL -> FrameType.GOAL
}

fun Player.asNebulaUser(): OrbitOnlineUser = OrbitOnlineUser(this)
