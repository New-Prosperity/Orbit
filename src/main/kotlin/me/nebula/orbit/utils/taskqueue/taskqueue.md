# TaskQueue

Sequential or limited-concurrency async task execution queue backed by virtual threads.

## DSL

```kotlin
val queue = taskQueue("my-queue") {
    maxConcurrent(1)
    onComplete { println("task done") }
    onError { e -> e.printStackTrace() }
}
queue.submit { heavyWork() }
queue.submit { moreWork() }
```

## States

- `PENDING` -- idle, no tasks running.
- `RUNNING` -- actively draining tasks.
- `PAUSED` -- tasks queued but not executing; call `resume()` to continue.
- `STOPPED` -- permanently stopped; rejects new submissions.

## Registry

```kotlin
TaskQueueRegistry.register(queue)
TaskQueueRegistry.get("my-queue")
TaskQueueRegistry.remove("my-queue")
TaskQueueRegistry.clear()
```

## Behavior

- Each task runs on its own virtual thread.
- `maxConcurrent` controls how many tasks run simultaneously (default 1 = sequential).
- `onComplete` fires after each successful task. `onError` fires on exception.
- `clear()` removes pending tasks without stopping running ones.
