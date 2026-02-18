package me.nebula.orbit.utils.broadcastscheduler

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

class BroadcastScheduler(
    private val intervalTicks: Int,
    private val messages: List<Component>,
    private val shuffled: Boolean = false,
) {
    private var task: Task? = null
    private var index = 0
    private val ordered: List<Component> = if (shuffled) messages.shuffled() else messages

    fun start() {
        require(task == null) { "Scheduler already running" }
        require(messages.isNotEmpty()) { "No messages to broadcast" }
        index = 0
        task = MinecraftServer.getSchedulerManager().buildTask {
            val msg = ordered[index % ordered.size]
            MinecraftServer.getConnectionManager().onlinePlayers.forEach { it.sendMessage(msg) }
            index++
        }.repeat(TaskSchedule.tick(intervalTicks)).schedule()
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    val isRunning: Boolean get() = task != null
}

class BroadcastSchedulerBuilder {
    private val messages = mutableListOf<Component>()
    var intervalTicks: Int = 6000
    var shuffled: Boolean = false

    fun intervalSeconds(seconds: Int) {
        intervalTicks = seconds * 20
    }

    fun message(text: String) {
        messages.add(MiniMessage.miniMessage().deserialize(text))
    }

    fun message(component: Component) {
        messages.add(component)
    }

    fun build(): BroadcastScheduler = BroadcastScheduler(intervalTicks, messages.toList(), shuffled)
}

fun broadcastScheduler(block: BroadcastSchedulerBuilder.() -> Unit): BroadcastScheduler =
    BroadcastSchedulerBuilder().apply(block).build()
