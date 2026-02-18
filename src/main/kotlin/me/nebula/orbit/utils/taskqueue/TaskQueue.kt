package me.nebula.orbit.utils.taskqueue

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference

enum class QueueState { PENDING, RUNNING, PAUSED, STOPPED }

class TaskQueue @PublishedApi internal constructor(
    val name: String,
    private val maxConcurrent: Int,
    private val onComplete: () -> Unit,
    private val onError: (Throwable) -> Unit,
) {

    private val queue = ConcurrentLinkedQueue<() -> Unit>()
    private val semaphore = Semaphore(maxConcurrent)
    private val state = AtomicReference(QueueState.PENDING)

    val currentState: QueueState get() = state.get()
    val pendingCount: Int get() = queue.size

    fun submit(task: () -> Unit) {
        require(state.get() != QueueState.STOPPED) { "Queue '$name' is stopped" }
        queue.add(task)
        if (state.compareAndSet(QueueState.PENDING, QueueState.RUNNING)) {
            drainQueue()
        } else if (state.get() == QueueState.RUNNING) {
            drainQueue()
        }
    }

    fun pause() {
        state.set(QueueState.PAUSED)
    }

    fun resume() {
        if (state.compareAndSet(QueueState.PAUSED, QueueState.RUNNING)) {
            drainQueue()
        }
    }

    fun stop() {
        state.set(QueueState.STOPPED)
        queue.clear()
    }

    fun clear() {
        queue.clear()
    }

    private fun drainQueue() {
        while (state.get() == QueueState.RUNNING && queue.isNotEmpty() && semaphore.tryAcquire()) {
            val task = queue.poll() ?: run {
                semaphore.release()
                return
            }
            Thread.startVirtualThread {
                try {
                    task()
                    onComplete()
                } catch (e: Throwable) {
                    onError(e)
                } finally {
                    semaphore.release()
                    if (state.get() == QueueState.RUNNING && queue.isNotEmpty()) {
                        drainQueue()
                    }
                    if (queue.isEmpty() && state.get() == QueueState.RUNNING) {
                        state.compareAndSet(QueueState.RUNNING, QueueState.PENDING)
                    }
                }
            }
        }
    }
}

class TaskQueueBuilder @PublishedApi internal constructor(private val name: String) {

    @PublishedApi internal var maxConcurrent: Int = 1
    @PublishedApi internal var onComplete: () -> Unit = {}
    @PublishedApi internal var onError: (Throwable) -> Unit = {}

    fun maxConcurrent(count: Int) { maxConcurrent = count }
    fun onComplete(handler: () -> Unit) { onComplete = handler }
    fun onError(handler: (Throwable) -> Unit) { onError = handler }

    @PublishedApi internal fun build(): TaskQueue = TaskQueue(name, maxConcurrent, onComplete, onError)
}

inline fun taskQueue(name: String, block: TaskQueueBuilder.() -> Unit = {}): TaskQueue =
    TaskQueueBuilder(name).apply(block).build()

object TaskQueueRegistry {

    private val queues = ConcurrentHashMap<String, TaskQueue>()

    fun register(queue: TaskQueue) { queues[queue.name] = queue }
    fun get(name: String): TaskQueue? = queues[name]
    fun remove(name: String) { queues.remove(name)?.stop() }
    fun all(): Map<String, TaskQueue> = queues.toMap()

    fun clear() {
        queues.values.forEach { it.stop() }
        queues.clear()
    }
}
