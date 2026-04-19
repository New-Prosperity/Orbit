package me.nebula.orbit.notification

import me.nebula.gravity.notification.Notification
import me.nebula.gravity.notification.Priority
import net.kyori.adventure.text.Component
import net.minestom.server.item.ItemStack

interface TitleCapable {
    fun showTitle(title: OrbitNotification.Title)
}

interface ActionBarCapable {
    fun showActionBar(actionBar: OrbitNotification.ActionBar)
}

interface ToastCapable {
    fun showToast(toast: OrbitNotification.Toast)
}

sealed interface OrbitNotification : Notification {

    data class Title(
        val title: Component,
        val subtitle: Component? = null,
        val fadeInTicks: Int = 10,
        val stayTicks: Int = 60,
        val fadeOutTicks: Int = 10,
        override val priority: Priority = Priority.INFO,
    ) : OrbitNotification

    data class ActionBar(
        val message: Component,
        override val priority: Priority = Priority.INFO,
    ) : OrbitNotification

    data class Toast(
        val title: Component,
        val icon: ItemStack,
        val frame: ToastFrame = ToastFrame.TASK,
        override val priority: Priority = Priority.INFO,
    ) : OrbitNotification
}

enum class ToastFrame { TASK, CHALLENGE, GOAL }
