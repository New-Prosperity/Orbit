package me.nebula.orbit.notification

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.notification.Notification
import me.nebula.gravity.notification.NotificationBundle
import me.nebula.gravity.notification.PlatformNotificationBridge
import me.nebula.gravity.notification.Priority
import me.nebula.gravity.user.NebulaUser
import net.kyori.adventure.text.Component
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

object OrbitNotifications {

    private val log = logger("OrbitNotifications")

    fun install() {
        PlatformNotificationBridge.register { user, notification ->
            when (notification) {
                is OrbitNotification.Title -> {
                    (user as? TitleCapable)?.showTitle(notification)?.let { true }
                        ?: run { user.sendMessage(flattenTitle(notification)); true }
                }
                is OrbitNotification.ActionBar -> {
                    (user as? ActionBarCapable)?.showActionBar(notification)?.let { true }
                        ?: run { user.sendMessage(notification.message); true }
                }
                is OrbitNotification.Toast -> {
                    (user as? ToastCapable)?.showToast(notification)?.let { true }
                        ?: run { user.sendMessage(notification.title); true }
                }
                else -> false
            }
        }
        log.info { "Orbit notification bridge installed" }
    }

    private fun flattenTitle(title: OrbitNotification.Title): Component {
        val subtitle = title.subtitle
        return if (subtitle != null) title.title.append(Component.newline()).append(subtitle) else title.title
    }
}

fun NotificationBundle.title(
    title: Component,
    subtitle: Component? = null,
    fadeInTicks: Int = 10,
    stayTicks: Int = 60,
    fadeOutTicks: Int = 10,
    priority: Priority = Priority.INFO,
) {
    +OrbitNotification.Title(title, subtitle, fadeInTicks, stayTicks, fadeOutTicks, priority)
}

fun NotificationBundle.actionBar(message: Component, priority: Priority = Priority.INFO) {
    +OrbitNotification.ActionBar(message, priority)
}

fun NotificationBundle.toast(
    title: Component,
    icon: ItemStack,
    frame: ToastFrame = ToastFrame.TASK,
    priority: Priority = Priority.INFO,
) {
    +OrbitNotification.Toast(title, icon, frame, priority)
}

fun NotificationBundle.toast(
    title: Component,
    iconMaterial: Material = Material.PAPER,
    frame: ToastFrame = ToastFrame.TASK,
    priority: Priority = Priority.INFO,
) {
    +OrbitNotification.Toast(title, ItemStack.of(iconMaterial), frame, priority)
}
