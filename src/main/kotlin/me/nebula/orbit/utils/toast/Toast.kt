package me.nebula.orbit.utils.toast

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.entity.Player
import net.minestom.server.advancements.FrameType
import net.minestom.server.advancements.Notification
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

private val miniMessage = MiniMessage.miniMessage()

enum class ToastFrame {
    TASK,
    CHALLENGE,
    GOAL;

    internal fun toMinestom(): FrameType = when (this) {
        TASK -> FrameType.TASK
        CHALLENGE -> FrameType.CHALLENGE
        GOAL -> FrameType.GOAL
    }
}

class ToastBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var title: Component = Component.empty()
    @PublishedApi internal var icon: ItemStack = ItemStack.of(Material.PAPER)
    @PublishedApi internal var frame: ToastFrame = ToastFrame.TASK
    @PublishedApi internal var sound: SoundEvent? = SoundEvent.UI_TOAST_CHALLENGE_COMPLETE
    @PublishedApi internal var volume: Float = 1f
    @PublishedApi internal var pitch: Float = 1f

    fun title(text: String) { title = miniMessage.deserialize(text) }
    fun title(component: Component) { title = component }
    fun icon(material: Material) { icon = ItemStack.of(material) }
    fun icon(item: ItemStack) { icon = item }
    fun frame(type: ToastFrame) { frame = type }
    fun task() { frame = ToastFrame.TASK }
    fun challenge() { frame = ToastFrame.CHALLENGE }
    fun goal() { frame = ToastFrame.GOAL }
    fun sound(event: SoundEvent?, volume: Float = 1f, pitch: Float = 1f) {
        sound = event; this.volume = volume; this.pitch = pitch
    }
    fun silent() { sound = null }
}

fun Player.showToast(block: ToastBuilder.() -> Unit) {
    val builder = ToastBuilder().apply(block)
    val notification = Notification(builder.title, builder.frame.toMinestom(), builder.icon)
    sendNotification(notification)
    builder.sound?.let { event ->
        playSound(Sound.sound(event.key(), Sound.Source.MASTER, builder.volume, builder.pitch))
    }
}

fun toast(block: ToastBuilder.() -> Unit): (Player) -> Unit {
    val builder = ToastBuilder().apply(block)
    val notification = Notification(builder.title, builder.frame.toMinestom(), builder.icon)
    val soundEvent = builder.sound
    val vol = builder.volume
    val pit = builder.pitch
    return { player ->
        player.sendNotification(notification)
        soundEvent?.let { player.playSound(Sound.sound(it.key(), Sound.Source.MASTER, vol, pit)) }
    }
}

fun Player.showToast(title: String, icon: Material = Material.PAPER, frame: ToastFrame = ToastFrame.TASK) {
    showToast {
        title(title)
        icon(icon)
        frame(frame)
    }
}
