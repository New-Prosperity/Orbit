package me.nebula.orbit.utils.entitybuilder

import me.nebula.orbit.utils.cooldown.EntityNamedCooldown
import java.time.Duration
import kotlin.random.Random

class SequenceExecutor(private val children: List<BehaviorExecutor>) : BehaviorExecutor {

    private var index = 0

    override fun onStart(entity: SmartEntity) {
        index = 0
        children.firstOrNull()?.onStart(entity)
    }

    override fun execute(entity: SmartEntity): Boolean {
        while (index < children.size) {
            val child = children[index]
            if (child.execute(entity)) return true
            child.onStop(entity)
            index++
            if (index < children.size) children[index].onStart(entity)
        }
        return false
    }

    override fun onStop(entity: SmartEntity) {
        if (index < children.size) children[index].onStop(entity)
    }

    override fun onInterrupt(entity: SmartEntity) {
        if (index < children.size) children[index].onInterrupt(entity)
    }
}

class SelectorExecutor(private val children: List<BehaviorExecutor>) : BehaviorExecutor {

    private var index = -1

    override fun onStart(entity: SmartEntity) {
        index = if (children.isNotEmpty()) 0.also { children[0].onStart(entity) } else -1
    }

    override fun execute(entity: SmartEntity): Boolean {
        while (index in children.indices) {
            val child = children[index]
            if (child.execute(entity)) return true
            child.onStop(entity)
            index++
            if (index in children.indices) children[index].onStart(entity)
        }
        return false
    }

    override fun onStop(entity: SmartEntity) {
        if (index in children.indices) children[index].onStop(entity)
    }

    override fun onInterrupt(entity: SmartEntity) {
        if (index in children.indices) children[index].onInterrupt(entity)
    }
}

enum class ParallelPolicy { ALL, ANY }

class ParallelExecutor(
    private val children: List<BehaviorExecutor>,
    private val policy: ParallelPolicy = ParallelPolicy.ALL,
) : BehaviorExecutor {

    private val active = LinkedHashSet<Int>()

    override fun onStart(entity: SmartEntity) {
        active.clear()
        children.forEachIndexed { i, c ->
            active.add(i)
            c.onStart(entity)
        }
    }

    override fun execute(entity: SmartEntity): Boolean {
        val iter = active.iterator()
        while (iter.hasNext()) {
            val i = iter.next()
            if (!children[i].execute(entity)) {
                children[i].onStop(entity)
                iter.remove()
            }
        }
        return when (policy) {
            ParallelPolicy.ALL -> active.size == children.size
            ParallelPolicy.ANY -> active.isNotEmpty()
        }
    }

    override fun onStop(entity: SmartEntity) {
        active.forEach { children[it].onStop(entity) }
        active.clear()
    }

    override fun onInterrupt(entity: SmartEntity) {
        active.forEach { children[it].onInterrupt(entity) }
        active.clear()
    }
}

class CooldownDecorator(
    private val child: BehaviorExecutor,
    private val cooldownName: String,
    private val cooldown: Duration,
) : BehaviorExecutor {

    private var running = false

    override fun onStart(entity: SmartEntity) {
        if (entity.isOnCooldown(cooldownName)) {
            running = false
            return
        }
        running = true
        child.onStart(entity)
    }

    override fun execute(entity: SmartEntity): Boolean {
        if (!running) return false
        if (child.execute(entity)) return true
        child.onStop(entity)
        EntityNamedCooldown.useFor(entity, cooldownName, cooldown)
        running = false
        return false
    }

    override fun onStop(entity: SmartEntity) {
        if (running) {
            child.onStop(entity)
            EntityNamedCooldown.useFor(entity, cooldownName, cooldown)
            running = false
        }
    }

    override fun onInterrupt(entity: SmartEntity) {
        if (running) {
            child.onInterrupt(entity)
            running = false
        }
    }
}

class TimeoutDecorator(
    private val child: BehaviorExecutor,
    private val ticks: Int,
) : BehaviorExecutor {

    private var remaining = 0

    override fun onStart(entity: SmartEntity) {
        remaining = ticks
        child.onStart(entity)
    }

    override fun execute(entity: SmartEntity): Boolean {
        if (--remaining <= 0) {
            child.onStop(entity)
            return false
        }
        if (!child.execute(entity)) {
            child.onStop(entity)
            return false
        }
        return true
    }

    override fun onStop(entity: SmartEntity) = child.onStop(entity)
    override fun onInterrupt(entity: SmartEntity) = child.onInterrupt(entity)
}

