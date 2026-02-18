package me.nebula.orbit.utils.notification

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.EnumSet

private val miniMessage = MiniMessage.miniMessage()

enum class NotificationChannel {
    CHAT,
    ACTION_BAR,
    TITLE,
    BOSS_BAR,
    SOUND,
}

data class Notification(
    val title: String,
    val message: String,
    val subtitle: String,
    val channels: Set<NotificationChannel>,
    val soundEvent: SoundEvent,
    val soundVolume: Float,
    val soundPitch: Float,
    val titleFadeIn: Duration,
    val titleStay: Duration,
    val titleFadeOut: Duration,
    val bossBarColor: BossBar.Color,
    val bossBarOverlay: BossBar.Overlay,
    val bossBarDuration: Duration,
) {

    fun send(player: Player) {
        channels.forEach { channel ->
            when (channel) {
                NotificationChannel.CHAT -> {
                    val text = message.ifEmpty { title }
                    player.sendMessage(miniMessage.deserialize(text))
                }
                NotificationChannel.ACTION_BAR -> {
                    val text = message.ifEmpty { title }
                    player.sendActionBar(miniMessage.deserialize(text))
                }
                NotificationChannel.TITLE -> {
                    val titleComponent = if (title.isNotEmpty()) miniMessage.deserialize(title) else Component.empty()
                    val subtitleComponent = if (subtitle.isNotEmpty()) miniMessage.deserialize(subtitle)
                    else if (message.isNotEmpty()) miniMessage.deserialize(message)
                    else Component.empty()
                    player.showTitle(Title.title(
                        titleComponent,
                        subtitleComponent,
                        Title.Times.times(titleFadeIn, titleStay, titleFadeOut),
                    ))
                }
                NotificationChannel.BOSS_BAR -> {
                    val text = title.ifEmpty { message }
                    val bar = BossBar.bossBar(miniMessage.deserialize(text), 1f, bossBarColor, bossBarOverlay)
                    player.showBossBar(bar)
                    val durationMs = bossBarDuration.toMillis()
                    val intervalMs = 50L
                    val totalTicks = (durationMs / intervalMs).toInt().coerceAtLeast(1)
                    var ticksElapsed = 0
                    MinecraftServer.getSchedulerManager().buildTask {
                        ticksElapsed++
                        val progress = 1f - (ticksElapsed.toFloat() / totalTicks)
                        if (progress <= 0f) {
                            player.hideBossBar(bar)
                        } else {
                            bar.progress(progress.coerceIn(0f, 1f))
                        }
                    }.repeat(TaskSchedule.millis(intervalMs)).schedule()
                }
                NotificationChannel.SOUND -> {
                    player.playSound(Sound.sound(soundEvent.key(), Sound.Source.MASTER, soundVolume, soundPitch))
                }
            }
        }
    }

    fun broadcast(instance: Instance) {
        instance.players.forEach { send(it) }
    }

    fun broadcastAll() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { send(it) }
    }
}

class NotificationBuilder @PublishedApi internal constructor() {

    @PublishedApi internal var title: String = ""
    @PublishedApi internal var message: String = ""
    @PublishedApi internal var subtitle: String = ""
    @PublishedApi internal val channels: EnumSet<NotificationChannel> = EnumSet.noneOf(NotificationChannel::class.java)
    @PublishedApi internal var soundEvent: SoundEvent = SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP
    @PublishedApi internal var soundVolume: Float = 1f
    @PublishedApi internal var soundPitch: Float = 1f
    @PublishedApi internal var titleFadeIn: Duration = Duration.ofMillis(500)
    @PublishedApi internal var titleStay: Duration = Duration.ofSeconds(3)
    @PublishedApi internal var titleFadeOut: Duration = Duration.ofMillis(500)
    @PublishedApi internal var bossBarColor: BossBar.Color = BossBar.Color.WHITE
    @PublishedApi internal var bossBarOverlay: BossBar.Overlay = BossBar.Overlay.PROGRESS
    @PublishedApi internal var bossBarDuration: Duration = Duration.ofSeconds(5)

    fun title(text: String) { title = text }
    fun message(text: String) { message = text }
    fun subtitle(text: String) { subtitle = text }
    fun channels(vararg chs: NotificationChannel) { channels.addAll(chs) }
    fun sound(event: SoundEvent, volume: Float = 1f, pitch: Float = 1f) {
        soundEvent = event; soundVolume = volume; soundPitch = pitch
    }
    fun titleTimes(fadeIn: Duration, stay: Duration, fadeOut: Duration) {
        titleFadeIn = fadeIn; titleStay = stay; titleFadeOut = fadeOut
    }
    fun bossBar(color: BossBar.Color = BossBar.Color.WHITE, overlay: BossBar.Overlay = BossBar.Overlay.PROGRESS, duration: Duration = Duration.ofSeconds(5)) {
        bossBarColor = color; bossBarOverlay = overlay; bossBarDuration = duration
    }

    @PublishedApi internal fun build(): Notification = Notification(
        title = title,
        message = message,
        subtitle = subtitle,
        channels = channels.toSet(),
        soundEvent = soundEvent,
        soundVolume = soundVolume,
        soundPitch = soundPitch,
        titleFadeIn = titleFadeIn,
        titleStay = titleStay,
        titleFadeOut = titleFadeOut,
        bossBarColor = bossBarColor,
        bossBarOverlay = bossBarOverlay,
        bossBarDuration = bossBarDuration,
    )
}

inline fun notify(player: Player, block: NotificationBuilder.() -> Unit) {
    NotificationBuilder().apply(block).build().send(player)
}

inline fun buildNotification(block: NotificationBuilder.() -> Unit): Notification =
    NotificationBuilder().apply(block).build()

object NotificationManager {

    fun notifyPlayer(player: Player, notification: Notification) {
        notification.send(player)
    }

    fun notifyInstance(instance: Instance, notification: Notification) {
        notification.broadcast(instance)
    }

    fun broadcast(notification: Notification) {
        notification.broadcastAll()
    }

    inline fun notifyPlayer(player: Player, block: NotificationBuilder.() -> Unit) {
        notifyPlayer(player, NotificationBuilder().apply(block).build())
    }

    inline fun notifyInstance(instance: Instance, block: NotificationBuilder.() -> Unit) {
        notifyInstance(instance, NotificationBuilder().apply(block).build())
    }

    inline fun broadcast(block: NotificationBuilder.() -> Unit) {
        broadcast(NotificationBuilder().apply(block).build())
    }
}

fun announceChat(message: String, instance: Instance? = null) {
    val component = miniMessage.deserialize(message)
    recipients(instance).forEach { it.sendMessage(component) }
}

fun announceActionBar(message: String, instance: Instance? = null) {
    val component = miniMessage.deserialize(message)
    recipients(instance).forEach { it.sendActionBar(component) }
}

fun announceTitle(
    title: String,
    subtitle: String = "",
    fadeIn: Long = 10,
    stay: Long = 70,
    fadeOut: Long = 20,
    instance: Instance? = null,
) {
    val titleComponent = miniMessage.deserialize(title)
    val subtitleComponent = miniMessage.deserialize(subtitle)
    val times = Title.Times.times(
        Duration.ofMillis(fadeIn * 50),
        Duration.ofMillis(stay * 50),
        Duration.ofMillis(fadeOut * 50),
    )
    val adventureTitle = Title.title(titleComponent, subtitleComponent, times)
    recipients(instance).forEach { it.showTitle(adventureTitle) }
}

private fun recipients(instance: Instance?): Collection<Player> =
    instance?.players ?: MinecraftServer.getConnectionManager().onlinePlayers