class LoopDecorator(
    private val child: BehaviorExecutor,
    private val times: Int,
) : BehaviorExecutor {

    private var iteration = 0

    override fun onStart(entity: SmartEntity) {
        iteration = 0
        if (times > 0) child.onStart(entity)
    }

    override fun execute(entity: SmartEntity): Boolean {
        if (iteration >= times) return false
        if (child.execute(entity)) return true
        child.onStop(entity)
        iteration++
        if (iteration >= times) return false
        child.onStart(entity)
        return true
    }

    override fun onStop(entity: SmartEntity) {
        if (iteration < times) child.onStop(entity)
    }

    override fun onInterrupt(entity: SmartEntity) {
        if (iteration < times) child.onInterrupt(entity)
    }
}

class WaitExecutor(private val ticks: Int) : BehaviorExecutor {

    private var remaining = 0

    override fun onStart(entity: SmartEntity) { remaining = ticks }

    override fun execute(entity: SmartEntity): Boolean = --remaining > 0
}

fun sequence(vararg children: BehaviorExecutor): BehaviorExecutor =
    SequenceExecutor(children.toList())

fun selector(vararg children: BehaviorExecutor): BehaviorExecutor =
    SelectorExecutor(children.toList())

fun parallel(
    vararg children: BehaviorExecutor,
    policy: ParallelPolicy = ParallelPolicy.ALL,
): BehaviorExecutor = ParallelExecutor(children.toList(), policy)

fun withCooldown(name: String, duration: Duration, child: BehaviorExecutor): BehaviorExecutor =
    CooldownDecorator(child, name, duration)

fun withTimeout(ticks: Int, child: BehaviorExecutor): BehaviorExecutor =
    TimeoutDecorator(child, ticks)

fun loop(times: Int, child: BehaviorExecutor): BehaviorExecutor =
    LoopDecorator(child, times)

fun wait(ticks: Int): BehaviorExecutor = WaitExecutor(ticks)

class WithChanceDecorator(
    private val child: BehaviorExecutor,
    private val chance: Float,
) : BehaviorExecutor {
    private var skipped = false

    override fun onStart(entity: SmartEntity) {
        skipped = Random.nextFloat() >= chance
        if (!skipped) child.onStart(entity)
    }

    override fun execute(entity: SmartEntity): Boolean {
        if (skipped) return false
        return child.execute(entity)
    }

    override fun onStop(entity: SmartEntity) { if (!skipped) child.onStop(entity) }
    override fun onInterrupt(entity: SmartEntity) { if (!skipped) child.onInterrupt(entity) }
}

class FallbackDecorator(
    private val primary: BehaviorExecutor,
    private val alternative: BehaviorExecutor,
) : BehaviorExecutor {
    private var active: BehaviorExecutor? = null
    private var fellBack = false

    override fun onStart(entity: SmartEntity) {
        fellBack = false
        active = primary
        primary.onStart(entity)
    }

    override fun execute(entity: SmartEntity): Boolean {
        val a = active ?: return false
        if (a.execute(entity)) return true
        a.onStop(entity)
        if (a === primary && !fellBack) {
            fellBack = true
            active = alternative
            alternative.onStart(entity)
            return alternative.execute(entity).also { if (!it) { alternative.onStop(entity); active = null } }
        }
        active = null
        return false
    }

    override fun onStop(entity: SmartEntity) { active?.onStop(entity); active = null }
    override fun onInterrupt(entity: SmartEntity) { active?.onInterrupt(entity); active = null }
}

class RepeatForeverDecorator(
    private val child: BehaviorExecutor,
) : BehaviorExecutor {
    override fun onStart(entity: SmartEntity) { child.onStart(entity) }

    override fun execute(entity: SmartEntity): Boolean {
        if (child.execute(entity)) return true
        child.onStop(entity)
        child.onStart(entity)
        return true
    }

    override fun onStop(entity: SmartEntity) { child.onStop(entity) }
    override fun onInterrupt(entity: SmartEntity) { child.onInterrupt(entity) }
}

class InvertDecorator(
    private val child: BehaviorExecutor,
) : BehaviorExecutor {
    override fun onStart(entity: SmartEntity) { child.onStart(entity) }
    override fun execute(entity: SmartEntity): Boolean = !child.execute(entity)
    override fun onStop(entity: SmartEntity) { child.onStop(entity) }
    override fun onInterrupt(entity: SmartEntity) { child.onInterrupt(entity) }
}

fun withChance(chance: Float, child: BehaviorExecutor): BehaviorExecutor =
    WithChanceDecorator(child, chance)

fun fallback(primary: BehaviorExecutor, alternative: BehaviorExecutor): BehaviorExecutor =
    FallbackDecorator(primary, alternative)

fun repeatForever(child: BehaviorExecutor): BehaviorExecutor = RepeatForeverDecorator(child)

fun invert(child: BehaviorExecutor): BehaviorExecutor = InvertDecorator(child)
